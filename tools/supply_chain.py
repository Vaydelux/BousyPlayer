#!/usr/bin/env python3
"""CycloneDX Maven inventory validation and OSV vulnerability gate.

Only resolved Maven modules are covered, NOT nested native C/C++ libraries.
The API receives Maven coordinates/versions only; never media URLs or secrets.
"""
import argparse
import json
import re
import sys
import time
from pathlib import Path
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen

OSV_URL = "https://api.osv.dev/v1/querybatch"
MAX_COMPONENTS = 3000
BATCH = 100


def packages_from_sbom(document):
    if document.get("bomFormat") != "CycloneDX" or document.get("specVersion") != "1.5":
        raise ValueError("Expected a CycloneDX 1.5 SBOM")
    items = document.get("components")
    if not isinstance(items, list) or not 1 <= len(items) <= MAX_COMPONENTS:
        raise ValueError("Dependency inventory is absent or unexpectedly large")
    seen = set()
    packages = []
    for item in items:
        if not isinstance(item, dict) or item.get("type") != "library":
            raise ValueError("Unexpected SBOM component")
        group, name, version = (item.get(key) for key in ("group", "name", "version"))
        if not all(isinstance(v, str) and re.fullmatch(r"[a-zA-Z0-9._-]{1,180}", v)
                   for v in (group, name, version)):
            raise ValueError("Malformed or dynamic Maven dependency coordinates")
        purl = f"pkg:maven/{group}/{name}@{version}"
        if item.get("purl") != purl or item.get("bom-ref") != purl:
            raise ValueError("Maven PURL mismatch")
        if purl in seen:
            raise ValueError("Duplicate resolved dependency")
        seen.add(purl)
        packages.append((f"{group}:{name}", version, purl))
    return sorted(packages)


def osv_request(queries):
    payload = json.dumps({"queries": queries}, separators=(",", ":")).encode("utf-8")
    for attempt in range(3):
        request = Request(OSV_URL, data=payload,
                          headers={"Content-Type": "application/json",
                                   "User-Agent": "BousyPlayer-CI/0.27"})
        try:
            with urlopen(request, timeout=40) as response:
                if response.status != 200:
                    raise RuntimeError(f"OSV returned HTTP {response.status}")
                body = response.read(10_000_001)
                if len(body) > 10_000_000:
                    raise RuntimeError("OSV response too large")
                return json.loads(body)
        except (HTTPError, URLError, TimeoutError, OSError) as error:
            if attempt == 2:
                raise RuntimeError(f"OSV service unreachable: {type(error).__name__}") from error
            time.sleep(2 ** attempt)
    raise RuntimeError("OSV scan retries exhausted")


def scan(packages, send=osv_request):
    findings = {}
    for offset in range(0, len(packages), BATCH):
        batch = packages[offset:offset + BATCH]
        pending = [(idx, {"package": {"name": name, "ecosystem": "Maven"},
                          "version": version})
                   for idx, (name, version, _) in enumerate(batch)]
        # The querybatch response order matches each request's order.
        pages = 0
        while pending:
            pages += 1
            if pages > 12:
                raise RuntimeError("OSV pagination limit exceeded; refusing incomplete scan")
            response = send([item[1] for item in pending])
            results = response.get("results") if isinstance(response, dict) else None
            if not isinstance(results, list) or len(results) != len(pending):
                raise RuntimeError("Incomplete OSV response; refusing to release")
            next_pending = []
            for (index, query), result in zip(pending, results):
                if not isinstance(result, dict):
                    raise RuntimeError("Malformed OSV result")
                ids = result.get("vulns", [])
                if not isinstance(ids, list):
                    raise RuntimeError("Malformed OSV advisory list")
                _, _, purl = batch[index]
                for advisory in ids:
                    if not isinstance(advisory, dict) or not isinstance(advisory.get("id"), str):
                        raise RuntimeError("Malformed OSV advisory")
                    findings.setdefault(purl, set()).add(advisory["id"])
                token = result.get("next_page_token")
                if token:
                    if not isinstance(token, str) or len(token) > 4096:
                        raise RuntimeError("Malformed OSV pagination token")
                    next_pending.append((index, dict(query, page_token=token)))
            pending = next_pending
    return [{"purl": key, "ids": sorted(values)} for key, values in sorted(findings.items())]


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--sbom", type=Path, required=True)
    parser.add_argument("--report", type=Path, required=True)
    parser.add_argument("--enforce", action="store_true",
                        help="Fail on any known OSV advisory or scan error")
    args = parser.parse_args()
    report = {"scanner": "OSV", "scope": "resolved Maven debugRuntimeClasspath only",
              "status": "error", "packages_checked": 0, "findings": []}
    exit_code = 0
    try:
        packages = packages_from_sbom(json.loads(args.sbom.read_text(encoding="utf-8")))
        report["packages_checked"] = len(packages)
        report["findings"] = scan(packages)
        report["status"] = "vulnerable" if report["findings"] else "clean"
        if report["findings"] and args.enforce:
            exit_code = 1
    except (ValueError, OSError, RuntimeError, json.JSONDecodeError) as error:
        report["error"] = str(error)
        exit_code = 2 if args.enforce else 0
    args.report.parent.mkdir(parents=True, exist_ok=True)
    args.report.write_text(json.dumps(report, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    print(f"OSV Maven scan: {report['status']}; {report['packages_checked']} packages; "
          f"{len(report['findings'])} affected packages")
    for finding in report["findings"][:15]:
        print(f"ADVISORY: {finding['purl']} {', '.join(finding['ids'])}")
    if "error" in report:
        print(f"OSV scan error: {report['error']}", file=sys.stderr)
    if exit_code:
        print("Security gate failed: no APK prerelease can be published.", file=sys.stderr)
    return exit_code


if __name__ == "__main__":
    raise SystemExit(main())
