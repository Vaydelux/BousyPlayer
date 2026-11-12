#!/usr/bin/env python3
"""Offline negative fixtures for Android instrumentation result verification."""
from importlib import util
from pathlib import Path
from tempfile import TemporaryDirectory

source = Path(__file__).with_name("verify_instrumentation_results.py")
spec = util.spec_from_file_location("verify_instrumentation_results", source)
module = util.module_from_spec(spec)
spec.loader.exec_module(module)


def suite(classname, tests, status=None):
    children = "".join(
        f'<testcase classname="com.vaydelux.bousyplayer.{classname}" name="case{i}">'
        f'{status if i == 0 and status else ""}</testcase>'
        for i in range(tests)
    )
    return f'<testsuite name="{classname}" tests="{tests}">{children}</testsuite>'


def fails(directory):
    try:
        module.verify(directory)
    except ValueError:
        return True
    return False


with TemporaryDirectory() as temp:
    folder = Path(temp)
    classes = [
        ("MainActivityLifecycleTest", 1),
        ("PlayerSessionLifecycleInstrumentedTest", 5),
        ("RecentStorePrivacyInstrumentedTest", 1),
    ]
    for cls, tests in classes:
        (folder / f"TEST-{cls}.xml").write_text(suite(cls, tests), encoding="utf-8")
    module.verify(folder)
    assert not fails(folder), "Valid 7-case XML was incorrectly rejected"

    file = folder / "TEST-MainActivityLifecycleTest.xml"
    file.unlink()
    assert fails(folder), "Missing ActivityScenario test suite was accepted"
    file.write_text(suite(classes[0][0], 1), encoding="utf-8")
    (folder / "TEST-RecentStorePrivacyInstrumentedTest.xml").unlink()
    assert fails(folder), "Missing history privacy suite was accepted"
    (folder / "TEST-RecentStorePrivacyInstrumentedTest.xml").write_text(
        suite(classes[2][0], 1), encoding="utf-8")

    file.write_text(suite(classes[0][0], 1, "<failure>synthetic error</failure>"),
                    encoding="utf-8")
    assert fails(folder), "Failed JUnit case was accepted"
    file.write_text(suite(classes[0][0], 1, "<skipped/>"), encoding="utf-8")
    assert fails(folder), "Skipped JUnit case was accepted"
    file.write_text(suite(classes[0][0], 1), encoding="utf-8")
    file.write_text("<testsuite><testcase>", encoding="utf-8")
    assert fails(folder), "Malformed XML was accepted"
    file.write_text(suite(classes[0][0], 1), encoding="utf-8")
    duplicate = folder / "TEST-copy.xml"
    duplicate.write_text(suite(classes[0][0], 1), encoding="utf-8")
    assert fails(folder), "Duplicated JUnit test results were accepted"
    duplicate.unlink()
    for f in folder.glob("*.xml"):
        f.unlink()
    assert fails(folder), "Zero-test suite was accepted"

print("PASS: instrumentation JUnit parser rejects missing, skipped, failing, malformed, duplicate and empty test runs")
