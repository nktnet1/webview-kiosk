#!/usr/bin/env python3
"""Build and check the R8-minified smokeRelease APK for the startup regression.

With no APK argument, use the release-equivalent smokeRelease variant signed by
Android Gradle Plugin with the local debug key (never production credentials).
Pass an APK path to inspect an existing release artifact without rebuilding.
Requires Android SDK Build Tools' dexdump, but no emulator or extra packages.
"""

import argparse
import os
import re
import shutil
import subprocess
import sys
import tempfile
import zipfile
from collections import deque
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SMOKE_APK = ROOT / "app/build/outputs/apk/smokeRelease/app-smokeRelease.apk"
TARGET = "Ljava9/util/concurrent/ThreadLocalRandom;"
CLASS_HEADER = re.compile(r"^Class #\d+\s+-")
CLASS_DESCRIPTOR = re.compile(r"^\s+Class descriptor\s+: '([^']+)'$")
MEMBER_HEADER = re.compile(r"^    #\d+\s+: \(in L[^)]+\)$", re.MULTILINE)
INSTRUCTION = re.compile(
    r"^[0-9a-f]+:[ \t]+[0-9a-f ]+\|([0-9a-f]+):[ \t]+([\w/-]+)(?:[ \t]+(.*))?$",
    re.MULTILINE,
)
DEX_NAME = re.compile(r"classes(?:[2-9][0-9]*)?\.dex$")


class DexCheckError(Exception):
    """An expected failure that should fail the release job with a clear error."""


def find_dexdump():
    tool = shutil.which("dexdump")
    if tool:
        return tool

    roots = [os.getenv("ANDROID_HOME"), os.getenv("ANDROID_SDK_ROOT")]
    properties = ROOT / "local.properties"
    if properties.is_file():
        for line in properties.read_text(encoding="utf-8").splitlines():
            if line.startswith("sdk.dir="):
                roots.append(line.split("=", 1)[1].replace("\\:", ":"))

    for root in filter(None, roots):
        candidates = sorted(
            (Path(root).expanduser() / "build-tools").glob("*/dexdump"),
            key=lambda path: tuple(int(x) for x in re.findall(r"\d+", path.parent.name)),
            reverse=True,
        )
        for candidate in candidates:
            if candidate.is_file() and os.access(candidate, os.X_OK):
                return str(candidate)
    raise DexCheckError(
        "dexdump not found. Install Android SDK Build Tools and set ANDROID_HOME "
        "or ANDROID_SDK_ROOT."
    )


def build_smoke_apk():
    """Build a release-minified APK without requiring a production keystore."""
    wrapper = ROOT / ("gradlew.bat" if os.name == "nt" else "gradlew")
    if not wrapper.is_file():
        raise DexCheckError(f"Gradle wrapper not found: {wrapper}")
    print("Building R8-minified smokeRelease APK (signed with local debug key)...", flush=True)
    try:
        result = subprocess.run(
            [str(wrapper), ":app:assembleSmokeRelease", "--build-cache", "--console=plain"],
            cwd=ROOT,
            timeout=1200,
            check=False,
        )
    except (OSError, subprocess.TimeoutExpired) as error:
        raise DexCheckError(f"Could not build smokeRelease APK: {error}") from error
    if result.returncode:
        raise DexCheckError(f"smokeRelease build failed (Gradle exit {result.returncode}).")
    if not SMOKE_APK.is_file():
        raise DexCheckError(f"Gradle succeeded but smokeRelease APK was not found: {SMOKE_APK}")
    return SMOKE_APK


def get_target_class(lines):
    """Read a dexdump stream without buffering the other (potentially huge) classes."""
    selected = False
    buffer = []
    for line in lines:
        if CLASS_HEADER.match(line):
            if selected:
                return "".join(buffer)
            selected = False
            buffer = []
        descriptor = CLASS_DESCRIPTOR.match(line.rstrip("\n"))
        if descriptor and descriptor.group(1) == TARGET:
            selected = True
        if selected:
            buffer.append(line)
    return "".join(buffer) if selected else None


def method_code(class_text, name, signature):
    """Extract DEX instructions for one named member, independent of offsets."""
    boundaries = list(MEMBER_HEADER.finditer(class_text))
    for index, member in enumerate(boundaries):
        end = boundaries[index + 1].start() if index + 1 < len(boundaries) else len(class_text)
        part = class_text[member.end():end]
        if (
            re.search(rf"^      name\s+: '{re.escape(name)}'$", part, re.MULTILINE)
            and re.search(rf"^      type\s+: '{re.escape(signature)}'$", part, re.MULTILINE)
        ):
            instructions = []
            for match in INSTRUCTION.finditer(part):
                instructions.append((int(match.group(1), 16), match.group(2), match.group(3) or ""))
            return instructions
    return None


