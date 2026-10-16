#!/usr/bin/env python3
"""Export Linux x86_64 Android test and lint assets using Python 3.10+."""

import argparse
import contextlib
import copy
import gzip
import hashlib
import http.client
import os
import platform
import re
import shutil
import subprocess
import sys
import tarfile
import tempfile
import time
import urllib.error
import urllib.parse
import urllib.request
import zipfile
from pathlib import Path


PROJECT_ROOT = Path(__file__).resolve().parents[1]
MAX_ARCHIVE_BYTES = 180_000_000  # Decimal MB, including each numbered part.
SDK_PLATFORM = "android-37.0"
SDK_BUILD_TOOLS = "36.0.0"
ROBOLECTRIC_VERSIONS = ("9-robolectric-4913185-2-i7", "10-robolectric-5803371-i7")
ROBOLECTRIC_MAVEN_URL = (
    "https://repo.maven.apache.org/maven2/org/robolectric/android-all-instrumented"
)
CMDLINE_TOOLS_ZIP = "commandlinetools-linux-15859902_latest.zip"
CMDLINE_TOOLS_SHA256 = "4e4c464f145a7512b57d088ac6c278c03c9eea610886b35a5e0804e74eedf583"
JBR_ARCHIVE = "jbrsdk-21.0.11-linux-x64-b1163.116.tar.gz"
JBR_SHA256 = "097bf328b5bf5a236c095b8c8e5204c0eda91e5baa06b37383a7754b10671d8b"


class ExportError(Exception):
    """An actionable export failure, reported without a Python traceback."""


def say(message):
    print(f"\n==> {message}", flush=True)


def run(command, *, cwd=None, env=None, capture=False, stdout=None, message=None, input_text=None):
    try:
        result = subprocess.run(
            [str(arg) for arg in command], cwd=cwd or PROJECT_ROOT, env=env,
            check=True, text=True, stdout=subprocess.PIPE if capture else stdout, input=input_text,
        )
    except (OSError, subprocess.CalledProcessError) as error:
        raise ExportError(message or f"Could not run {command[0]}: {error}") from error
    return result.stdout.strip() if capture else ""


def file_digest(path, algorithm="sha256"):
    digest = hashlib.new(algorithm)
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


class SplitArchiveWriter:
    """Write a compressed stream to bounded parts, retaining small archives whole.

    No full-size intermediate archive is created. Parts are only finalized after
    the compressor closes successfully; callers keep them in a staging directory.
    """

    def __init__(self, path, limit=MAX_ARCHIVE_BYTES):
        if limit < 1:
            raise ValueError("Archive size limit must be positive")
        self.path = path
        self.limit = limit
        self.parts = []
        self.stream = None
        self.part_size = 0

    def write(self, data):
        pending = memoryview(data)
        size = len(pending)
        while pending:
            if self.stream is None or self.part_size == self.limit:
                self.close()
                # Fixed-width suffixes keep lexical and numeric order identical.
                if len(self.parts) >= 1000:
                    raise ExportError("Archive needs more than 1000 numbered parts")
                part = self.path.with_name(f"{self.path.name}.part-{len(self.parts):03d}")
                self.stream = part.open("wb")
                self.parts.append(part)
                self.part_size = 0
            count = min(len(pending), self.limit - self.part_size)
            self.stream.write(pending[:count])
            self.part_size += count
            pending = pending[count:]
        return size

    def flush(self):
        if self.stream is not None:
            self.stream.flush()

    def close(self):
        if self.stream is not None:
            self.stream.close()
            self.stream = None

    def __enter__(self):
        return self

    def __exit__(self, exception_type, _exception, _traceback):
        self.close()
        if exception_type is None:
            if not self.parts:
                raise ExportError(f"No archive data was produced: {self.path}")
            if len(self.parts) == 1:
                self.parts[0].replace(self.path)


