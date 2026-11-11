#!/usr/bin/env python3
"""Offline supply-chain tests: no network required."""
import importlib.util
import json
from pathlib import Path

spec = importlib.util.spec_from_file_location("supply_chain",
    Path(__file__).resolve().parent / "supply_chain.py")
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


def assert_raises(callback, error=ValueError):
    try:
        callback()
    except error:
        return
    raise AssertionError(f"Expected {error.__name__}")


def fixture(components):
    return {"bomFormat": "CycloneDX", "specVersion": "1.5", "components": components}


def component(group, name, version):
    purl = f"pkg:maven/{group}/{name}@{version}"
    return {"type": "library", "group": group, "name": name,
            "version": version, "purl": purl, "bom-ref": purl}


def test():
    jackson = component("com.fasterxml.jackson.core", "jackson-databind", "2.17.1")
    gson = component("com.google.code.gson", "gson", "2.10.1")
    resolved = module.packages_from_sbom(fixture([gson, jackson]))
    assert resolved == [
        ("com.fasterxml.jackson.core:jackson-databind", "2.17.1", jackson["purl"]),
        ("com.google.code.gson:gson", "2.10.1", gson["purl"])
    ]
    assert_raises(lambda: module.packages_from_sbom(fixture([])))
    assert_raises(lambda: module.packages_from_sbom(fixture([jackson, jackson])))
    assert_raises(lambda: module.packages_from_sbom(fixture([component("a", "b", "1.+")])))
    assert_raises(lambda: module.packages_from_sbom(fixture([dict(jackson, purl="pkg:maven/evil/x@0")])))
    assert_raises(lambda: module.packages_from_sbom({"bomFormat": "Invalid"}))

    def clean(queries):
        assert queries and queries[0]["package"]["ecosystem"] == "Maven"
        return {"results": [{} for _ in queries]}
    assert module.scan(resolved, clean) == []

    def vulnerable(queries):
        return {"results": [{"vulns": [{"id": "GHSA-test-1111"}]}, {}]}
    results = module.scan(resolved, vulnerable)
    assert results == [{"purl": jackson["purl"], "ids": ["GHSA-test-1111"]}]

    calls = []
    def paged(queries):
        calls.append(queries)
        if len(calls) == 1:
            return {"results": [{"vulns": [{"id": "GHSA-a"}],
                                 "next_page_token": "page-two"}, {}]}
        assert len(queries) == 1 and queries[0]["page_token"] == "page-two"
        return {"results": [{"vulns": [{"id": "GHSA-b"}]}]}
    assert module.scan(resolved, paged) == [
        {"purl": jackson["purl"], "ids": ["GHSA-a", "GHSA-b"]}
    ]
    assert_raises(lambda: module.scan(resolved, lambda _: {"results": []}), RuntimeError)
    assert_raises(lambda: module.scan(resolved, lambda _: {"results": [{"vulns": "bad"}, {}]}), RuntimeError)
    print("PASS: CycloneDX schema, Maven coordinates, OSV advisories, pagination, and fail-closed checks")


if __name__ == "__main__":
    test()
