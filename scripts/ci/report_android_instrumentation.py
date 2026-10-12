#!/usr/bin/env python3
"""Print connected Android test failures from Gradle's JUnit XML reports."""

import os
import sys
import xml.etree.ElementTree as ET
from pathlib import Path


def report(results: Path) -> str:
    files = sorted(results.rglob("TEST-*.xml"))
    if not files:
        return f"No JUnit XML reports found in {results}. Check the uploaded device logcat."

    failures = []
    unreadable = []
    for path in files:
        try:
            suite = ET.parse(path).getroot()
        except (ET.ParseError, OSError) as error:
            unreadable.append(f"Could not read {path}: {error}")
            continue
        for test in suite.iter("testcase"):
            for result in test:
                if result.tag not in ("failure", "error"):
                    continue
                name = f"{test.get('classname', '<unknown>')}.{test.get('name', '<unknown>')}"
                stack = (result.text or "").strip()
                message = result.get("message") or stack.split("\n", 1)[0] or "No failure message"
                failures.append((name, result.tag, message, stack))

    lines = [f"Android instrumentation: {len(files)} JUnit XML report(s) found."]
    for name, kind, message, stack in failures:
        lines.append(f"\nFAIL {name} ({kind}): {message}")
        if stack:
            lines.append(stack)
    lines.extend(unreadable)
    if not failures:
        lines.append("No failing test cases recorded in XML. Check the uploaded device logcat.")
    return "\n".join(lines)


def main() -> None:
    results = Path(sys.argv[1]) if len(sys.argv) > 1 else Path(
        "app/build/outputs/androidTest-results/connected"
    )
    output = report(results)
    print(output)
    summary = os.environ.get("GITHUB_STEP_SUMMARY")
    if summary:
        # An indented code block preserves the full JUnit assertion/stack trace.
        with Path(summary).open("a", encoding="utf-8") as handle:
            handle.write("## Android instrumentation test results\n\n")
            handle.write("\n".join(f"    {line}" for line in output.splitlines()))
            handle.write("\n\n")


if __name__ == "__main__":
    main()
