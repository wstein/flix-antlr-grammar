#!/usr/bin/env bash
# Measure this grammar against wstein/flix-spec's fixtures and report all three conformance lanes.
#
# flix-spec owns the canonical TreeKind vocabulary, the fixtures, the expected trees and the
# comparison algorithm. This repository owns the grammar, `conformance/projection-map.json` which
# maps our rule names onto that vocabulary, and the projection that turns our parse trees into the
# shape the comparison reads.
#
# The comparison is invoked, never reimplemented. flix-jetbrains-plugin ported it into Kotlin and
# within one release the port had drifted from the original in two ways that both changed its
# results. "The comparison lives in one place so four repositories do not re-derive it four times"
# is flix-spec's reason to exist, so this shells out exactly as tree-sitter-flix does.
#
# Usage:
#   scripts/flix-spec-conformance.sh [flix-spec-dir]
#   FLIX_SPEC=~/src/flix-spec scripts/flix-spec-conformance.sh
#
# Exits non-zero when either derived lane exceeds its ratchet in conformance/baseline.json, or when
# the source-invariants lane fails.
set -euo pipefail

REPO="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SPEC="${1:-${FLIX_SPEC:-}}"

if [ -z "$SPEC" ]; then
  echo "This needs a checkout of flix-spec, which owns the fixtures and the comparison." >&2
  echo "  scripts/flix-spec-conformance.sh <path>      or      FLIX_SPEC=<path>" >&2
  echo "Clone it from https://github.com/wstein/flix-spec" >&2
  exit 2
fi
if [ ! -d "$SPEC" ]; then
  echo "Not a directory: $SPEC" >&2
  exit 2
fi
SPEC="$(cd "$SPEC" && pwd)"

# The pin lock, held from this side. Nothing measured against the shared fixtures is comparable
# while the two repositories describe different compilers, and no naming convention detects that.
EXPECTED_PIN="$(jq -r '.measuredAt.flixSpecPinCommit' "$REPO/conformance/baseline.json")"
ACTUAL_PIN="$(jq -r '.upstream.commit' "$SPEC/pin.json")"
if [ "$EXPECTED_PIN" != "$ACTUAL_PIN" ]; then
  echo "This baseline was measured against Flix $EXPECTED_PIN, but $SPEC pins $ACTUAL_PIN." >&2
  echo "A different pin is a different question, not a regression: re-measure and record it." >&2
  exit 1
fi

# ...and the same for the fixture set. The pin says which compiler; the fixture revision says which
# expectations, and the two move independently: fixtures are regenerated whenever a fixture source
# changes, with the pin standing still. A baseline compared against a different fixture revision is
# answering a different question, which is exactly what the recorded value is for -- it was recorded
# and never read.
EXPECTED_REV="$(jq -r '.measuredAt.fixtureRevision' "$REPO/conformance/baseline.json")"

OUT="$REPO/build/flix-spec-projection"
MAP="$REPO/conformance/projection-map.json"
REPORT="$REPO/build/flix-spec-report.json"

echo "== projecting flix-spec fixtures with this grammar =="
FLIX_SPEC="$SPEC" "$REPO/gradlew" -p "$REPO" -q :antlr4:projectFixtures

echo ""
echo "== validating the projection map against the canonical vocabulary =="
"$SPEC/gradlew" -p "$SPEC" -q :tools:project:validateProjectionMap --args="$MAP"

BASELINE="$(jq -r '.divergences' "$REPO/conformance/baseline.json")"
RECOVERY_BASELINE="$(jq -r '.recoveryDivergences // 0' "$REPO/conformance/baseline.json")"

echo ""
echo "== comparing against flix-spec =="
if ! "$SPEC/gradlew" -p "$SPEC" -q :tools:project:conformance \
  --args="--actual $OUT --map $MAP --report $REPORT --baseline $BASELINE --recovery-baseline $RECOVERY_BASELINE"; then
  echo "" >&2
  echo "error: conformance regressed against conformance/baseline.json" >&2
  echo "  baselines allow $BASELINE structural and $RECOVERY_BASELINE recovery divergences; see $REPORT" >&2
  exit 1
fi

ACTUAL_REV="$(jq -r '.provenance.fixtureRevision' "$REPORT")"
if [ "$EXPECTED_REV" != "$ACTUAL_REV" ]; then
  echo "" >&2
  echo "FATAL: this baseline was measured against fixture revision $EXPECTED_REV," >&2
  echo "       but flix-spec produced $ACTUAL_REV." >&2
  echo "A different fixture revision is a different question, not a regression: re-measure and" >&2
  echo "record it, rather than reading the ratchet as if it still applied." >&2
  exit 1
fi

echo ""
echo "report: $REPORT"