def export_archive(path, entries, *, exclude=None, dereference=False):
    inodes = {}
    with SplitArchiveWriter(path) as output:
        with gzip.GzipFile(filename="", fileobj=output, mode="wb", mtime=0, compresslevel=6) as compressed:
            with tarfile.open(fileobj=compressed, mode="w|", dereference=dereference) as archive:
                for source, destination in entries:
                    def filter_member(member):
                        if exclude is not None:
                            member = exclude(member)
                        # tarfile disables hard-link detection with dereference.
                        # Tool distributions can contain symlinks to shared large files;
                        # retain their archive hard links while following symlinks.
                        if member is not None and dereference and member.isfile():
                            relative = member.name[len(destination):].lstrip("/")
                            actual = source / relative if relative else source
                            metadata = actual.stat()
                            inode = (metadata.st_ino, metadata.st_dev)
                            if metadata.st_ino and inode in inodes:
                                member.type = tarfile.LNKTYPE
                                member.linkname = inodes[inode]
                                member.size = 0
                            elif metadata.st_ino:
                                inodes[inode] = member.name
                        return member

                    archive.add(source, arcname=destination, filter=filter_member)


ARCHIVE_NOTES = f"""Archive limit: {MAX_ARCHIVE_BYTES} bytes (180 MB, decimal)
Archives at or below the limit: original filename (.tar.gz or .zip), without a part suffix
Larger archives: <filename>.part-000, .part-001, ...; each part is at most the limit
Archive reconstruction: concatenate numbered parts in suffix order before extraction.
SHA256SUMS verifies the exported files (including each numbered part).
"""


def publish(staging, output, names, contents, *, obsolete=()):
    """Publish only after every archive succeeds; leave unrelated files alone."""
    archives = sorted(staging.iterdir())
    for name in names:
        if not (staging / name).is_file() and not (staging / f"{name}.part-000").is_file():
            raise ExportError(f"Missing required export: {name}")
    for archive in archives:
        if not 0 < archive.stat().st_size <= MAX_ARCHIVE_BYTES:
            raise ExportError(f"Output exceeds archive limit or is empty: {archive.name}")
    (staging / "SHA256SUMS").write_text(
        "".join(f"{file_digest(archive)}  {archive.name}\n" for archive in archives),
        encoding="utf-8",
    )
    (staging / "CONTENTS.txt").write_text(contents + ARCHIVE_NOTES, encoding="utf-8")
    new_files = {item.name for item in staging.iterdir()}
    for item in staging.iterdir():
        item.replace(output / item.name)
    for pattern in (*names, *(f"{name}.part-*" for name in names), *obsolete):
        for previous in output.glob(pattern):
            if previous.name not in new_files:
                previous.unlink()
    say(f"Export complete: {output}")


def download(url, destination):
    """Bound memory, retry transient failures, and never leave partial targets."""
    temporary = destination.with_name(destination.name + ".download")
    try:
        for attempt in range(4):
            try:
                with urllib.request.urlopen(url, timeout=60) as response:
                    with temporary.open("wb") as output:
                        shutil.copyfileobj(response, output, length=1024 * 1024)
                temporary.replace(destination)
                return
            except (urllib.error.URLError, TimeoutError, ConnectionError, http.client.HTTPException) as error:
                if attempt == 3:
                    raise ExportError(f"Could not download {url}: {error}") from error
                time.sleep(2 ** attempt)
    finally:
        temporary.unlink(missing_ok=True)


def verified_download(url, destination, checksum):
    """Reuse verified SHA-256 cache entries without replacing them on failure."""
    if not re.fullmatch(r"[0-9a-fA-F]{64}", checksum):
        raise ExportError(f"Invalid SHA-256 checksum for {destination.name}")
    if destination.is_file() and file_digest(destination) == checksum.lower():
        say(f"Reusing verified {destination.name}")
        return destination
    destination.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix=".download.", dir=destination.parent) as directory:
        temporary = Path(directory) / destination.name
        say(f"Downloading {destination.name} (SHA-256 verified)")
        download(url, temporary)
        if file_digest(temporary) != checksum.lower():
            raise ExportError(f"SHA-256 mismatch: {destination.name}")
        temporary.replace(destination)
    return destination


def extract_zip(archive_path, destination):
    with zipfile.ZipFile(archive_path) as archive:
        for member in archive.infolist():
            target = destination / member.filename
            if not target.resolve().is_relative_to(destination.resolve()):
                raise ExportError(f"Unsafe path in ZIP archive: {member.filename}")
            archive.extract(member, destination)
            mode = (member.external_attr >> 16) & 0o777
            if mode:
                target.chmod(mode)


