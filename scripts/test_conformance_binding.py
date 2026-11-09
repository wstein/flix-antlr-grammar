"""Artifact and report contract checks; no compiler or network required."""
import copy
import importlib.util
from pathlib import Path
import tempfile
import unittest
import zipfile

spec = importlib.util.spec_from_file_location("binding", Path(__file__).with_name("flix-spec-conformance.py"))
binding = importlib.util.module_from_spec(spec)
spec.loader.exec_module(binding)


class BindingTest(unittest.TestCase):
    def test_runner_bytes_must_match_exact_digest(self):
        with tempfile.TemporaryDirectory() as tmp:
            jar = Path(tmp) / "runner.jar"
            jar.write_bytes(b"official runner")
            digest = binding.digest(jar)
            binding.require_digest(jar, digest)
            jar.write_bytes(b"official runner" + b"x")
            with self.assertRaisesRegex(ValueError, "SHA-256"):
                binding.require_digest(jar, digest)

    def test_fixture_revision_and_schema_cannot_drift(self):
        baseline = {"measuredAt": {"reportSchemaVersion": 9, "fixtureRevision": "fixed", "fixtures": 166}}
        report = {"schemaVersion": 9, "provenance": {"fixtureRevision": "fixed"},
                  "lanes": {"oracle_conformance": {"fixturesExpected": 166, "fixturesCompared": 166, "fixturesMissing": []}}}
        binding.require_report(report, baseline)
        for path, value in [(('schemaVersion',), 8), (('provenance', 'fixtureRevision'), 'changed'),
                            (('lanes', 'oracle_conformance', 'fixturesCompared'), 165)]:
            changed = copy.deepcopy(report)
            target = changed
            for key in path[:-1]:
                target = target[key]
            target[path[-1]] = value
            with self.assertRaises(ValueError):
                binding.require_report(changed, baseline)

    def test_divergence_identity_includes_fixture_path_and_reason(self):
        divergence = {"fixture": "a.json", "path": "a.Root[0]", "expected": "A", "actual": "B", "reason": "kind"}
        report = {"lanes": {"oracle_conformance": {"divergences": [divergence]}}}
        recorded = binding.identities(report)
        self.assertEqual(recorded, binding.identities(report))
        for key in divergence:
            changed = copy.deepcopy(report)
            changed['lanes']['oracle_conformance']['divergences'][0][key] = 'other'
            self.assertNotEqual(recorded, binding.identities(changed), key)

    def test_depth_floors_are_independent(self):
        baseline = {"depthFloor": 88, "legacyWalkDepthFloor": 88,
                    "measuredAt": {"canonicalNodesExpected": 100}}
        for compared, unmapped, depth, reason in [(80, 1, 80, "canonical"), (88, 20, 88, "walk")]:
            report = {"lanes": {"oracle_conformance": {"nodesCompared": compared,
                      "nodesExpected": 100, "nodesUnmapped": unmapped, "depthPercent": depth}}}
            with self.assertRaisesRegex(ValueError, reason):
                binding.require_depth(report, baseline)

    def test_depth_rounding_matches_the_canonical_runner(self):
        self.assertEqual(88, binding.percent(7, 8))
        self.assertEqual(87, binding.percent(1749, 2000))
        self.assertEqual(1, binding.percent(1, 200))
        baseline = {"depthFloor": 88, "legacyWalkDepthFloor": 88,
                    "measuredAt": {"canonicalNodesExpected": 8}}
        report = {"lanes": {"oracle_conformance": {"nodesCompared": 7,
                  "nodesExpected": 8, "nodesUnmapped": 1, "depthPercent": 88}}}
        binding.require_depth(report, baseline)

    def test_canonical_denominator_cannot_be_shrunk(self):
        baseline = {"depthFloor": 88, "legacyWalkDepthFloor": 88,
                    "measuredAt": {"canonicalNodesExpected": 100}}
        report = {"lanes": {"oracle_conformance": {"nodesCompared": 88,
                  "nodesExpected": 88, "nodesUnmapped": 0, "depthPercent": 100}}}
        with self.assertRaisesRegex(ValueError, "denominator"):
            binding.require_depth(report, baseline)

    def test_extracted_bundle_binds_source_and_metadata_bytes(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary) / "data"
            root.mkdir()
            jar = Path(temporary) / "data.jar"
            files = {"pin.json": b"{}", "fixtures/positive/f.flix": b"def f(): Unit = ()",
                     "fixtures/raw/f.json": b"{}", "ast/transparency.json": b"{}"}
            with zipfile.ZipFile(jar, "w") as bundle:
                for name, content in files.items():
                    bundle.writestr(name, content)
                    path = root / name
                    path.parent.mkdir(parents=True, exist_ok=True)
                    path.write_bytes(content)
            binding.require_bundle(root, jar)
            for name, content in files.items():
                (root / name).write_bytes(content + b" ")
                with self.assertRaisesRegex(ValueError, "bundle bytes"):
                    binding.require_bundle(root, jar)
                (root / name).write_bytes(content)

    def test_extracted_bundle_rejects_extra_runtime_files(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary) / "data"
            root.mkdir()
            jar = Path(temporary) / "data.jar"
            with zipfile.ZipFile(jar, "w") as bundle:
                bundle.writestr("pin.json", b"{}")
            (root / "pin.json").write_bytes(b"{}")
            (root / "fixtures").mkdir()
            (root / "fixtures/extra.flix").write_text("def extra(): Unit = ()")
            with self.assertRaisesRegex(ValueError, "bundle paths"):
                binding.require_bundle(root, jar)


if __name__ == "__main__":
    unittest.main()
