"""Exercise Python asset exports without downloading tools or dependencies."""

import contextlib
import hashlib
import importlib.util
import io
import json
import os
import shutil
import subprocess
import sys
import tarfile
import tempfile
import unittest
import urllib.error
import zipfile
from pathlib import Path
from unittest import mock


ROOT = Path(__file__).resolve().parents[2]
SPEC = importlib.util.spec_from_file_location("asset_exporter", ROOT / "scripts/export-android-test-assets.py")
ASSETS = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(ASSETS)


def write(path, value, *, executable=False):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(value, encoding="utf-8")
    if executable:
        path.chmod(0o755)
    return path


class ArchiveTest(unittest.TestCase):
    def test_stream_limits_and_reconstruction(self):
        self.assertEqual(180_000_000, ASSETS.MAX_ARCHIVE_BYTES)
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            for length in (1, 11, 12, 13, 24, 25, 40):
                with self.subTest(length=length):
                    path = root / f"archive-{length}.tar.gz"
                    data = bytes(range(length))
                    with ASSETS.SplitArchiveWriter(path, limit=12) as stream:
                        # Include zero-byte writes and chunks crossing boundaries.
                        for chunk in (data[:7], b"", data[7:22], data[22:]):
                            self.assertEqual(len(chunk), stream.write(chunk))
                    if length <= 12:
                        self.assertEqual(data, path.read_bytes())
                        self.assertFalse(list(root.glob(f"{path.name}.part-*")))
                    else:
                        self.assertFalse(path.exists())
                        parts = sorted(root.glob(f"{path.name}.part-*"))
                        self.assertEqual(data, b"".join(part.read_bytes() for part in parts))
                        self.assertTrue(all(0 < part.stat().st_size <= 12 for part in parts))
                        self.assertEqual(f"{path.name}.part-000", parts[0].name)

    def test_empty_stream_invalid_limit_and_interrupted_stream(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "asset.tar.gz"
            with self.assertRaises(ValueError):
                ASSETS.SplitArchiveWriter(path, 0)
            with self.assertRaises(ASSETS.ExportError):
                with ASSETS.SplitArchiveWriter(path, 12):
                    pass
            with self.assertRaisesRegex(RuntimeError, "interrupted"):
                with ASSETS.SplitArchiveWriter(path, 12) as stream:
                    stream.write(b"incomplete")
                    raise RuntimeError("interrupted")
            self.assertFalse(path.exists())
            self.assertTrue(stream.stream is None)

    def test_gzip_tar_round_trip_across_parts(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            source = root / "input.bin"
            source.write_bytes(bytes(range(256)) * 20)
            path = root / "bundle.tar.gz"
            original_writer = ASSETS.SplitArchiveWriter
            with mock.patch.object(ASSETS, "SplitArchiveWriter", side_effect=lambda name: original_writer(name, 150)):
                ASSETS.export_archive(path, [(source, "nested/input.bin")])
            parts = sorted(root.glob("*.part-*"))
            self.assertGreater(len(parts), 1)
            with tarfile.open(fileobj=io.BytesIO(b"".join(part.read_bytes() for part in parts)), mode="r:gz") as archive:
                self.assertEqual(source.read_bytes(), archive.extractfile("nested/input.bin").read())


GRADLE_STUB = """#!/usr/bin/env python3
import json, os, pathlib, sys
if sys.argv[1:] == ['--version']:
    print('Gradle ' + os.environ.get('FAKE_GRADLE_VERSION', '9.8.0'))
    sys.exit(0)
root = pathlib.Path(os.environ['CAPTURE_DIR'])
with (root / 'gradle-calls.jsonl').open('a') as log:
    log.write(json.dumps({'args': sys.argv[1:], 'home': os.environ['GRADLE_USER_HOME'], 'java': os.environ.get('JAVA_HOME'),
                         'sdk': os.environ['ANDROID_SDK_ROOT']}) + '\\n')
if os.environ.get('FAIL_RESOLVE'):
    sys.exit(23)
modules = pathlib.Path(os.environ['GRADLE_USER_HOME']) / 'caches/modules-2'
if not os.environ.get('SKIP_DEPS'):
    dependency = modules / 'files-2.1/example/tool/1.0/hash/tool.jar'
    dependency.parent.mkdir(parents=True, exist_ok=True)
    dependency.write_text('dependency')
    (modules / 'modules-2.lock').write_text('excluded')
    (modules / 'gc.properties').write_text('excluded')
    (modules.parent.parent / 'gradle.properties').write_text('secret')
if not os.environ.get('SKIP_METADATA'):
    (modules / 'metadata-2.107').mkdir(parents=True, exist_ok=True)
    (modules / 'metadata-2.107/module-artifact.bin').write_text('metadata')
"""



class ExporterTest(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory(prefix="asset test ")
        self.addCleanup(self.directory.cleanup)
        self.root = Path(self.directory.name)
        self.project = self.root / "project with spaces"
        self.project.mkdir()
        self.output = self.root / "exports with spaces"
        self.bin = self.root / "bin"
        self.bin.mkdir()
        write(self.bin / "java", "#!/bin/sh\nexit 0\n", executable=True)
        self.gradle = write(self.bin / "gradle", GRADLE_STUB, executable=True)
        self.sdk = self.root / "sdk"
        write(self.sdk / "platforms/android-37.0/android.jar", "SDK fixture")
        write(self.sdk / "build-tools/36.0.0/source.properties", "tools fixture")
        write(self.sdk / "licenses/license", "licence fixture")
        write(self.sdk / "ndk/secret", "not exported")
        write(self.project / "gradle/wrapper/gradle-wrapper.properties",
              "distributionUrl=https\\://services.gradle.org/distributions/gradle-9.8.0-bin.zip\n")
        self.downloads = []
        self.tool_packages = {}
        self.stack = contextlib.ExitStack()
        self.addCleanup(self.stack.close)
        self.stack.enter_context(mock.patch.object(ASSETS, "PROJECT_ROOT", self.project))
        self.stack.enter_context(mock.patch.object(ASSETS.platform, "system", return_value="Linux"))
        self.stack.enter_context(mock.patch.object(ASSETS.platform, "machine", return_value="x86_64"))
        self.stack.enter_context(mock.patch.dict(os.environ, {"CAPTURE_DIR": str(self.root),
            "PATH": str(self.bin) + os.pathsep + os.environ.get("PATH", "")}))
        self.stack.enter_context(mock.patch.object(ASSETS, "download", side_effect=self.download))

    def download(self, url, destination):
        self.downloads.append(url)
        name = url.rsplit("/", 1)[-1]
        if name in self.tool_packages:
            destination.write_bytes(self.tool_packages[name])
            return
        data = b"test runtime" if ".jar" in url else b"test pom"
        destination.write_bytes(hashlib.sha512(data).hexdigest().encode() if url.endswith(".sha512") else data)

    def tool_fixtures(self):
        for name, entries in (
            ("gradle-9.8.0-bin.zip", {"gradle-9.8.0/bin/gradle": GRADLE_STUB}),
            (ASSETS.CMDLINE_TOOLS_ZIP, {"cmdline-tools/bin/sdkmanager": """#!/usr/bin/env python3
import os, pathlib, sys
root = pathlib.Path(os.environ['CAPTURE_DIR'])
with (root / 'sdkmanager-calls.txt').open('a') as output:
    output.write(' '.join(sys.argv[1:]) + '\\n')
if '--licenses' in sys.argv:
    (root / 'sdkmanager-input.txt').write_text(sys.stdin.read())
else:
    sdk = pathlib.Path(os.environ['ANDROID_SDK_ROOT'])
    for name in ('platforms/android-37.0/android.jar', 'build-tools/36.0.0/source.properties'):
        path = sdk / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text('SDK fixture')
"""}),
        ):
            stream = io.BytesIO()
            with zipfile.ZipFile(stream, "w") as archive:
                for path, text in entries.items():
                    member = zipfile.ZipInfo(path)
                    member.external_attr = 0o100755 << 16
                    archive.writestr(member, text)
            self.tool_packages[name] = stream.getvalue()
        stream = io.BytesIO()
        with tarfile.open(fileobj=stream, mode="w:gz") as archive:
            for name in ("java", "javac"):
                data = b"#!/bin/sh\nexit 0\n"
                member = tarfile.TarInfo(f"{ASSETS.JBR_ARCHIVE.removesuffix('.tar.gz')}/bin/{name}")
                member.mode, member.size = 0o755, len(data)
                archive.addfile(member, io.BytesIO(data))
        self.tool_packages[ASSETS.JBR_ARCHIVE] = stream.getvalue()
        self.stack.enter_context(mock.patch.object(ASSETS, "JBR_SHA256", hashlib.sha256(stream.getvalue()).hexdigest()))
        self.stack.enter_context(mock.patch.object(ASSETS, "CMDLINE_TOOLS_SHA256",
                                                  hashlib.sha256(self.tool_packages[ASSETS.CMDLINE_TOOLS_ZIP]).hexdigest()))
        wrapper = self.project / "gradle/wrapper/gradle-wrapper.properties"
        with wrapper.open("a") as output:
            output.write("distributionSha256Sum=" + hashlib.sha256(self.tool_packages['gradle-9.8.0-bin.zip']).hexdigest() + "\n")

    def download_export(self, *arguments):
        with contextlib.redirect_stdout(io.StringIO()), contextlib.redirect_stderr(io.StringIO()) as errors:
            result = ASSETS.main(["--output", str(self.output), "--download-cache", str(self.root / "download cache"),
                                  *arguments])
        self.error = errors.getvalue()
        return result

    def export(self, kind="gradle", *extra, output=None):
        args = ["--assets", kind, "--output", str(output or self.output)]
        args.extend(["--sdk-root", str(self.sdk), "--gradle-bin", str(self.gradle)])
        with contextlib.redirect_stdout(io.StringIO()), contextlib.redirect_stderr(io.StringIO()) as errors:
            result = ASSETS.main([*args, *extra])
        self.error = errors.getvalue()
        return result

    def calls(self, name):
        return [json.loads(line) for line in (self.root / f"{name}-calls.jsonl").read_text().splitlines()]

    def archive(self, name, output=None):
        path = (output or self.output) / f"{name}.tar.gz"
        if path.exists():
            return tarfile.open(path, "r:gz")
        data = b"".join(part.read_bytes() for part in sorted(path.parent.glob(path.name + ".part-*")))
        return tarfile.open(fileobj=io.BytesIO(data), mode="r:gz")

    def verify_checksums(self, output=None):
        root = output or self.output
        records = (root / "SHA256SUMS").read_text().splitlines()
        for line in records:
            expected, name = line.split("  ", 1)
            self.assertEqual(expected, hashlib.sha256((root / name).read_bytes()).hexdigest())
        expected_names = {path.name for path in root.iterdir() if ".tar.gz" in path.name or ".zip" in path.name}
        self.assertEqual(expected_names, {line.split("  ", 1)[1] for line in records})

    def test_gradle_exports_only_offline_inputs_and_cleans_stale_parts(self):
        for name in ("gradle-deps.tar.gz.part-000", "gradle-cache.tar.gz", "gradle-cache.tar.gz.part-000"):
            write(self.output / name, "obsolete")
        write(self.output / "unrelated.txt", "keep")
        self.assertEqual(0, self.export(), self.error)
        self.assertEqual(8, len(self.downloads))
        with self.archive("android-sdk") as archive:
            self.assertIn("platforms/android-37.0/android.jar", archive.getnames())
            self.assertFalse(any("ndk/" in name for name in archive.getnames()))
        with self.archive("gradle-deps") as archive:
            names = archive.getnames()
            self.assertIn("caches/modules-2/files-2.1/example/tool/1.0/hash/tool.jar", names)
            self.assertIn("caches/modules-2/metadata-2.107/module-artifact.bin", names)
            self.assertFalse(any(name.endswith((".lock", "gc.properties", "gradle.properties")) for name in names))
        with self.archive("robolectric-maven") as archive:
            self.assertEqual(4, sum(name.endswith((".jar", ".pom")) for name in archive.getnames()))
        self.assertEqual("keep", (self.output / "unrelated.txt").read_text())
        self.assertFalse(list(self.output.glob("*.part-*")))
        self.assertFalse(list(self.output.glob("gradle-cache*")))
        self.verify_checksums()
        call = self.calls("gradle")[0]
        self.assertEqual(str(self.sdk), call["sdk"])
        self.assertIn(":app:resolveAndroidTestAssets", call["args"])
        self.assertIn("--init-script", call["args"])
        self.assertNotIn(":app:testDebugUnitTest", call["args"])
        self.assertFalse(list((self.project / "build").iterdir()))

    def test_reuses_gradle_and_robolectric_cache_and_repairs_corruption(self):
        home = self.root / "existing gradle home"
        write(home / "caches/modules-2/files-2.1/preexisting/library.jar", "cached")
        args = ("--gradle-user-home", str(home))
        self.assertEqual(0, self.export("gradle", *args), self.error)
        self.assertEqual(0, self.export("gradle", *args), self.error)
        self.assertEqual(8, len(self.downloads))
        with self.archive("gradle-deps") as archive:
            self.assertIn("caches/modules-2/files-2.1/preexisting/library.jar", archive.getnames())
        next((home / "robolectric-maven").rglob("*.jar")).write_bytes(b"corrupted")
        # Malformed checksum bytes must also trigger a fresh download.
        next((home / "robolectric-maven").rglob("*.pom.sha512")).write_bytes(b"\xff")
        self.assertEqual(0, self.export("gradle", *args), self.error)
        self.assertEqual(12, len(self.downloads))

    def test_failed_resolution_and_missing_cache_do_not_replace_exports(self):
        for environment, message in (({"FAIL_RESOLVE": "1"}, "could not resolve"),
                                     ({"SKIP_DEPS": "1"}, "artifacts were not populated"),
                                     ({"SKIP_METADATA": "1"}, "metadata was not populated")):
            with self.subTest(environment=environment), mock.patch.dict(os.environ, environment):
                write(self.output / "gradle-deps.tar.gz", "previous successful export")
                self.assertEqual(1, self.export())
                self.assertIn(message, self.error)
                self.assertEqual("previous successful export", (self.output / "gradle-deps.tar.gz").read_text())
                self.assertFalse(list(self.output.glob(".staging.*")))

    def test_failed_download_or_checksum_keeps_previous_exports(self):
        write(self.output / "robolectric-maven.tar.gz", "previous")
        with mock.patch.object(ASSETS, "download", side_effect=ASSETS.ExportError("download failed")):
            self.assertEqual(1, self.export())
            self.assertIn("download failed", self.error)
        with mock.patch.object(ASSETS, "verify_maven_checksum", return_value=False):
            self.assertEqual(1, self.export())
            self.assertIn("checksum mismatch", self.error)
        self.assertEqual("previous", (self.output / "robolectric-maven.tar.gz").read_text())

    def test_gradle_wrapper_versions_and_sdk_mismatch(self):
        wrapper = self.project / "gradle/wrapper/gradle-wrapper.properties"
        wrapper.write_text("distributionUrl=https\\://services.gradle.org/distributions/gradle-9.8.0-milestone-1-bin.zip\n")
        self.assertEqual("9.8.0-milestone-1", ASSETS.required_gradle_version())
        self.assertEqual(1, self.export())
        self.assertIn("does not match wrapper version", self.error)
        with mock.patch.dict(os.environ, {"FAKE_GRADLE_VERSION": "9.8.0-milestone-1"}):
            self.assertEqual(0, self.export(), self.error)
            write(self.project / "local.properties", f"sdk.dir={self.root}/another-sdk\n")
            self.assertEqual(1, self.export())
            self.assertIn("local.properties selects", self.error)

    def test_sdk_install_uses_existing_manager(self):
        (self.sdk / "platforms/android-37.0/android.jar").unlink()
        write(self.sdk / "cmdline-tools/latest/bin/sdkmanager", '#!/usr/bin/env python3\n'
              'import os, pathlib\n'
              'sdk = pathlib.Path(os.environ["ANDROID_SDK_ROOT"])\n'
              '(sdk / "platforms/android-37.0/android.jar").write_text("installed")\n', executable=True)
        self.assertEqual(0, self.export(), self.error)
        self.assertEqual(8, len(self.downloads))  # Only Maven requests; no SDK bootstrap.

    def test_relative_sdk_properties_resolve_from_checkout(self):
        shutil.copytree(self.sdk, self.project / "sdk")
        write(self.project / "local.properties", "sdk.dir=sdk\n")
        self.assertEqual(self.project / "sdk", ASSETS.sdk_root(ASSETS.parse_args([])))

    def test_gradle_export_and_stale_parts_in_both_directions(self):
        write(self.output / "gradle-deps.tar.gz", "old whole archive")
        original_writer = ASSETS.SplitArchiveWriter
        with mock.patch.object(ASSETS, "SplitArchiveWriter", side_effect=lambda name: original_writer(name, 100)):
            self.assertEqual(0, self.export("gradle"), self.error)
        self.assertFalse((self.output / "gradle-deps.tar.gz").exists())
        self.assertGreater(len(list(self.output.glob("gradle-deps.tar.gz.part-*"))), 1)
        self.verify_checksums()
        with self.archive("gradle-deps") as archive:
            self.assertIn("caches/modules-2/metadata-2.107/module-artifact.bin", archive.getnames())
        self.assertEqual(0, self.export("gradle"), self.error)
        self.assertFalse(list(self.output.glob("*.part-*")))

    def test_download_only_needs_no_host_tools_and_splits_original_packages(self):
        self.tool_fixtures()
        original_writer = ASSETS.SplitArchiveWriter
        with mock.patch.object(ASSETS.platform, "system", return_value="Darwin"), \
             mock.patch.object(ASSETS, "run") as run, \
             mock.patch.object(ASSETS, "prepare_downloaded_tools") as prepare, \
             mock.patch.object(ASSETS, "SplitArchiveWriter", side_effect=lambda name: original_writer(name, 100)):
            self.assertEqual(0, self.download_export("--download-tools", "--download-only"), self.error)
        run.assert_not_called()
        prepare.assert_not_called()
        self.assertEqual(3, len(self.downloads))
        self.assertIn("https://services.gradle.org/distributions/gradle-9.8.0-bin.zip", self.downloads)
        tools = self.output / "tools"
        for name, expected in self.tool_packages.items():
            with self.subTest(name=name):
                parts = sorted(tools.glob(name + ".part-*"))
                self.assertGreater(len(parts), 1)
                self.assertEqual(expected, b"".join(part.read_bytes() for part in parts))
                self.assertTrue(all(part.stat().st_size <= 100 for part in parts))
        self.verify_checksums(tools)
        self.assertFalse((self.root / "gradle-calls.jsonl").exists())

    def test_native_export_uses_downloaded_gradle_jbr_and_sdk_tools(self):
        self.tool_fixtures()
        sdk = self.root / "new SDK"
        self.assertEqual(0, self.download_export("--download-tools", "--sdk-root", str(sdk),
                                                "--accept-sdk-licenses"), self.error)
        self.assertTrue(ASSETS.sdk_installed(sdk))
        calls = (self.root / "sdkmanager-calls.txt").read_text().splitlines()
        self.assertIn("--licenses", calls[0])
        self.assertIn("platforms;android-37.0", calls[1])
        self.assertEqual("y\n" * 100, (self.root / "sdkmanager-input.txt").read_text())
        gradle = self.calls("gradle")[0]
        self.assertEqual(str(sdk), gradle["sdk"])
        self.assertIn(".prepared.", gradle["java"])
        self.assertFalse(Path(gradle["java"]).exists())
        self.verify_checksums()
        self.verify_checksums(self.output / "tools")

    def test_tool_download_cache_repairs_corruption_and_rejects_bad_downloads(self):
        self.tool_fixtures()
        arguments = ("--download-jbr", "--download-only")
        self.assertEqual(0, self.download_export(*arguments), self.error)
        self.assertEqual(0, self.download_export(*arguments), self.error)
        self.assertEqual(1, len(self.downloads))
        cache = self.root / "download cache/archives" / ASSETS.JBR_ARCHIVE
        cache.write_bytes(b"corrupted")
        self.assertEqual(0, self.download_export(*arguments), self.error)
        self.assertEqual(2, len(self.downloads))
        previous = {path.name: path.read_bytes() for path in (self.output / "tools").iterdir()}
        cache.write_bytes(b"corrupted again")
        self.tool_packages[ASSETS.JBR_ARCHIVE] = b"bad download"
        self.assertEqual(1, self.download_export(*arguments))
        self.assertIn("SHA-256 mismatch", self.error)
        self.assertEqual(previous, {path.name: path.read_bytes() for path in (self.output / "tools").iterdir()})
        self.assertEqual(b"corrupted again", cache.read_bytes())
        self.assertFalse(list((self.output / "tools").glob(".staging.*")))

    def test_individual_downloads_preserve_other_tools_and_remove_stale_parts(self):
        self.tool_fixtures()
        original_writer = ASSETS.SplitArchiveWriter
        with mock.patch.object(ASSETS, "SplitArchiveWriter", side_effect=lambda name: original_writer(name, 100)):
            self.assertEqual(0, self.download_export("--download-tools", "--download-only"), self.error)
        write(self.output / "tools/unrelated.txt", "keep")
        self.assertEqual(0, self.download_export("--download-jbr", "--download-only"), self.error)
        tools = self.output / "tools"
        self.assertTrue((tools / ASSETS.JBR_ARCHIVE).is_file())
        self.assertFalse(list(tools.glob(ASSETS.JBR_ARCHIVE + ".part-*")))
        self.assertEqual(self.tool_packages["gradle-9.8.0-bin.zip"], (tools / "gradle-9.8.0-bin.zip").read_bytes())
        self.assertEqual("keep", (tools / "unrelated.txt").read_text())
        self.verify_checksums(tools)

    def test_previous_tool_exports_are_verified_before_being_preserved(self):
        self.tool_fixtures()
        self.assertEqual(0, self.download_export("--download-tools", "--download-only"), self.error)
        tools = self.output / "tools"
        sums = (tools / "SHA256SUMS").read_bytes()
        (tools / "gradle-9.8.0-bin.zip").write_bytes(b"corrupted")
        self.assertEqual(1, self.download_export("--download-jbr", "--download-only"))
        self.assertIn("Previous export checksum mismatch", self.error)
        self.assertEqual(sums, (tools / "SHA256SUMS").read_bytes())
        self.assertEqual(0, self.download_export("--download-gradle", "--download-only"), self.error)
        self.verify_checksums(tools)

    def test_gradle_download_requires_pinned_checksum_and_https(self):
        self.assertEqual(1, self.download_export("--download-gradle", "--download-only"))
        self.assertIn("distributionSha256Sum", self.error)
        self.assertFalse(self.downloads)
        self.tool_fixtures()
        wrapper = self.project / "gradle/wrapper/gradle-wrapper.properties"
        wrapper.write_text(wrapper.read_text().replace("https", "http"))
        self.assertEqual(1, self.download_export("--download-gradle", "--download-only"))
        self.assertIn("HTTPS", self.error)
        self.assertFalse(self.downloads)


class DispatchTest(unittest.TestCase):
    def test_python_cli_help_without_tools(self):
        result = subprocess.run(
            [sys.executable, str(ROOT / "scripts/export-android-test-assets.py"), "--help"],
            text=True, capture_output=True,
        )
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertIn("--assets", result.stdout)
        self.assertIn("180,000,000", result.stdout)
        self.assertIn("--download-only", result.stdout)

    def test_defaults_and_invalid_options(self):
        self.assertEqual("gradle", ASSETS.parse_args([]).assets)
        self.assertEqual("gradle", ASSETS.parse_args(["--assets", "gradle"]).assets)
        for arguments in (("--assets", "unknown"),
                          ("--output", ""), ("--output",), ("--download-only",)):
            with self.subTest(arguments=arguments), contextlib.redirect_stderr(io.StringIO()):
                with self.assertRaises(SystemExit) as error:
                    ASSETS.parse_args(arguments)
                self.assertEqual(2, error.exception.code)

    def test_docker_dispatch_for_mac_arm_linux_and_windows(self):
        for operating_system, architecture in (("Darwin", "arm64"), ("Linux", "aarch64"), ("Windows", "AMD64")):
            with self.subTest(os=operating_system), tempfile.TemporaryDirectory() as directory:
                output = Path(directory) / "output with spaces"
                with mock.patch.object(ASSETS.platform, "system", return_value=operating_system), \
                     mock.patch.object(ASSETS.platform, "machine", return_value=architecture), \
                     mock.patch.object(ASSETS.shutil, "which", return_value="/bin/docker"), \
                     mock.patch.object(ASSETS, "run", return_value="linux") as run, \
                     mock.patch.object(ASSETS, "export_gradle") as native, \
                     contextlib.redirect_stdout(io.StringIO()):
                    self.assertEqual(0, ASSETS.main(["--output", str(output)]))
                native.assert_not_called()
                commands = [call.args[0] for call in run.call_args_list]
                builds = [command for command in commands if command[1] == "build"]
                containers = [command for command in commands if command[1] == "run"]
                self.assertEqual(1, len(builds))
                self.assertEqual(1, len(containers))
                for command in builds + containers:
                    self.assertIn("linux/amd64", command)
                self.assertIn(f"{output}:/export", containers[0])
                self.assertIn("python3", containers[0])
                self.assertIn(ASSETS.DOCKER_BOOTSTRAP, containers[0])
                compile(ASSETS.DOCKER_BOOTSTRAP, "docker-bootstrap", "exec")

    def test_docker_rejects_native_paths_unavailable_daemon_and_windows_containers(self):
        with mock.patch.object(ASSETS.platform, "system", return_value="Darwin"), \
             mock.patch.object(ASSETS.shutil, "which", return_value="/bin/docker"), \
             mock.patch.object(ASSETS, "run") as run:
            with contextlib.redirect_stderr(io.StringIO()) as errors:
                self.assertEqual(1, ASSETS.main(["--sdk-root", "/some/mac/sdk"]))
            self.assertIn("native Linux options", errors.getvalue())
            run.assert_not_called()
            for result in ("windows", ASSETS.ExportError("Docker daemon is unavailable")):
                run.reset_mock()
                run.side_effect = result if isinstance(result, Exception) else None
                run.return_value = result
                with contextlib.redirect_stderr(io.StringIO()):
                    self.assertEqual(1, ASSETS.main([]))
                self.assertEqual(1, run.call_count)

    def test_dockerfiles_supply_python_and_use_jbr_cdn(self):
        android = (ROOT / "scripts/Dockerfile.android-test-assets").read_text()
        self.assertIn("python3", android)
        self.assertIn("https://cache-redirector.jetbrains.com/intellij-jbr/${JBR_ARCHIVE}", android)
        self.assertIn(f"ARG JBR_ARCHIVE={ASSETS.JBR_ARCHIVE}", android)
        self.assertIn(f"ARG JBR_SHA256={ASSETS.JBR_SHA256}", android)


class DownloadTest(unittest.TestCase):
    def test_extraction_rejects_paths_and_links_outside_destination(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            source = root / "tools.zip"
            with zipfile.ZipFile(source, "w") as archive:
                archive.writestr("../outside", "must not be extracted")
            with self.assertRaisesRegex(ASSETS.ExportError, "Unsafe path"):
                ASSETS.extract_zip(source, root / "destination")
            self.assertFalse((root / "outside").exists())
            source = root / "tools.tar.gz"
            with tarfile.open(source, "w:gz") as archive:
                member = tarfile.TarInfo("link")
                member.type, member.linkname = tarfile.SYMTYPE, "../outside"
                archive.addfile(member)
            with self.assertRaisesRegex(ASSETS.ExportError, "Unsafe link"):
                ASSETS.extract_tar(source, root / "destination")
            self.assertFalse((root / "destination/link").exists())

    def test_transient_download_retries_and_publishes_complete_bytes(self):
        with tempfile.TemporaryDirectory() as directory, \
             mock.patch.object(ASSETS.time, "sleep"), \
             mock.patch.object(ASSETS.urllib.request, "urlopen", side_effect=[
                 urllib.error.URLError("offline"), io.BytesIO(b"complete")]) as request:
            path = Path(directory) / "download.jar"
            ASSETS.download("https://example.test/file", path)
            self.assertEqual(b"complete", path.read_bytes())
            self.assertEqual(2, request.call_count)
            self.assertFalse(path.with_name(path.name + ".download").exists())

    def test_exhausted_download_keeps_previous_target(self):
        with tempfile.TemporaryDirectory() as directory, \
             mock.patch.object(ASSETS.time, "sleep"), \
             mock.patch.object(ASSETS.urllib.request, "urlopen", side_effect=urllib.error.URLError("offline")):
            path = write(Path(directory) / "download.jar", "previous")
            with self.assertRaisesRegex(ASSETS.ExportError, "Could not download"):
                ASSETS.download("https://example.test/file", path)
            self.assertEqual("previous", path.read_text())
            self.assertFalse(path.with_name(path.name + ".download").exists())

    def test_sdk_bootstrap_verifies_checksum_and_restores_zip_permissions(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            sdk, work = root / "sdk", root / "work"
            sdk.mkdir()
            work.mkdir()
            source = root / "tools.zip"
            with zipfile.ZipFile(source, "w") as archive:
                member = zipfile.ZipInfo("cmdline-tools/bin/sdkmanager")
                member.external_attr = 0o100755 << 16
                archive.writestr(member, "#!/bin/sh\nexit 0\n")

            def install(command, **_kwargs):
                self.assertTrue(os.access(command[0], os.X_OK))
                write(sdk / "platforms/android-37.0/android.jar", "SDK")
                write(sdk / "build-tools/36.0.0/source.properties", "tools")

            with mock.patch.object(ASSETS, "download", side_effect=lambda _url, path: shutil.copyfile(source, path)), \
                 mock.patch.object(ASSETS.shutil, "which", return_value=None), \
                 mock.patch.object(ASSETS, "run", side_effect=install), \
                 contextlib.redirect_stdout(io.StringIO()):
                with self.assertRaisesRegex(ASSETS.ExportError, "SHA-256 mismatch"):
                    ASSETS.ensure_sdk(sdk, work, {})
                with mock.patch.object(ASSETS, "CMDLINE_TOOLS_SHA256", ASSETS.file_digest(source)):
                    ASSETS.ensure_sdk(sdk, work, {})
                self.assertTrue(ASSETS.sdk_installed(sdk))


if __name__ == "__main__":
    unittest.main()