def extract_tar(archive_path, destination):
    # Check each member immediately before extraction so an earlier symlink
    # cannot redirect a later member outside the destination.
    root = destination.resolve()
    with tarfile.open(archive_path) as archive:
        for member in archive:
            target = destination / member.name
            if not (member.isfile() or member.isdir() or member.issym() or member.islnk()) or (
                not target.resolve().is_relative_to(root)
            ):
                raise ExportError(f"Unsafe path or file type in tar archive: {member.name}")
            if member.issym() or member.islnk():
                base = target.parent if member.issym() else destination
                if Path(member.linkname).is_absolute() or not (
                    base / member.linkname
                ).resolve().is_relative_to(root):
                    raise ExportError(f"Unsafe link in tar archive: {member.name}")
            member = copy.copy(member)
            member.uid = member.gid = member.uname = member.gname = None
            member.mode &= 0o777
            # Some Python 3.10 backports of data_filter incorrectly reject JBR's
            # safe ../java.base symlinks. Our checks above validate link targets;
            # tar_filter retains tarfile's path and permission protections.
            options = {"filter": "tar"} if hasattr(tarfile, "tar_filter") else {}
            archive.extract(member, destination, **options)


def tool_download_specs(args):
    specs = {}
    if args.download_gradle:
        wrapper = PROJECT_ROOT / "gradle/wrapper/gradle-wrapper.properties"
        properties = dict(line.strip().split("=", 1) for line in wrapper.read_text().splitlines()
                          if "=" in line and not line.lstrip().startswith(("#", "!")))
        url = properties.get("distributionUrl", "").replace("\\:", ":")
        address = urllib.parse.urlsplit(url)
        if address.scheme != "https" or not address.netloc or address.username or address.password:
            raise ExportError("Gradle downloads require an HTTPS distributionUrl without embedded credentials")
        checksum = properties.get("distributionSha256Sum", "")
        if not re.fullmatch(r"[0-9a-fA-F]{64}", checksum):
            raise ExportError("Set distributionSha256Sum in gradle-wrapper.properties before downloading Gradle")
        specs["gradle"] = (f"gradle-{required_gradle_version()}-bin.zip", url, checksum)
    if args.download_jbr:
        specs["jbr"] = (
            JBR_ARCHIVE, f"https://cache-redirector.jetbrains.com/intellij-jbr/{JBR_ARCHIVE}",
            JBR_SHA256,
        )
    if args.download_android_tools:
        specs["android-tools"] = (
            CMDLINE_TOOLS_ZIP, f"https://dl.google.com/android/repository/{CMDLINE_TOOLS_ZIP}",
            CMDLINE_TOOLS_SHA256,
        )
    return specs


def export_downloaded_tools(args):
    """Export original Linux amd64 packages; keep their unsplit cache separate."""
    specs = tool_download_specs(args)
    if not specs:
        return {}
    downloaded = {}
    output = args.output / "tools"
    output.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix=".staging.", dir=output) as directory:
        staging = Path(directory)
        for kind, (name, url, checksum) in specs.items():
            source = verified_download(url, args.download_cache / "archives" / name, checksum)
            downloaded[kind] = source
            with source.open("rb") as stream, SplitArchiveWriter(staging / name) as target:
                shutil.copyfileobj(stream, target, length=1024 * 1024)
        # Verify and preserve earlier selections when downloading only one tool.
        # Restream them too, so a lower size limit applies to every managed file.
        sums = output / "SHA256SUMS"
        previous_checksums = {}
        if sums.is_file():
            for line in sums.read_text().splitlines():
                if "  " in line:
                    checksum, name = line.split("  ", 1)
                    previous_checksums[name] = checksum
        all_names = {spec[0] for spec in specs.values()}
        for existing in output.iterdir():
            if not existing.is_file():
                continue
            name = re.sub(r"\.part-\d{3}$", "", existing.name)
            if name in (JBR_ARCHIVE, CMDLINE_TOOLS_ZIP) or re.fullmatch(
                r"gradle-[0-9][0-9A-Za-z.+-]*-bin\.zip", name
            ):
                all_names.add(name)
        for name in sorted(all_names - {spec[0] for spec in specs.values()}):
            whole = output / name
            parts = sorted(path for path in output.glob(name + ".part-*")
                           if re.fullmatch(re.escape(name) + r"\.part-\d{3}", path.name))
            if (whole.is_file() and parts) or (not whole.is_file() and
                    [path.name for path in parts] != [f"{name}.part-{index:03d}" for index in range(len(parts))]):
                raise ExportError(f"Incomplete or conflicting previous export: {name}; download that tool again")
            files = [whole] if whole.is_file() else parts
            for existing in files:
                if file_digest(existing) != previous_checksums.get(existing.name):
                    raise ExportError(f"Previous export checksum mismatch: {existing.name}; download that tool again")
            with SplitArchiveWriter(staging / name) as target:
                for existing in files:
                    with existing.open("rb") as stream:
                        shutil.copyfileobj(stream, target, length=1024 * 1024)
        names = tuple(sorted(all_names))
        contents = """Platform: Linux x86_64 (including downloads made on other hosts)
Gradle: original distribution ZIP, version and SHA-256 pinned by gradle-wrapper.properties
JBR: original JetBrains JDK 21 SDK archive, pinned by the exporter and Docker image
Android tools: original command-line tools ZIP, pinned by the exporter and Docker image
Sources: Gradle distribution, JetBrains JBR CDN, Google Android
Raw download cache: kept separately from these size-limited exports
Gradle and JBR are used automatically for native Gradle exports when selected.
Download-only mode does not unpack or execute downloaded tools.
"""
        publish(staging, output, names, contents, obsolete=(
            "node-v*.tar.xz", "node-v*.tar.xz.part-*",
            "pnpm-*.tgz", "pnpm-*.tgz.part-*",
        ))
    return downloaded


