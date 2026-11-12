#!/usr/bin/env python3
"""Fail closed if Android instrumentation did not execute all critical suites.

Reads Gradle AndroidJUnitRunner JUnit XML outputs. A Gradle BUILD SUCCESSFUL
line alone may not prove instrumentation tests actually executed.
"""
import argparse
import sys
from pathlib import Path
from xml.etree import ElementTree

REQUIRED_CLASSES = frozenset({
    "MainActivityLifecycleTest",
    "PlayerSessionLifecycleInstrumentedTest",
    "RecentStorePrivacyInstrumentedTest",
})
MIN_EXECUTED_TESTS = 7


def verify(directory: Path):
    if not directory.is_dir() or directory.is_symlink():
        raise ValueError("Android connected-test JUnit result directory is missing")
    candidates = sorted(directory.rglob("*.xml"))
    if not candidates:
        raise ValueError("No connected Android JUnit XML results were created")

    executed = 0
    coverage = set()
    names = set()
    for path in candidates:
        if not path.is_file() or path.is_symlink() or path.stat().st_size > 4 * 1024 * 1024:
            raise ValueError("Invalid or oversized instrumentation XML result file")
        try:
            root = ElementTree.parse(path).getroot()
        except ElementTree.ParseError as ex:
            raise ValueError(f"Malformed Android instrumentation JUnit XML: {path.name}") from ex
        # Android Gradle Plugin reports a testsuite root; support testsuites
        # for compatible Gradle versions and ignore unrelated XML diagnostics.
        tag = root.tag.split("}", 1)[-1]
        if tag not in {"testsuite", "testsuites"}:
            continue
        suites = [root] if tag == "testsuite" else list(root.findall(".//testsuite"))
        for suite in suites:
            if int(suite.attrib.get("failures", "0")) != 0 or \
                    int(suite.attrib.get("errors", "0")) != 0 or \
                    int(suite.attrib.get("skipped", "0")) != 0:
                raise ValueError("JUnit suite reported failure, error or skipped test")
        for case in root.iter():
            if case.tag.split("}", 1)[-1] != "testcase":
                continue
            for element in case:
                if element.tag.split("}", 1)[-1] in {"failure", "error", "skipped"}:
                    raise ValueError("JUnit testcase failed, errored or was skipped")
            cls = case.attrib.get("classname", "")
            if not cls:
                cls = suites[0].attrib.get("name", "") if suites else ""
            cls = cls.rsplit(".", 1)[-1]
            name = case.attrib.get("name", "")
            if not cls or not name:
                raise ValueError("JUnit testcase missing class or method name")
            if (cls, name) in names:
                raise ValueError("Duplicate Android JUnit testcase result")
            names.add((cls, name))
            coverage.add(cls)
            executed += 1
    if executed < MIN_EXECUTED_TESTS:
        raise ValueError(f"Only {executed} Android instrumentation tests executed; "
                         f"expected at least {MIN_EXECUTED_TESTS}")
    missing = REQUIRED_CLASSES - coverage
    if missing:
        raise ValueError(f"Missing Android instrumentation test class(es): {', '.join(sorted(missing))}")
    print(f"PASS: {executed} real Android instrumentation tests executed; "
          "all 3 required suites present; 0 failures, errors or skips")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("results", type=Path)
    args = parser.parse_args()
    try:
        verify(args.results)
    except (ValueError, OSError, OverflowError) as ex:
        sys.exit(f"FAIL: {ex}")


if __name__ == "__main__":
    main()