def check_guard(class_text):
    """Prove false-initialized returns and true-initialized throws in setSeed."""
    if not re.search(r"^\s+Superclass\s+: 'Ljava/util/Random;'$", class_text, re.MULTILINE):
        raise DexCheckError("ThreadLocalRandom no longer extends java.util.Random.")
    if not re.search(
        r"^\s+name\s+: 'initialized'\n\s+type\s+: 'Z'$", class_text, re.MULTILINE
    ):
        raise DexCheckError("ThreadLocalRandom.initialized boolean field is missing.")

    constructor = method_code(class_text, "<init>", "()V")
    if not constructor:
        raise DexCheckError("ThreadLocalRandom constructor is missing.")
    super_calls = [
        i for i, (_, op, args) in enumerate(constructor)
        if op.startswith("invoke-direct") and "Ljava/util/Random;.<init>:()V" in args
    ]
    initializers = [
        i for i, (_, op, args) in enumerate(constructor)
        if op == "iput-boolean" and f"{TARGET}.initialized:Z" in args
    ]
    if not super_calls or not initializers or min(initializers) <= min(super_calls):
        raise DexCheckError("ThreadLocalRandom constructor does not initialise its guard after super().")
    init_idx = initializers[0]
    init_register = constructor[init_idx][2].split(",", 1)[0]
    if not any(
        op.startswith("const/") and args.startswith(f"{init_register},")
        and re.search(r"#int 1(?:\s|$)", args)
        for _, op, args in constructor[min(super_calls) + 1:init_idx]
    ):
        raise DexCheckError("ThreadLocalRandom constructor does not set initialized = true.")

    code = method_code(class_text, "setSeed", "(J)V")
    if not code or len(code) < 4:
        raise DexCheckError("ThreadLocalRandom.setSeed(long) has no valid guarded implementation.")
    _, load_op, load_args = code[0]
    if load_op != "iget-boolean" or f"{TARGET}.initialized:Z" not in load_args:
        raise DexCheckError("R8 removed the initialized read from ThreadLocalRandom.setSeed().")
    guard_register = load_args.split(",", 1)[0]
    _, condition, condition_args = code[1]
    branch = re.fullmatch(r"(v\d+),\s*([0-9a-f]+)(?:\s*//.*)?", condition_args)
    if condition not in ("if-nez", "if-eqz") or not branch or branch.group(1) != guard_register:
        raise DexCheckError("R8 removed the initialized conditional from ThreadLocalRandom.setSeed().")

    addresses = {address: idx for idx, (address, _, _) in enumerate(code)}
    target = addresses.get(int(branch.group(2), 16))
    if target is None:
        raise DexCheckError("ThreadLocalRandom.setSeed() branches outside its method body.")
    # When initialized is false, we must return. When true, we must throw.
    false_index = 2 if condition == "if-nez" else target
    true_index = target if condition == "if-nez" else 2
    if code[false_index][1] != "return-void":
        raise DexCheckError("ThreadLocalRandom.setSeed() no longer returns during construction.")
    exception_path = code[true_index:]
    # Do not accept an unreachable throw that appears *after* a return.
    first_terminal = next(
        (i for i, (_, op, _) in enumerate(exception_path) if op in ("throw", "return-void")),
        None,
    )
    if first_terminal is None or exception_path[first_terminal][1] != "throw" or not any(
        op == "new-instance" and "Ljava/lang/UnsupportedOperationException;" in args
        for _, op, args in exception_path[:first_terminal]
    ):
        raise DexCheckError("ThreadLocalRandom.setSeed() no longer rejects seeding after construction.")


def dump_class(dexdump, dex_path):
    """Stream full dexdump output; retain only target class and short diagnostics."""
    tail = deque(maxlen=12)
    try:
        with subprocess.Popen(
            [dexdump, "-d", "-n", str(dex_path)],
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            text=True,
            errors="replace",
        ) as process:
            # Do not stop reading early even if the class is found: let dexdump
            # finish normally and report any disassembly errors.
            def lines():
                for line in process.stdout:
                    tail.append(line)
                    yield line

            found = get_target_class(lines())
            # get_target_class can return before EOF; drain remaining stdout.
            for line in process.stdout:
                tail.append(line)
            status = process.wait()
    except OSError as error:
        raise DexCheckError(f"Failed to run dexdump: {error}") from error
    if status:
        raise DexCheckError(f"dexdump failed (exit {status}): {''.join(tail).strip()}")
    return found


def check_apk(apk, dexdump):
    if not apk.is_file():
        raise DexCheckError(f"APK not found: {apk}")
    try:
        with zipfile.ZipFile(apk) as archive, tempfile.TemporaryDirectory() as folder:
            entries = sorted(
                (name for name in archive.namelist() if DEX_NAME.fullmatch(name)),
                key=lambda name: int(name[7:-4]) if name != "classes.dex" else 1,
            )
            if not entries:
                raise DexCheckError(f"APK has no classes*.dex: {apk}")
            for entry in entries:
                path = Path(folder) / entry
                with archive.open(entry) as source, path.open("wb") as target:
                    shutil.copyfileobj(source, target)
                class_text = dump_class(dexdump, path)
                if class_text is not None:
                    check_guard(class_text)
                    return entry
    except (zipfile.BadZipFile, RuntimeError, OSError) as error:
        raise DexCheckError(f"Cannot inspect APK {apk}: {error}") from error
    raise DexCheckError(
        "java9.util.concurrent.ThreadLocalRandom is missing from the release DEX. "
        "R8 may have renamed, removed, or merged the class (check the keep rule)."
    )


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "apk", nargs="?", type=Path,
        help="Existing minified APK (default: build smokeRelease with test signing)",
    )
    parser.add_argument("--dexdump", help="Path to Android SDK Build Tools' dexdump")
    args = parser.parse_args(argv)
    try:
        # No APK argument means test exactly what CI builds, with no release secrets.
        apk = args.apk.expanduser().resolve() if args.apk else build_smoke_apk()
        dexdump = args.dexdump or find_dexdump()
        dex = check_apk(apk, dexdump)
    except DexCheckError as error:
        print(f"FAIL: release DEX check: {error}", file=sys.stderr)
        return 1
    print(f"PASS: {apk} ({dex}): ThreadLocalRandom.setSeed() retains the constructor guard.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