def prepare_downloaded_tools(args, downloaded, work):
    env = os.environ.copy()
    if "jbr" in downloaded:
        extract_tar(downloaded["jbr"], work / "jbr")
        java_home = work / "jbr" / JBR_ARCHIVE.removesuffix(".tar.gz")
        if not executable(java_home / "bin/java") or not executable(java_home / "bin/javac"):
            raise ExportError("Downloaded JBR archive did not contain the expected JDK executables")
        env["JAVA_HOME"] = str(java_home)
        env["PATH"] = str(java_home / "bin") + os.pathsep + env.get("PATH", "")
    if "gradle" in downloaded and not args.gradle_bin:
        extract_zip(downloaded["gradle"], work / "gradle")
        args.gradle_bin = work / "gradle" / f"gradle-{required_gradle_version()}" / "bin/gradle"
        if not executable(args.gradle_bin):
            raise ExportError("Downloaded Gradle ZIP did not contain the expected Gradle executable")
    return env


def verify_maven_checksum(target):
    checksum = target.with_name(target.name + ".sha512")
    if not target.is_file() or target.stat().st_size == 0 or not checksum.is_file():
        return False
    try:
        expected = "".join(checksum.read_text(encoding="ascii").split())
    except UnicodeError:
        return False
    return bool(re.fullmatch(r"[0-9a-fA-F]{128}", expected)) and (
        file_digest(target, "sha512") == expected.lower()
    )


def robolectric_artifact(root, version, extension):
    directory = root / "org/robolectric/android-all-instrumented" / version
    directory.mkdir(parents=True, exist_ok=True)
    target = directory / f"android-all-instrumented-{version}.{extension}"
    if verify_maven_checksum(target):
        return
    temporary = target.with_name(target.name + ".tmp")
    try:
        url = f"{ROBOLECTRIC_MAVEN_URL}/{version}/{target.name}"
        download(url, temporary)
        download(url + ".sha512", temporary.with_name(temporary.name + ".sha512"))
        if not verify_maven_checksum(temporary):
            raise ExportError(f"Robolectric checksum mismatch: {target.name}")
        temporary.replace(target)
        temporary.with_name(temporary.name + ".sha512").replace(
            target.with_name(target.name + ".sha512")
        )
    finally:
        temporary.unlink(missing_ok=True)
        temporary.with_name(temporary.name + ".sha512").unlink(missing_ok=True)


def required_gradle_version():
    wrapper = PROJECT_ROOT / "gradle/wrapper/gradle-wrapper.properties"
    if not wrapper.is_file():
        raise ExportError("The Gradle wrapper configuration was not found")
    urls = re.findall(r"^distributionUrl=(.+)$", wrapper.read_text(), re.MULTILINE)
    match = re.search(r"/gradle-([0-9][0-9A-Za-z.+-]*)-bin\.zip$", urls[-1].strip()) if urls else None
    if not match:
        raise ExportError("Cannot determine the pinned Gradle version from gradle-wrapper.properties")
    return match[1]


