#!/usr/bin/env python3
"""Export Linux x86_64 Gradle/Android and Rust assets using Python 3.10+."""

import argparse
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
import urllib.request
import zipfile
from pathlib import Path


PROJECT_ROOT = Path(__file__).resolve().parents[1]
MAX_ARCHIVE_BYTES = 250_000_000  # Decimal MB, including each numbered part.
SDK_PLATFORM = "android-37.0"
SDK_BUILD_TOOLS = "36.0.0"
ROBOLECTRIC_VERSIONS = ("9-robolectric-4913185-2-i7", "10-robolectric-5803371-i7")
ROBOLECTRIC_MAVEN_URL = (
    "https://repo.maven.apache.org/maven2/org/robolectric/android-all-instrumented"
)
CMDLINE_TOOLS_ZIP = "commandlinetools-linux-15859902_latest.zip"
CMDLINE_TOOLS_SHA256 = "4e4c464f145a7512b57d088ac6c278c03c9eea610886b35a5e0804e74eedf583"
HOST_TRIPLE = "x86_64-unknown-linux-gnu"
TOOLCHAIN = f"stable-{HOST_TRIPLE}"
RUST_PROXIES = ("cargo", "rustup", "rustc", "rustdoc")
ANDROID_TARGETS = (
    "aarch64-linux-android", "armv7-linux-androideabi",
    "i686-linux-android", "x86_64-linux-android",
)


class ExportError(Exception):
    """An actionable export failure, reported without a Python traceback."""


def say(message):
    print(f"\n==> {message}", flush=True)


