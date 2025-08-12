#!/usr/bin/env bash
# Simple test for haskell/GeneticSchedule.hs using sample-request.json
set -euo pipefail

if ! command -v runhaskell >/dev/null 2>&1; then
  echo "[SKIP] runhaskell not found. Install GHC to run Haskell tests." >&2
  exit 0
fi

DIR=$(cd "$(dirname "$0")/.." && pwd)
cd "$DIR"

OUT=$(runhaskell haskell/GeneticSchedule.hs < sample-request.json)

echo "Haskell output: $OUT"

# Very basic checks without jq
case "$OUT" in
  *"bestSchedule"* ) echo "[OK] bestSchedule present";;
  *) echo "[FAIL] bestSchedule missing"; exit 1;;
 esac

case "$OUT" in
  *"fitness"* ) echo "[OK] fitness present";;
  *) echo "[FAIL] fitness missing"; exit 1;;
 esac

echo "Haskell test passed"