def executable(path):
    return path.is_file() and os.access(path, os.X_OK)


def find_gradle(args, env=None):
    env = os.environ if env is None else env
    version = required_gradle_version()
    candidates = []
    if args.gradle_bin:
        candidates.append(args.gradle_bin)
    else:
        if env.get("GRADLE_HOME"):
            candidates.append(Path(env["GRADLE_HOME"]) / "bin/gradle")
        for home in (args.gradle_user_home, env.get("GRADLE_USER_HOME"), Path.home() / ".gradle"):
            if home:
                candidates.extend(sorted(Path(home).glob(
                    f"wrapper/dists/gradle-{version}-bin/*/gradle-{version}/bin/gradle"
                )))
        binary_on_path = shutil.which("gradle", path=env.get("PATH"))
        if binary_on_path:
            candidates.append(Path(binary_on_path))
    binary = next((Path(path).absolute() for path in candidates if executable(Path(path))), None)
    if binary is None:
        raise ExportError(f"Gradle {version} is required; use --download-gradle or pass --gradle-bin /path/to/gradle")
    if f"Gradle {version}" not in run([binary, "--version"], env=env, capture=True).splitlines():
        raise ExportError(f"Local Gradle executable does not match wrapper version {version}: {binary}")
    say(f"Using locally installed Gradle {version} ({binary})")
    return binary


def sdk_root(args):
    local = PROJECT_ROOT / "local.properties"
    matches = re.findall(r"^sdk\.dir=(.*)$", local.read_text(), re.MULTILINE) if local.is_file() else []
    selected = re.sub(r"\\([\\ :!=#])", r"\1", matches[-1].strip()) if matches else ""
    if selected and not Path(selected).is_absolute():
        selected = str(PROJECT_ROOT / selected)
    root = args.sdk_root or selected or os.environ.get("ANDROID_SDK_ROOT") or os.environ.get("ANDROID_HOME")
    if not root:
        root = next((path for path in (
            Path.home() / "Android/Sdk", Path.home() / "Library/Android/sdk"
        ) if path.is_dir()), None)
    if not root and args.download_android_tools:
        root = args.download_cache / "android-sdk"
    if root and args.download_android_tools:
        Path(root).mkdir(parents=True, exist_ok=True)
    if not root or not Path(root).is_dir():
        raise ExportError("Set ANDROID_SDK_ROOT, pass --sdk-root, or use --download-android-tools to prepare an SDK")
    root = Path(root).resolve()
    if args.sdk_root and selected and Path(selected).resolve() != root:
        raise ExportError(f"local.properties selects {selected}, but --sdk-root selects {root}")
    return root


def sdk_installed(root):
    return (root / f"platforms/{SDK_PLATFORM}/android.jar").is_file() and (
        root / f"build-tools/{SDK_BUILD_TOOLS}/source.properties"
    ).is_file() and (root / "platform-tools/adb").is_file()


def ensure_sdk(root, work, env, *, tools_archive=None, accept_licenses=False):
    if sdk_installed(root):
        return
    options = [
        f"--sdk_root={root}", "--channel=3", f"platforms;{SDK_PLATFORM}",
        f"build-tools;{SDK_BUILD_TOOLS}", "platform-tools",
    ]
    candidates = [root / "cmdline-tools/latest/bin/sdkmanager"]
    candidates.extend(sorted(root.glob("cmdline-tools/*/bin/sdkmanager")))
    candidates.append(root / "tools/bin/sdkmanager")
    if shutil.which("sdkmanager"):
        candidates.append(Path(shutil.which("sdkmanager")))
    manager = next((path for path in candidates if executable(path)), None) if tools_archive is None else None

    def install(manager):
        if accept_licenses:
            run([manager, f"--sdk_root={root}", "--licenses"], env=env, input_text="y\n" * 100,
                message="Could not accept Android SDK licences")
        run([manager, *options], env=env)

    if manager:
        try:
            install(manager)
            if sdk_installed(root):
                return
        except ExportError:
            pass
    archive = tools_archive or work / CMDLINE_TOOLS_ZIP
    if tools_archive is None:
        say("Downloading current Android command-line tools (SHA-256 verified)")
        download(f"https://dl.google.com/android/repository/{CMDLINE_TOOLS_ZIP}", archive)
    if file_digest(archive) != CMDLINE_TOOLS_SHA256:
        raise ExportError("Android command-line tools SHA-256 mismatch")
    tools = work / "sdk-tools"
    extract_zip(archive, tools)
    manager = tools / "cmdline-tools/bin/sdkmanager"
    if not executable(manager):
        raise ExportError("Downloaded SDK archive did not contain executable sdkmanager")
    try:
        install(manager)
    except ExportError as error:
        raise ExportError(
            f"Could not install platforms;{SDK_PLATFORM}, build-tools;{SDK_BUILD_TOOLS}, "
            "and platform-tools; "
            "check network access and SDK licences (use --accept-sdk-licenses for noninteractive acceptance)"
        ) from error
    if not sdk_installed(root):
        raise ExportError("Android SDK components are still missing after installation")


