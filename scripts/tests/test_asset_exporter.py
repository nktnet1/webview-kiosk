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
        self.assertEqual(250_000_000, ASSETS.MAX_ARCHIVE_BYTES)
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
    log.write(json.dumps({'args': sys.argv[1:], 'home': os.environ['GRADLE_USER_HOME'],
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

CARGO_STUB = """#!/usr/bin/env python3
import json, os, pathlib, sys
root = pathlib.Path(os.environ['CAPTURE_DIR'])
with (root / 'cargo-calls.jsonl').open('a') as log:
    log.write(json.dumps({'args': sys.argv[1:], 'cwd': str(pathlib.Path.cwd()),
                         'toolchain': os.environ['RUSTUP_TOOLCHAIN'],
                         'rustup': os.environ['RUSTUP_HOME']}) + '\\n')
command = sys.argv[1]
if command == 'generate-lockfile':
    pathlib.Path('Cargo.lock').write_text('# fixture lockfile\\nversion = 4\\n')
elif command == 'vendor':
    if os.environ.get('FAIL_VENDOR'):
        sys.exit(22)
    pathlib.Path('vendor/example-1.0').mkdir(parents=True)
    pathlib.Path('vendor/example-1.0/lib.rs').write_text('dependency sources')
    print('[source.crates-io]\\nreplace-with = "vendored-sources"\\n'
          '[source.vendored-sources]\\ndirectory = "vendor"')
elif command == 'metadata':
    if os.environ.get('FAIL_METADATA'):
        sys.exit(23)
    print('{}')
elif command == '--version':
    print('cargo 1.99.0 (fixture)')
else:
    sys.exit(8)
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
        self.crate = self.project / "app/native-adblock"
        write(self.crate / "Cargo.toml", '[package]\nname = "fixture"\nversion = "0.1.0"\n')
        write(self.crate / "rust-toolchain.toml", '[toolchain]\nchannel = "stable"\n')
        write(self.crate / "src/lib.rs", "// Rust source fixture\n")
        self.rustup = self.root / "rustup"
        self.toolchain = self.rustup / "toolchains" / ASSETS.TOOLCHAIN
        write(self.toolchain / "bin/rustc", "compiler fixture", executable=True)
        write(self.toolchain / f"lib/rustlib/{ASSETS.HOST_TRIPLE}/lib/libstd.rlib", "host stdlib")
        for target in ASSETS.ANDROID_TARGETS:
            write(self.toolchain / f"lib/rustlib/{target}/lib/android.rlib", "not needed")
        self.settings = write(self.rustup / "settings.toml", 'version = "12"\n'
                              f'default_toolchain = "{ASSETS.TOOLCHAIN}"\n'
                              '[overrides]\n"/private/user/path" = "nightly"\n')
        self.cargo = self.root / "cargo/bin"
        write(self.cargo / "cargo", CARGO_STUB, executable=True)
        write(self.cargo / "rustc", '#!/usr/bin/env python3\nimport os, sys\n'
              'print("rustc 1.99.0 (fixture)")\n'
              'if "-vV" in sys.argv:\n'
              '    print("host: " + os.environ.get("FAKE_RUST_HOST", "x86_64-unknown-linux-gnu"))\n', executable=True)
        write(self.cargo / "rustup", "rustup proxy", executable=True)
        # Verify symlink dereferencing and hard-link preservation in the archive.
        (self.cargo / "rustdoc").symlink_to(self.cargo / "rustup")
        write(self.cargo / "private-tool", "not exported", executable=True)
        write(self.cargo.parent / "credentials.toml", "secret")
        self.downloads = []
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
        data = b"test runtime" if ".jar" in url else b"test pom"
        destination.write_bytes(hashlib.sha512(data).hexdigest().encode() if url.endswith(".sha512") else data)

    def export(self, kind="gradle", *extra, output=None):
        args = ["--assets", kind, "--output", str(output or self.output)]
        if kind in ("gradle", "all"):
            args.extend(["--sdk-root", str(self.sdk), "--gradle-bin", str(self.gradle)])
        if kind in ("rust", "all"):
            args.extend(["--rustup-home", str(self.rustup), "--cargo-bin-dir", str(self.cargo)])
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
        expected_names = {path.name for path in root.iterdir() if ".tar.gz" in path.name}
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

    def test_rust_exports_relocatable_host_toolchain_without_modifying_crate(self):
        self.assertEqual(0, self.export("rust"), self.error)
        self.verify_checksums()
        with self.archive("rust-toolchain-linux-x86_64") as archive:
            names = archive.getnames()
            self.assertIn(f"rustup/toolchains/{ASSETS.TOOLCHAIN}/bin/rustc", names)
            self.assertTrue(archive.getmember("cargo/bin/rustdoc").islnk())
            self.assertEqual(b"rustup proxy", archive.extractfile("cargo/bin/rustdoc").read())
            self.assertTrue(archive.getmember("cargo/bin/rustc").mode & 0o111)
            self.assertFalse(any("android" in name or "private-tool" in name or "credentials" in name for name in names))
            settings = archive.extractfile("rustup/settings.toml").read().decode()
            self.assertNotIn("/private/user/path", settings)
            self.assertIn(f'default_toolchain = "{ASSETS.TOOLCHAIN}"', settings)
        with self.archive("native-adblock-vendor") as archive:
            self.assertIn("Cargo.lock", archive.getnames())
            self.assertIn(".cargo/config.toml", archive.getnames())
            self.assertIn("vendor/example-1.0/lib.rs", archive.getnames())
        self.assertFalse((self.crate / "Cargo.lock").exists())
        self.assertFalse((self.crate / "vendor").exists())
        calls = self.calls("cargo")
        self.assertIn(["vendor", "--locked", "--versioned-dirs", "vendor"], [call["args"] for call in calls])
        self.assertIn(["metadata", "--locked", "--offline", "--format-version", "1"], [call["args"] for call in calls])
        self.assertTrue(all(call["rustup"] == str(self.rustup) for call in calls))
        self.assertTrue(all(call["toolchain"] == ASSETS.TOOLCHAIN for call in calls))

    def test_rust_reuses_versioned_default_and_existing_lockfile(self):
        versioned = f"1.99.0-{ASSETS.HOST_TRIPLE}"
        self.toolchain.rename(self.toolchain.with_name(versioned))
        self.settings.write_text(f'# [documentation] is a comment, not a table\n'
                                 f'version = "12"\ndefault_toolchain = "{versioned}"\n')
        write(self.crate / "Cargo.lock", "previous lockfile\n")
        self.assertEqual(0, self.export("rust"), self.error)
        self.assertTrue(all(call["toolchain"] == versioned for call in self.calls("cargo")))
        self.assertNotIn(["generate-lockfile"], [call["args"] for call in self.calls("cargo")])
        with self.archive("native-adblock-vendor") as archive:
            self.assertEqual(b"previous lockfile\n", archive.extractfile("Cargo.lock").read())
        with self.archive("rust-toolchain-linux-x86_64") as archive:
            self.assertIn(f"rustup/toolchains/{ASSETS.TOOLCHAIN}/bin/rustc", archive.getnames())
            self.assertFalse(any(versioned in name for name in archive.getnames()))

    def test_rust_failures_preserve_previous_exports(self):
        for environment, message in (({"FAIL_VENDOR": "1"}, "Cargo could not vendor"),
                                     ({"FAIL_METADATA": "1"}, "cannot resolve the exported dependencies offline"),
                                     ({"FAKE_RUST_HOST": "aarch64-unknown-linux-gnu"}, "Expected x86_64")):
            with self.subTest(environment=environment), mock.patch.dict(os.environ, environment):
                write(self.output / "native-adblock-vendor.tar.gz", "previous")
                self.assertEqual(1, self.export("rust"))
                self.assertIn(message, self.error)
                self.assertEqual("previous", (self.output / "native-adblock-vendor.tar.gz").read_text())
        shutil.rmtree(self.toolchain)
        self.settings.write_text('version = "12"\ndefault_toolchain = "nightly-x86_64-unknown-linux-gnu"\n')
        self.assertEqual(1, self.export("rust"))
        self.assertIn("No installed stable or version-numbered", self.error)

    def test_combined_export_and_stale_parts_in_both_directions(self):
        write(self.output / "gradle-deps.tar.gz", "old whole archive")
        write(self.output / "rust/native-adblock-vendor.tar.gz.part-999", "old part")
        original_writer = ASSETS.SplitArchiveWriter
        with mock.patch.object(ASSETS, "SplitArchiveWriter", side_effect=lambda name: original_writer(name, 100)):
            self.assertEqual(0, self.export("all"), self.error)
        self.assertFalse((self.output / "gradle-deps.tar.gz").exists())
        self.assertGreater(len(list(self.output.glob("gradle-deps.tar.gz.part-*"))), 1)
        self.assertFalse((self.output / "rust/native-adblock-vendor.tar.gz.part-999").exists())
        self.verify_checksums()
        self.verify_checksums(self.output / "rust")
        with self.archive("gradle-deps") as archive:
            self.assertIn("caches/modules-2/metadata-2.107/module-artifact.bin", archive.getnames())
        self.assertEqual(0, self.export("all"), self.error)
        self.assertFalse(list(self.output.glob("*.part-*")))
        self.assertFalse(list((self.output / "rust").glob("*.part-*")))


class DispatchTest(unittest.TestCase):
    def test_cli_and_legacy_wrappers_help_without_tools(self):
        commands = ([sys.executable, str(ROOT / "scripts/export-android-test-assets.py")],
                    ["sh", str(ROOT / "scripts/export-android-test-assets.sh")],
                    ["sh", str(ROOT / "scripts/export-rust-test-assets.sh")])
        for command in commands:
            result = subprocess.run([*command, "--help"], text=True, capture_output=True)
            self.assertEqual(0, result.returncode, result.stderr)
            self.assertIn("--assets", result.stdout)
            self.assertIn("250,000,000", result.stdout)

    def test_defaults_and_compatibility_alias(self):
        self.assertEqual("gradle", ASSETS.parse_args([]).assets)
        self.assertEqual("all", ASSETS.parse_args(["--with-rust"]).assets)
        self.assertEqual(ASSETS.PROJECT_ROOT / "build/android-test-assets/rust",
                         ASSETS.parse_args(["--assets", "rust"]).output)
        for arguments in (("--assets", "rust", "--sdk-root", "/sdk"),
                          ("--rustup-home", "/rustup"), ("--assets", "rust", "--with-rust"),
                          ("--output", ""), ("--output",)):
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
                    self.assertEqual(0, ASSETS.main(["--assets", "all", "--output", str(output)]))
                native.assert_not_called()
                commands = [call.args[0] for call in run.call_args_list]
                builds = [command for command in commands if command[1] == "build"]
                containers = [command for command in commands if command[1] == "run"]
                self.assertEqual(2, len(builds))
                self.assertEqual(2, len(containers))
                for command in builds + containers:
                    self.assertIn("linux/amd64", command)
                self.assertIn(f"{output}:/export", containers[0])
                self.assertIn(f"{output}/rust:/export", containers[1])
                self.assertIn("python3", containers[0])
                self.assertIn(ASSETS.DOCKER_BOOTSTRAP, containers[1])
                self.assertNotIn("RUSTUP_TOOLCHAIN=", " ".join(containers[1]))
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
        for name in ("android", "rust"):
            dockerfile = (ROOT / f"scripts/Dockerfile.{name}-test-assets").read_text()
            self.assertIn("python3", dockerfile)
        android = (ROOT / "scripts/Dockerfile.android-test-assets").read_text()
        self.assertIn("https://cache-redirector.jetbrains.com/intellij-jbr/${JBR_ARCHIVE}", android)


class DownloadTest(unittest.TestCase):
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
