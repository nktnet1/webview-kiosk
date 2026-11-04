#!/usr/bin/env python3
"""Run the local CI suites without installing dependencies or starting an emulator."""

from __future__ import annotations

import shutil
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SUITES = ("checks", "jvm", "docs", "docs-browser")

USAGE = """Usage: python3 scripts/ci/test_local.py <suite>

Suites:
  checks          Offline CI, asset-exporter, cosmetic and scriptlet tests (Python/Node)
  jvm             Filter-engine JVM and Android JVM/Robolectric unit tests
  docs            Documentation format/lint, typecheck and Vitest tests
  docs-browser    Build docs and run Playwright Chromium tests
  all             Run all four suites in the order above

Examples:
  python3 scripts/ci/test_local.py checks
  python3 scripts/ci/test_local.py jvm
  python3 scripts/ci/test_local.py docs-browser

Requirements: Python 3.10+ and Node 24 for checks; JDK 21 and an Android SDK
for JVM/Robolectric tests; Node 24, pnpm and installed docs/node_modules for
docs tests, and Playwright Chromium for docs-browser. This runner does not
install tools or dependencies. For full Android device/AVD verification, run:
  python3 scripts/verify.py
"""


class SetupError(Exception):
    """Missing dependency or unsupported CLI argument."""


def require_tool(tool: str) -> None:
    if not shutil.which(tool):
        raise SetupError(f"'{tool}' is required for this suite.")


def run(*argv: str) -> int:
    """Return the child's exit code, preserving CI failures without a shell."""
    try:
        return subprocess.run(argv, cwd=ROOT, check=False).returncode
    except OSError as error:
        raise SetupError(f"Could not execute '{argv[0]}': {error}") from error


def checks() -> int:
    require_tool("python3")
    require_tool("node")
    print("Local tests: checking CI scripts, test assets, cosmetics and scriptlets", flush=True)
    result = run("python3", "-m", "unittest", "discover", "-s", "scripts/tests", "-p", "test_*.py")
    if result:
        return result
    # subprocess does not perform shell glob expansion; pass real test paths.
    tests = sorted((ROOT / "scripts/tests").glob("*.test.mjs"))
    if not tests:
        raise SetupError("No Node script tests found under scripts/tests.")
    return run("node", "--test", *(str(path.relative_to(ROOT)) for path in tests))


def jvm() -> int:
    require_tool("java")
    print("Local tests: running adblock JVM and Android JVM/Robolectric tests", flush=True)
    return run("sh", "gradlew", ":adblock:test", ":app:testDebugUnitTest",
               "--build-cache", "--profile", "--stacktrace")


def prepare_docs() -> None:
    require_tool("node")
    require_tool("pnpm")
    if not (ROOT / "docs/node_modules").is_dir():
        raise SetupError(
            "docs/node_modules is missing. Install the locked dependencies before running docs tests."
        )


def docs() -> int:
    prepare_docs()
    print("Local tests: checking documentation and running Vitest", flush=True)
    for arguments in (("check",), ("typecheck",), ("test",)):
        result = run("pnpm", "-C", "docs", *arguments)
        if result:
            return result
    return 0


def docs_browser() -> int:
    prepare_docs()
    print("Local tests: building documentation and running Playwright", flush=True)
    result = run("pnpm", "-C", "docs", "build")
    return result or run("pnpm", "-C", "docs", "test:e2e")


DISPATCH = {"checks": checks, "jvm": jvm, "docs": docs, "docs-browser": docs_browser}


def main(argv: list[str] | None = None) -> int:
    args = sys.argv[1:] if argv is None else argv
    if len(args) == 1 and args[0] in ("-h", "--help", "help"):
        print(USAGE, end="")
        return 0
    if not args:
        print(USAGE, end="")
        return 2
    if args[0] not in (*SUITES, "all"):
        print(f"Local tests: Unknown suite '{args[0]}'. See --help.", file=sys.stderr)
        return 2
    if len(args) > 1:
        print(f"Local tests: Unexpected option '{args[1]}'. See --help.", file=sys.stderr)
        return 2
    try:
        for suite in (SUITES if args[0] == "all" else (args[0],)):
            result = DISPATCH[suite]()
            if result:
                return result
    except SetupError as error:
        print(f"Local tests: {error}", file=sys.stderr)
        return 2
    return 0


if __name__ == "__main__":
    sys.exit(main())