def export_gradle(args, output, *, env=None):
    env = dict(os.environ if env is None else env)
    if not shutil.which("java", path=env.get("PATH")):
        raise ExportError("Java is required; use --download-jbr or the project's JetBrains JDK 21")
    gradle = find_gradle(args, env)
    sdk = sdk_root(args)
    output.mkdir(parents=True, exist_ok=True)
    build = PROJECT_ROOT / "build"
    build.mkdir(exist_ok=True)
    with tempfile.TemporaryDirectory(prefix=".android-test-export.", dir=build) as directory:
        with tempfile.TemporaryDirectory(prefix=".staging.", dir=output) as staging_directory:
            work, staging = Path(directory), Path(staging_directory)
            home = args.gradle_user_home.resolve() if args.gradle_user_home else work / "gradle-home"
            if args.gradle_user_home and not home.is_dir():
                raise ExportError(f"Gradle home does not exist: {home}")
            home.mkdir(exist_ok=True)
            if args.gradle_user_home:
                print("Note: reusing the supplied Gradle home; previously resolved modules may also be exported.")
            env.update(ANDROID_HOME=str(sdk), ANDROID_SDK_ROOT=str(sdk), GRADLE_USER_HOME=str(home))
            ensure_sdk(sdk, work, env, tools_archive=args.sdk_tools_archive,
                       accept_licenses=args.accept_sdk_licenses)
            say("Resolving Android test, lint, and formatting dependencies (no checks executed)")
            run([gradle, "--no-daemon", "--no-configuration-cache", "--console=plain",
                 "--init-script", PROJECT_ROOT / "scripts/resolve-android-test-assets.init.gradle",
                 ":app:resolveAndroidTestAssets"], env=env,
                message="Gradle could not resolve the required Android test and lint dependencies")
            modules = home / "caches/modules-2"
            if not (modules / "files-2.1").is_dir():
                raise ExportError("Gradle dependency artifacts were not populated")
            if not any(path.is_dir() for path in modules.glob("metadata-2.*")):
                raise ExportError("Gradle dependency resolution metadata was not populated")
            maven = (home if args.gradle_user_home else work) / "robolectric-maven"
            say("Checking/downloading Robolectric API 28/29 Maven runtimes")
            for version in ROBOLECTRIC_VERSIONS:
                for extension in ("pom", "jar"):
                    robolectric_artifact(maven, version, extension)
            say("Exporting Android SDK, Gradle dependencies, and Robolectric runtimes")
            sdk_paths = [
                f"platforms/{SDK_PLATFORM}", f"build-tools/{SDK_BUILD_TOOLS}",
                "platform-tools",
            ]
            if (sdk / "licenses").is_dir():
                sdk_paths.append("licenses")
            export_archive(staging / "android-sdk.tar.gz", [(sdk / name, name) for name in sdk_paths])

            def exclude_locks(member):
                name = Path(member.name).name
                return None if name.endswith(".lock") or name == "gc.properties" else member

            export_archive(staging / "gradle-deps.tar.gz", [(modules, "caches/modules-2")], exclude=exclude_locks)
            maven_paths = [f"org/robolectric/android-all-instrumented/{version}" for version in ROBOLECTRIC_VERSIONS]
            export_archive(staging / "robolectric-maven.tar.gz", [(maven / name, name) for name in maven_paths])
            contents = f"""Project task: :app:resolveAndroidTestAssets (resolves dependencies only)
Platform: Linux x86_64
Gradle/JBR distributions: exported in tools/ when requested with --download-gradle/--download-jbr
Android SDK: platforms/{SDK_PLATFORM}, build-tools/{SDK_BUILD_TOOLS}, platform-tools
Android SDK licences: exported when available
Gradle dependencies: caches/modules-2 (artifacts and resolution metadata; excludes *.lock and gc.properties)
Robolectric: Android 9 / API 28 and Android 10 / API 29 Maven runtime artifacts
Gradle tests and lint: dependencies resolved only (no tasks executed)
Gradle deps destination: $GRADLE_USER_HOME/caches/modules-2/ (extract into $GRADLE_USER_HOME/)
Android SDK destination: $ANDROID_SDK_ROOT/
Robolectric Maven destination: ~/.m2/repository/ (default)
Android lint: androidLintTool and its transitive dependencies included in gradle-deps
Linux AAPT2: AGP-matched artifact included in gradle-deps
Kotlin formatting: Spotless, ktfmt, and Detekt brace-check dependencies included
"""
            publish(
                staging, output, ("android-sdk.tar.gz", "gradle-deps.tar.gz", "robolectric-maven.tar.gz"),
                contents, obsolete=("gradle-cache.tar.gz", "gradle-cache.tar.gz.part-*", "gradle-*-bin.zip.part-*"),
            )


