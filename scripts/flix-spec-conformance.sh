#!/usr/bin/env bash
# Compare with the digest-pinned published Flix 0.79 data and runner artifacts.
# Optional argument: an extracted data bundle (pin, vocabulary and fixture revision still checked).
# FLIX_SPEC_DATA and FLIX_SPEC_RUNNER select local JARs; their SHA-256 must match the recorded artifacts.
set -euo pipefail
umask 022
REPO="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
exec python3 "$REPO/scripts/flix-spec-conformance.py" "$@"
