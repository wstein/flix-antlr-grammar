#!/usr/bin/env python3
"""Run the published comparator with immutable data and runner artifact bindings.

Python's standard library handles ZIP, SHA-256 and JSON without adding a package or
embedding a second conformance comparator in the JVM customer.
"""
import argparse
import hashlib
import json
import os
import shutil
import tempfile
from pathlib import Path
import subprocess
import sys
import urllib.request
import zipfile

REPO = Path(__file__).resolve().parent.parent
MAVEN = "https://wstein.github.io/flix-spec/maven/io/github/wstein"


def digest(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def require_digest(path, expected):
    actual = digest(path)
    if actual != expected:
        raise ValueError(f"SHA-256 mismatch for {path}: expected {expected}, got {actual}")


def require_bundle(root, archive):
    """Bind all runtime-consumed data bytes, including source text outside fixtureRevision."""
    prefixes = ("ast/", "fixtures/", "profiles/", "schemas/", "corpus/", "defects/")
    def runtime(name):
        return name == "pin.json" or name.startswith(prefixes)
    with zipfile.ZipFile(archive) as bundle:
        expected = {entry.filename: bundle.read(entry) for entry in bundle.infolist()
                    if not entry.is_dir() and runtime(entry.filename)}
    actual = {file.relative_to(root).as_posix() for file in root.rglob("*")
              if file.is_file() and runtime(file.relative_to(root).as_posix())}
    if actual != set(expected):
        raise ValueError("extracted bundle paths differ from digest-pinned data")
    for name, content in expected.items():
        if (root / name).read_bytes() != content:
            raise ValueError(f"extracted bundle bytes differ from digest-pinned data: {name}")


def require_report(report, baseline):
    measured = baseline["measuredAt"]
    if report.get("schemaVersion") != measured["reportSchemaVersion"]:
        raise ValueError("report schema differs from recorded metric definition")
    if report.get("provenance", {}).get("fixtureRevision") != measured["fixtureRevision"]:
        raise ValueError("fixtureRevision differs from the recorded immutable fixture set")
    lane = report.get("lanes", {}).get("oracle_conformance", {})
    if (lane.get("fixturesExpected") != measured["fixtures"] or
            lane.get("fixturesCompared") != measured["fixtures"] or lane.get("fixturesMissing")):
        raise ValueError("comparison did not cover the complete recorded fixture set")


def percent(numerator, denominator):
    if denominator <= 0 or numerator < 0:
        raise ValueError("invalid depth denominator/numerator")
    # java.lang.Math.round for nonnegative rational percentages, without floating-point drift.
    return (200 * numerator + denominator) // (2 * denominator)


def require_depth(report, baseline):
    lane = report["lanes"]["oracle_conformance"]
    compared, expected, unmapped = (lane[key] for key in ("nodesCompared", "nodesExpected", "nodesUnmapped"))
    if expected != baseline["measuredAt"]["canonicalNodesExpected"]:
        raise ValueError("canonical depth denominator differs from the bound reference inventory")
    if compared > expected or unmapped < 0:
        raise ValueError("invalid depth node counts")
    canonical = percent(compared, expected)
    if canonical != lane["depthPercent"] or canonical < baseline["depthFloor"]:
        raise ValueError(f"canonical depth {canonical}% is below its floor or inconsistent")
    walk = percent(compared, compared + unmapped)
    if walk < baseline["legacyWalkDepthFloor"]:
        raise ValueError(f"legacy walk depth {walk}% is below the new floor preserving its historical measured value")
    return canonical, walk


def subset_inputs(source, actual, destination, paths):
    """Select fixed identities; the published runner still owns every comparison and metric."""
    spec, projected = destination / "spec", destination / "actual"
    spec.mkdir(parents=True)
    projected.mkdir()
    shutil.copy2(source / "pin.json", spec / "pin.json")
    for directory in ("ast", "profiles", "schemas", "defects", "corpus"):
        if (source / directory).is_dir():
            shutil.copytree(source / directory, spec / directory)
    for relative in paths:
        path = Path(relative)
        target = spec / path
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(source / path, target)
        for form in ("expected", "raw"):
            fixture = Path("fixtures") / form / f"{path.stem}.json"
            (spec / fixture).parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(source / fixture, spec / fixture)
        shutil.copy2(actual / f"{path.stem}.json", projected / f"{path.stem}.json")
    return spec, projected


def identities(report):
    fields = ("fixture", "path", "expected", "actual", "reason")
    result = []
    for lane, data in sorted(report.get("lanes", {}).items()):
        for divergence in data.get("divergences", []):
            result.append({"lane": lane, **{key: divergence[key] for key in fields}})
    return sorted(result, key=lambda item: tuple(item.values()))


def artifact(name, expected, configured, version):
    path = Path(configured).resolve() if configured else REPO / "build" / "flix-spec-artifacts" / f"{name}-{version}.jar"
    if not path.exists():
        if configured:
            raise ValueError(f"artifact does not exist: {path}")
        path.parent.mkdir(parents=True, exist_ok=True)
        url = f"{MAVEN}/{name}/0.79.0-SNAPSHOT/{name}-{version}.jar"
        with urllib.request.urlopen(url) as response:
            data = response.read()
        if hashlib.sha256(data).hexdigest() != expected:
            raise ValueError(f"SHA-256 mismatch downloading {url}")
        temporary = path.with_suffix(".download")
        temporary.write_bytes(data)
        temporary.replace(path)
    require_digest(path, expected)
    return path


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("spec", nargs="?", help="optional extracted data bundle; still checked against all recorded inputs")
    args = parser.parse_args(argv)
    baseline = json.loads((REPO / "conformance/baseline.json").read_text())
    measured = baseline["measuredAt"]
    runner = artifact("flix-spec-runner", measured["runnerSha256"], os.getenv("FLIX_SPEC_RUNNER"), measured["flixSpecArtifact"])
    data = artifact("flix-spec", measured["dataSha256"], os.getenv("FLIX_SPEC_DATA"), measured["flixSpecArtifact"])
    supplied = args.spec or os.getenv("FLIX_SPEC")
    if supplied:
        source = Path(supplied).resolve()
    else:
        source = REPO / "build" / "flix-spec-data" / measured["dataSha256"]
        source.mkdir(parents=True, exist_ok=True)
        with zipfile.ZipFile(data) as bundle:
            for entry in bundle.infolist():
                if not (source / entry.filename).resolve().is_relative_to(source.resolve()):
                    raise ValueError("unsafe data bundle path")
            bundle.extractall(source)
    require_bundle(source, data)
    pin = json.loads((source / "pin.json").read_text())
    for key, actual in (("flixSpecPin", pin["upstream"]["tag"]),
                        ("flixSpecPinCommit", pin["upstream"]["commit"]),
                        ("treeKindDigest", pin["treeKindDigest"]),
                        ("tokenKindDigest", pin["tokenKindDigest"])):
        if measured[key] != actual:
            raise ValueError(f"{key} differs from baseline: {actual}")
    actual = REPO / "build/flix-spec-projection"
    actual.mkdir(parents=True, exist_ok=True)
    # Remove only this adapter's prior JSON outputs, so dropped fixtures cannot persist as stale successes.
    for previous in actual.glob("*.json"):
        previous.unlink()
    environment = dict(os.environ, FLIX_SPEC=str(source))
    subprocess.run([str(REPO / "gradlew"), "-p", str(REPO), "-q", ":antlr4:projectFixtures"], env=environment, check=True)
    report_path = REPO / "build/flix-spec-report.json"
    report_path.unlink(missing_ok=True)
    command = ["java", "-jar", str(runner), "--spec-root", str(source), "--source-root", str(source),
               "--actual", str(actual), "--map", str(REPO / "conformance/projection-map.json"),
               "--report", str(report_path), "--baseline", str(baseline["divergences"]),
               "--recovery-baseline", str(baseline["recoveryDivergences"]),
               "--depth-floor", str(baseline["depthFloor"])]
    result = subprocess.run(command, check=False)
    if result.returncode not in (0, 1) or not report_path.exists():
        raise ValueError(f"runner input/execution failure: exit {result.returncode}")
    report = json.loads(report_path.read_text())
    require_report(report, baseline)
    canonical, walk = require_depth(report, baseline)
    print(f"depth: canonical {canonical}%, legacy walk {walk}% (floor {baseline['legacyWalkDepthFloor']}%)")
    remaining = json.loads((REPO / "conformance/remaining-divergences.json").read_text())
    if identities(report) != remaining:
        raise ValueError("remaining divergence identities changed; inspect fixture/path/reason before re-recording")
    manifest = json.loads((REPO / "conformance/legacy-fixture-paths.json").read_text())
    with tempfile.TemporaryDirectory(prefix="legacy-fixtures-", dir=REPO / "build") as temporary:
        selected, selected_actual = subset_inputs(source, actual, Path(temporary), manifest["common"])
        legacy_report = REPO / "build/flix-spec-legacy-report.json"
        legacy = baseline["legacyFixtureSet"]
        legacy_command = command.copy()
        for flag, value in (("--spec-root", selected), ("--source-root", selected),
                            ("--actual", selected_actual), ("--report", legacy_report),
                            ("--baseline", legacy["divergences"]), ("--depth-floor", legacy["depthFloor"])):
            legacy_command[legacy_command.index(flag) + 1] = str(value)
        legacy_report.unlink(missing_ok=True)
        legacy_result = subprocess.run(legacy_command, check=False)
        if legacy_result.returncode not in (0, 1) or not legacy_report.exists():
            raise ValueError("legacy fixture comparison failed to produce a report")
        selected_report = json.loads(legacy_report.read_text())
        require_report(selected_report, legacy)
        selected_canonical, selected_walk = require_depth(selected_report, legacy)
        print(f"legacy common fixtures: canonical {selected_canonical}%, legacy walk {selected_walk}%")
        if legacy_result.returncode:
            return legacy_result.returncode
    subprocess.run(["java", "-jar", str(runner), "render", "--report", str(report_path),
                    "--html", str(REPO / "build/flix-spec-report.html")], check=True)
    print(f"report: {report_path}")
    return result.returncode


if __name__ == "__main__":
    try:
        sys.exit(main())
    except (ValueError, OSError, subprocess.CalledProcessError, zipfile.BadZipFile) as error:
        print(f"error: {error}", file=sys.stderr)
        sys.exit(2)