# Run Python inside the container too, including checkout isolation. No host
# shell interpolation is used for paths or user arguments.
DOCKER_BOOTSTRAP = """
import pathlib, shutil, subprocess, sys
root = pathlib.Path('/tmp/project')
pathlib.Path('/tmp/assets-home').mkdir(exist_ok=True)
shutil.copytree('/source', root, ignore=shutil.ignore_patterns(
    '.git', '.gradle', '.idea', 'build', 'node_modules', 'local.properties',
    '.source', '.tanstack', 'dist', 'release', 'docs', '*.jks', '.env*',
    '*.bundle', '*.patch', '__pycache__'))
pathlib.Path('/var/cache/android-test-assets/gradle').mkdir(parents=True, exist_ok=True)
options = ['--sdk-root', '/opt/android-sdk', '--gradle-bin', '/opt/gradle/bin/gradle',
           '--gradle-user-home', '/var/cache/android-test-assets/gradle',
           '--download-cache', '/var/cache/android-test-assets/downloads']
subprocess.run([sys.executable, str(root / 'scripts/export-android-test-assets.py'),
                '--output', '/export', *options, *sys.argv[1:]], check=True)
"""


def export_docker(args):
    if not shutil.which("docker"):
        raise ExportError("Docker is required to export Linux x86_64 test assets on this host")
    operating_system = run(["docker", "info", "--format", "{{.OSType}}"], capture=True,
                           message="The Docker daemon is unavailable; start Docker and try again")
    if operating_system != "linux":
        raise ExportError("Docker must be configured to run Linux containers")
    version = required_gradle_version()
    image = f"webview-kiosk-android-test-assets:gradle-{version}-jbr21"
    dockerfile = "Dockerfile.android-test-assets"
    cache = "webview-kiosk-android-test-assets-deps:/var/cache/android-test-assets"
    say("Preparing the Linux x86_64 Gradle exporter image (cached after first use)")
    run(["docker", "build", "--platform", "linux/amd64", "--tag", image,
         "--build-arg", f"GRADLE_VERSION={version}",
         "--file", PROJECT_ROOT / "scripts" / dockerfile, PROJECT_ROOT / "scripts"])
    args.output.mkdir(parents=True, exist_ok=True)
    command = ["docker", "run", "--rm", "--platform", "linux/amd64"]
    if hasattr(os, "getuid"):
        command.extend(["--user", f"{os.getuid()}:{os.getgid()}"])
    command.extend(["--env", "HOME=/tmp/assets-home"])
    command.extend(["--volume", f"{PROJECT_ROOT}:/source:ro", "--volume", f"{args.output}:/export",
                    "--volume", cache, "--workdir", "/tmp", image,
                    "python3", "-c", DOCKER_BOOTSTRAP, "--assets", args.assets])
    for name in ("gradle", "jbr", "android-tools"):
        if getattr(args, f"download_{name.replace('-', '_')}"):
            command.append(f"--download-{name}")
    if args.accept_sdk_licenses:
        command.append("--accept-sdk-licenses")
    run(command)