def run(command, *, cwd=None, env=None, capture=False, stdout=None, message=None):
    try:
        result = subprocess.run(
            [str(arg) for arg in command], cwd=cwd or PROJECT_ROOT, env=env,
            check=True, text=True, stdout=subprocess.PIPE if capture else stdout,
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
                        # Rustup proxies and LLVM soname symlinks share large files;
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


ARCHIVE_NOTES = f"""Archive limit: {MAX_ARCHIVE_BYTES} bytes (250 MB, decimal)
Archives at or below the limit: one .tar.gz file (no .part-000 suffix)
Larger archives: .tar.gz.part-000, .part-001, ...; each part is at most the limit
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


def find_gradle(args):
    version = required_gradle_version()
    candidates = []
    if args.gradle_bin:
        candidates.append(args.gradle_bin)
    else:
        if os.environ.get("GRADLE_HOME"):
            candidates.append(Path(os.environ["GRADLE_HOME"]) / "bin/gradle")
        for home in (args.gradle_user_home, os.environ.get("GRADLE_USER_HOME"), Path.home() / ".gradle"):
            if home:
                candidates.extend(sorted(Path(home).glob(
                    f"wrapper/dists/gradle-{version}-bin/*/gradle-{version}/bin/gradle"
                )))
        if shutil.which("gradle"):
            candidates.append(Path(shutil.which("gradle")))
    binary = next((Path(path).absolute() for path in candidates if executable(Path(path))), None)
    if binary is None:
        raise ExportError(f"Gradle {version} must already be installed; pass --gradle-bin /path/to/gradle")
    if f"Gradle {version}" not in run([binary, "--version"], capture=True).splitlines():
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
    if not root or not Path(root).is_dir():
        raise ExportError("Set ANDROID_SDK_ROOT or pass --sdk-root to an existing Android SDK directory")
    root = Path(root).resolve()
    if args.sdk_root and selected and Path(selected).resolve() != root:
        raise ExportError(f"local.properties selects {selected}, but --sdk-root selects {root}")
    return root


def sdk_installed(root):
    return (root / f"platforms/{SDK_PLATFORM}/android.jar").is_file() and (
        root / f"build-tools/{SDK_BUILD_TOOLS}/source.properties"
    ).is_file()


def ensure_sdk(root, work, env):
    if sdk_installed(root):
        return
    options = [f"--sdk_root={root}", "--channel=3", f"platforms;{SDK_PLATFORM}", f"build-tools;{SDK_BUILD_TOOLS}"]
    candidates = [root / "cmdline-tools/latest/bin/sdkmanager"]
    candidates.extend(sorted(root.glob("cmdline-tools/*/bin/sdkmanager")))
    candidates.append(root / "tools/bin/sdkmanager")
    if shutil.which("sdkmanager"):
        candidates.append(Path(shutil.which("sdkmanager")))
    manager = next((path for path in candidates if executable(path)), None)
    if manager:
        try:
            run([manager, *options], env=env)
            if sdk_installed(root):
                return
        except ExportError:
            pass
    say("Downloading current Android command-line tools (SHA-256 verified)")
    archive = work / CMDLINE_TOOLS_ZIP
    download(f"https://dl.google.com/android/repository/{CMDLINE_TOOLS_ZIP}", archive)
    if file_digest(archive) != CMDLINE_TOOLS_SHA256:
        raise ExportError("Android command-line tools SHA-256 mismatch")
    tools = work / "sdk-tools"
    with zipfile.ZipFile(archive) as source:
        for member in source.infolist():
            destination = tools / member.filename
            if not destination.resolve().is_relative_to(tools.resolve()):
                raise ExportError("Unsafe path in Android command-line tools archive")
            source.extract(member, tools)
            mode = (member.external_attr >> 16) & 0o777
            if mode:
                destination.chmod(mode)
    manager = tools / "cmdline-tools/bin/sdkmanager"
    if not executable(manager):
        raise ExportError("Downloaded SDK archive did not contain executable sdkmanager")
    run([manager, *options], env=env, message=(
        f"Could not install platforms;{SDK_PLATFORM} and build-tools;{SDK_BUILD_TOOLS}; "
        "check network access and SDK licences"
    ))
    if not sdk_installed(root):
        raise ExportError("Android SDK components are still missing after installation")


def export_gradle(args, output):
    if not shutil.which("java"):
        raise ExportError("Java is required; use the project's JetBrains JDK 21")
    gradle = find_gradle(args)
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
            env = dict(os.environ, ANDROID_HOME=str(sdk), ANDROID_SDK_ROOT=str(sdk), GRADLE_USER_HOME=str(home))
            ensure_sdk(sdk, work, env)
            say("Resolving Android JVM test dependencies (no compilation or test execution)")
            run([gradle, "--no-daemon", "--no-configuration-cache", "--console=plain",
                 "--init-script", PROJECT_ROOT / "scripts/resolve-android-test-assets.init.gradle",
                 ":app:resolveAndroidTestAssets"], env=env,
                message="Gradle could not resolve the required Android JVM test dependencies")
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
            sdk_paths = [f"platforms/{SDK_PLATFORM}", f"build-tools/{SDK_BUILD_TOOLS}"]
            sdk_paths.extend(name for name in ("licenses", "platform-tools") if (sdk / name).is_dir())
            export_archive(staging / "android-sdk.tar.gz", [(sdk / name, name) for name in sdk_paths])

            def exclude_locks(member):
                name = Path(member.name).name
                return None if name.endswith(".lock") or name == "gc.properties" else member

            export_archive(staging / "gradle-deps.tar.gz", [(modules, "caches/modules-2")], exclude=exclude_locks)
            maven_paths = [f"org/robolectric/android-all-instrumented/{version}" for version in ROBOLECTRIC_VERSIONS]
            export_archive(staging / "robolectric-maven.tar.gz", [(maven / name, name) for name in maven_paths])
            contents = f"""Project task: :app:resolveAndroidTestAssets (resolves dependencies only)
Platform: Linux x86_64
Gradle distribution: not exported (keep separately; version pinned by Gradle wrapper)
Android SDK: platforms/{SDK_PLATFORM}, build-tools/{SDK_BUILD_TOOLS}, optional licences/platform-tools
Gradle dependencies: caches/modules-2 (artifacts and resolution metadata; excludes *.lock and gc.properties)
Robolectric: Android 9 / API 28 and Android 10 / API 29 Maven runtime artifacts
Gradle test invocation: not run (no compilation or tests)
Gradle deps destination: $GRADLE_USER_HOME/caches/modules-2/ (extract into $GRADLE_USER_HOME/)
Android SDK destination: $ANDROID_SDK_ROOT/
Robolectric Maven destination: ~/.m2/repository/ (default)
"""
            publish(
                staging, output, ("android-sdk.tar.gz", "gradle-deps.tar.gz", "robolectric-maven.tar.gz"),
                contents, obsolete=("gradle-cache.tar.gz", "gradle-cache.tar.gz.part-*", "gradle-*-bin.zip.part-*"),
            )


def copy_crate(source, destination):
    destination.mkdir(parents=True, exist_ok=True)
    for name in ("Cargo.toml", "rust-toolchain.toml", "src"):
        path = source / name
        if path.is_dir():
            shutil.copytree(path, destination / name)
        else:
            shutil.copy2(path, destination / name)
    if (source / "Cargo.lock").is_file():
        shutil.copy2(source / "Cargo.lock", destination / "Cargo.lock")


def export_rust(args, output):
    source = PROJECT_ROOT / "app/native-adblock"
    if not all((source / name).exists() for name in ("Cargo.toml", "rust-toolchain.toml", "src")):
        raise ExportError("Native adblock Cargo manifest, toolchain declaration, or sources are missing")
    rustup = (args.rustup_home or Path(os.environ.get("RUSTUP_HOME", Path.home() / ".rustup"))).resolve()
    cargo_on_path = shutil.which("cargo")
    if not args.cargo_bin_dir and not cargo_on_path:
        raise ExportError("cargo is required on native Linux; pass --cargo-bin-dir to the rustup proxies")
    # Do not resolve the cargo proxy symlink: its directory contains all proxies.
    binaries = (args.cargo_bin_dir or Path(cargo_on_path).parent).absolute()
    for binary in RUST_PROXIES:
        if not executable(binaries / binary):
            raise ExportError(f"Rustup proxy {binary} is missing from {binaries}")
    settings_file = rustup / "settings.toml"
    if not settings_file.is_file():
        raise ExportError(f"Rustup settings.toml is missing from {rustup}")
    # These rustup fields contain only format numbers and toolchain identifiers.
    # Read top-level quoted fields only, excluding user override tables. Avoid a
    # TOML dependency so the Ubuntu 22.04 Gradle image can use its Python 3.10.
    settings_text = re.split(
        r"^\s*\[", settings_file.read_text(encoding="utf-8"), maxsplit=1, flags=re.MULTILINE,
    )[0]
    settings = dict(re.findall(
        r'^\s*(version|default_toolchain)\s*=\s*"([^"\\]*)"\s*(?:#[^\n]*)?$',
        settings_text, re.MULTILINE,
    ))
    source_toolchain = TOOLCHAIN
    if not executable(rustup / f"toolchains/{source_toolchain}/bin/rustc"):
        source_toolchain = settings.get("default_toolchain", "")
        if not re.fullmatch(rf"[0-9]+(?:\.[0-9]+){{1,2}}-{HOST_TRIPLE}", source_toolchain) or not executable(
            rustup / f"toolchains/{source_toolchain}/bin/rustc"
        ):
            raise ExportError(f"No installed stable or version-numbered {HOST_TRIPLE} toolchain in {rustup}")
    env = dict(os.environ, RUSTUP_HOME=str(rustup), RUSTUP_TOOLCHAIN=source_toolchain,
               RUSTUP_AUTO_INSTALL="0", PATH=str(binaries) + os.pathsep + os.environ.get("PATH", ""))
    host = re.search(r"^host: (.+)$", run([binaries / "rustc", "-vV"], env=env, capture=True), re.MULTILINE)
    if not host or host[1] != HOST_TRIPLE:
        raise ExportError(f"Expected {HOST_TRIPLE} rustc, found: {host[1] if host else 'unknown'}")
    version = str(settings.get("version", ""))
    if not version.isdigit():
        raise ExportError("Cannot determine the rustup settings format version")
    output.mkdir(parents=True, exist_ok=True)
    build = PROJECT_ROOT / "build"
    build.mkdir(exist_ok=True)
    with tempfile.TemporaryDirectory(prefix=".rust-test-export.", dir=build) as directory:
        with tempfile.TemporaryDirectory(prefix=".staging.", dir=output) as staging_directory:
            work, staging = Path(directory), Path(staging_directory)
            crate = work / "native-adblock"
            copy_crate(source, crate)
            say("Resolving and vendoring native-adblock Cargo dependencies")
            cargo = binaries / "cargo"
            if not (crate / "Cargo.lock").is_file():
                run([cargo, "generate-lockfile"], cwd=crate, env=env,
                    message="Cargo could not generate the native-adblock lockfile")
            (crate / ".cargo").mkdir()
            with (crate / ".cargo/config.toml").open("w", encoding="utf-8") as config:
                run([cargo, "vendor", "--locked", "--versioned-dirs", "vendor"], cwd=crate, env=env, stdout=config,
                    message="Cargo could not vendor the locked native-adblock dependencies")
            if (not (crate / "Cargo.lock").is_file() or not (crate / "vendor").is_dir() or
                    not (crate / ".cargo/config.toml").stat().st_size):
                raise ExportError("Cargo vendoring did not produce the expected source config and lockfile")
            run([cargo, "metadata", "--locked", "--offline", "--format-version", "1"],
                cwd=crate, env=env, stdout=subprocess.DEVNULL,
                message="Cargo cannot resolve the exported dependencies offline")
            clean_settings = work / "settings.toml"
            clean_settings.write_text(f'version = "{version}"\ndefault_host_triple = "{HOST_TRIPLE}"\n'
                                      f'default_toolchain = "{TOOLCHAIN}"\nprofile = "minimal"\n', encoding="utf-8")
            toolchain_path = f"rustup/toolchains/{TOOLCHAIN}"

            def exclude_android(member):
                return None if any(
                    member.name == f"{toolchain_path}/lib/rustlib/{target}" or
                    member.name.startswith(f"{toolchain_path}/lib/rustlib/{target}/")
                    for target in ANDROID_TARGETS
                ) else member

            say("Exporting the Linux host Rust toolchain and vendored Cargo dependencies")
            entries = [(binaries / name, f"cargo/bin/{name}") for name in RUST_PROXIES]
            entries.extend(((rustup / "toolchains" / source_toolchain, toolchain_path),
                            (clean_settings, "rustup/settings.toml")))
            export_archive(staging / "rust-toolchain-linux-x86_64.tar.gz", entries,
                           exclude=exclude_android, dereference=True)
            export_archive(staging / "native-adblock-vendor.tar.gz", [
                (crate / name, name) for name in ("Cargo.lock", ".cargo/config.toml", "vendor")
            ])
            contents = f"""Platform: Linux x86_64 ({HOST_TRIPLE})
Rust channel: {TOOLCHAIN} (host toolchain; no Android targets)
Installed source toolchain: {source_toolchain}
Rust version: {run([binaries / 'rustc', '--version'], env=env, capture=True)}
Cargo version: {run([cargo, '--version'], env=env, capture=True)}
Crate: app/native-adblock/Cargo.toml
Cargo dependencies: vendored from Cargo.lock; verified with cargo metadata --locked --offline
Cargo tests: not run (no compilation or tests)
Android NDK: not required, installed, or exported
Rust toolchain destination: extract into a writable directory
Rust environment: CARGO_HOME=<dir>/cargo, RUSTUP_HOME=<dir>/rustup, PATH=$CARGO_HOME/bin:$PATH
Cargo dependencies destination: extract into app/native-adblock/; cd app/native-adblock before running cargo test --offline --locked
Toolchain selection: set RUSTUP_TOOLCHAIN={TOOLCHAIN} to avoid installing Android targets from rust-toolchain.toml
"""
            publish(staging, output,
                    ("rust-toolchain-linux-x86_64.tar.gz", "native-adblock-vendor.tar.gz"), contents)


# Run Python inside the container too, including checkout isolation. No host
# shell interpolation is used for paths or user arguments.
DOCKER_BOOTSTRAP = """
import pathlib, shutil, subprocess, sys
kind = sys.argv[1]
root = pathlib.Path('/tmp/project')
pathlib.Path('/tmp/assets-home').mkdir(exist_ok=True)
if kind == 'gradle':
    shutil.copytree('/source', root, ignore=shutil.ignore_patterns(
        '.git', '.gradle', '.idea', 'build', 'docs', 'local.properties',
        'release', '*.jks', '.env*', '*.bundle', '*.patch', 'target', 'vendor',
        '__pycache__', '.cargo'))
    pathlib.Path('/var/cache/android-test-assets/gradle').mkdir(parents=True, exist_ok=True)
    options = ['--sdk-root', '/opt/android-sdk', '--gradle-bin', '/opt/gradle/bin/gradle',
               '--gradle-user-home', '/var/cache/android-test-assets/gradle']
else:
    source = pathlib.Path('/source/app/native-adblock')
    crate = root / 'app/native-adblock'
    crate.mkdir(parents=True)
    for name in ('Cargo.toml', 'rust-toolchain.toml', 'Cargo.lock'):
        if (source / name).is_file():
            shutil.copy2(source / name, crate / name)
    shutil.copytree(source / 'src', crate / 'src')
    (root / 'scripts').mkdir()
    shutil.copy2('/source/scripts/export-android-test-assets.py', root / 'scripts')
    pathlib.Path('/var/cache/native-adblock-rust/cargo').mkdir(parents=True, exist_ok=True)
    options = ['--cargo-bin-dir', '/usr/local/cargo/bin', '--rustup-home', '/usr/local/rustup']
subprocess.run([sys.executable, str(root / 'scripts/export-android-test-assets.py'),
                '--assets', kind, '--output', '/export', *options], check=True)
"""


def export_docker(kind, output):
    if not shutil.which("docker"):
        raise ExportError("Docker is required to export Linux x86_64 test assets on this host")
    operating_system = run(["docker", "info", "--format", "{{.OSType}}"], capture=True,
                           message="The Docker daemon is unavailable; start Docker and try again")
    if operating_system != "linux":
        raise ExportError("Docker must be configured to run Linux containers")
    rust = kind == "rust"
    image = ("webview-kiosk-rust-test-assets:stable-bookworm" if rust else
             "webview-kiosk-android-test-assets:gradle-9.8.0-jbr21")
    dockerfile = f"Dockerfile.{'rust' if rust else 'android'}-test-assets"
    cache = ("webview-kiosk-rust-test-assets-cargo:/var/cache/native-adblock-rust" if rust else
             "webview-kiosk-android-test-assets-deps:/var/cache/android-test-assets")
    say(f"Preparing the Linux x86_64 {kind} exporter image (cached after first use)")
    run(["docker", "build", "--platform", "linux/amd64", "--tag", image,
         "--file", PROJECT_ROOT / "scripts" / dockerfile, PROJECT_ROOT / "scripts"])
    output.mkdir(parents=True, exist_ok=True)
    command = ["docker", "run", "--rm", "--platform", "linux/amd64"]
    if hasattr(os, "getuid"):
        command.extend(["--user", f"{os.getuid()}:{os.getgid()}"])
    command.extend(["--env", "HOME=/tmp/assets-home"])
    if rust:
        command.extend(["--env", "CARGO_HOME=/var/cache/native-adblock-rust/cargo",
                        "--env", "RUSTUP_HOME=/usr/local/rustup"])
    command.extend(["--volume", f"{PROJECT_ROOT}:/source:ro", "--volume", f"{output}:/export",
                    "--volume", cache, "--workdir", "/tmp", image,
                    "python3", "-c", DOCKER_BOOTSTRAP, kind])
    run(command)


def parse_args(argv=None):
    def path_argument(value):
        if not value:
            raise argparse.ArgumentTypeError("Path must not be empty")
        return Path(value)

    parser = argparse.ArgumentParser(description=__doc__, epilog="""
On Linux x86_64, use preinstalled Gradle/JetBrains JDK 21/Android SDK or a
rustup-managed host toolchain. Other hosts use Docker with Linux amd64 images.
Exports resolve dependencies without compiling or running tests. Cargo.lock is
preserved, or generated in a disposable crate copy. SHA256SUMS and CONTENTS.txt
accompany the archives. All archives automatically split above 250,000,000 bytes.
""")
    parser.add_argument("--assets", choices=("gradle", "rust", "all"), default="gradle",
                        help="assets to export (default: gradle; all puts Rust assets in output/rust)")
    parser.add_argument("--with-rust", action="store_true", help="compatibility alias for --assets all")
    parser.add_argument("--output", type=path_argument, help=(
        "output directory (default: build/android-test-assets, or its rust/ subdirectory in Rust-only mode)"
    ))
    parser.add_argument("--sdk-root", type=path_argument, help="native Linux Android SDK directory")
    parser.add_argument("--gradle-user-home", type=path_argument, help="reuse an existing native Gradle cache")
    parser.add_argument("--gradle-bin", type=path_argument, help="preinstalled native Gradle executable matching the wrapper")
    parser.add_argument("--cargo-bin-dir", type=path_argument, help="native rustup proxy binary directory")
    parser.add_argument("--rustup-home", type=path_argument, help="native installed rustup directory")
    args = parser.parse_args(argv)
    if args.with_rust:
        if args.assets == "rust":
            parser.error("--with-rust cannot be combined with --assets rust")
        args.assets = "all"
    if args.assets == "rust" and any((args.sdk_root, args.gradle_bin, args.gradle_user_home)):
        parser.error("Gradle toolchain options require --assets gradle or all")
    if args.assets == "gradle" and any((args.cargo_bin_dir, args.rustup_home)):
        parser.error("Rust toolchain options require --assets rust or all")
    args.output = (args.output or PROJECT_ROOT / "build/android-test-assets" /
                   ("rust" if args.assets == "rust" else "")).resolve()
    return args


def main(argv=None):
    args = parse_args(argv)
    native = platform.system() == "Linux" and platform.machine() == "x86_64"
    try:
        if not native and any((args.sdk_root, args.gradle_bin, args.gradle_user_home,
                               args.cargo_bin_dir, args.rustup_home)):
            raise ExportError("Toolchain overrides are native Linux options; Docker supplies its own Linux toolchains")
        kinds = ("gradle", "rust") if args.assets == "all" else (args.assets,)
        for kind in kinds:
            output = args.output / "rust" if args.assets == "all" and kind == "rust" else args.output
            if native:
                (export_gradle if kind == "gradle" else export_rust)(args, output)
            else:
                export_docker(kind, output)
    except (ExportError, OSError, ValueError, tarfile.TarError, zipfile.BadZipFile) as error:
        print(f"Error: {error}", file=sys.stderr)
        return 1
    except KeyboardInterrupt:
        print("Export interrupted", file=sys.stderr)
        return 130
    return 0


if __name__ == "__main__":
    sys.exit(main())
