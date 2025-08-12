#!/usr/bin/env bash
# Orchestrate available tests: Prolog and Haskell
set -euo pipefail
ROOT=$(cd "$(dirname "$0")/.." && pwd)
cd "$ROOT"

PROLOG_OK=0
HASKELL_OK=0

if command -v swipl >/dev/null 2>&1; then
  echo "Running Prolog tests..."
  swipl -q -s prolog/test_constraints.pl || PROLOG_OK=1
else
  echo "[SKIP] Prolog (swipl) not found"
fi

if [ -x tests/test_haskell.sh ]; then
  echo "Running Haskell tests..."
  bash tests/test_haskell.sh || HASKELL_OK=1
else
  echo "[WARN] tests/test_haskell.sh not executable; setting +x"
  chmod +x tests/test_haskell.sh
  bash tests/test_haskell.sh || HASKELL_OK=1
fi

if [ $PROLOG_OK -ne 0 ] || [ $HASKELL_OK -ne 0 ]; then
  echo "Some tests failed." >&2
  exit 1
fi

echo "All available tests passed."