def parse_args(argv=None):
    def path_argument(value):
        if not value:
            raise argparse.ArgumentTypeError("Path must not be empty")
        return Path(value)

    parser = argparse.ArgumentParser(description=__doc__, epilog="""
On Linux x86_64, use Gradle/JBR/SDK to resolve Android tests and lint.
Other hosts use Docker with Linux amd64 images. Exports resolve dependencies
without compiling or running tests or lint. SHA256SUMS and CONTENTS.txt
accompany the archives. All exported files automatically split above 180,000,000 bytes.
Use --download-tools to include verified Linux amd64 Gradle/JBR/Android tools;
use --download-only to fetch these packages without an SDK, Java, or Docker.
""")
    parser.add_argument("--assets", choices=("gradle", "all"), default="gradle",
                        help="Android only; all is a compatibility alias for gradle")
    parser.add_argument("--output", type=path_argument, help=(
        "output directory (default: build/android-test-assets)"
    ))
    parser.add_argument("--sdk-root", type=path_argument, help="native Linux Android SDK directory")
    parser.add_argument("--gradle-user-home", type=path_argument, help="reuse an existing native Gradle cache")
    parser.add_argument("--gradle-bin", type=path_argument, help="preinstalled native Gradle executable matching the wrapper")
    downloads = parser.add_argument_group("Optional tool downloads (Linux x86_64 packages)")
    downloads.add_argument(
        "--download-tools", action="store_true",
        help="download Gradle, JBR 21, and Android SDK command-line tools",
    )
    downloads.add_argument("--download-gradle", action="store_true",
                           help="download the distribution and verify the SHA-256 pinned by the Gradle wrapper")
    downloads.add_argument("--download-jbr", action="store_true", help="download the pinned JetBrains JDK 21 SDK")
    downloads.add_argument("--download-android-tools", action="store_true",
                           help="download pinned Android command-line tools; prepare an SDK on native Linux if needed")
    downloads.add_argument("--download-only", action="store_true",
                           help="export selected downloads into output/tools without resolving project dependencies")
    downloads.add_argument("--download-cache", type=path_argument,
                           default=PROJECT_ROOT / "build/test-asset-downloads", help="cache of verified original packages")
    downloads.add_argument("--accept-sdk-licenses", action="store_true",
                           help="accept Android SDK licences noninteractively when installing SDK packages")
    args = parser.parse_args(argv)
    if args.download_tools:
        args.download_gradle = args.download_jbr = args.download_android_tools = True
    if args.download_only and not any((
        args.download_gradle, args.download_jbr, args.download_android_tools,
    )):
        parser.error("--download-only requires --download-tools or at least one specific --download-* option")
    args.output = (args.output or PROJECT_ROOT / "build/android-test-assets").resolve()
    args.download_cache = args.download_cache.resolve()
    if args.download_cache.is_relative_to(args.output / "tools"):
        parser.error("--download-cache must be outside output/tools so cached originals are not exported")
    args.sdk_tools_archive = None
    return args


def main(argv=None):
    args = parse_args(argv)
    native = platform.system() == "Linux" and platform.machine() == "x86_64"
    try:
        if not native and not args.download_only and any((args.sdk_root, args.gradle_bin, args.gradle_user_home)):
            raise ExportError("Toolchain overrides are native Linux options; Docker supplies its own Linux toolchains")
        downloaded = export_downloaded_tools(args) if native or args.download_only else {}
        if args.download_only:
            return 0
        with contextlib.ExitStack() as stack:
            env = None
            if native and downloaded:
                directory = stack.enter_context(tempfile.TemporaryDirectory(prefix=".prepared.", dir=args.download_cache))
                env = prepare_downloaded_tools(args, downloaded, Path(directory))
                args.sdk_tools_archive = downloaded.get("android-tools")
            if native:
                export_gradle(args, args.output, env=env)
            else:
                export_docker(args)
    except (ExportError, OSError, ValueError, tarfile.TarError, zipfile.BadZipFile) as error:
        print(f"Error: {error}", file=sys.stderr)
        return 1
    except KeyboardInterrupt:
        print("Export interrupted", file=sys.stderr)
        return 130
    return 0


if __name__ == "__main__":
    sys.exit(main())
