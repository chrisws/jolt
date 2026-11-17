#!/usr/bin/env bash
# Syntax-check all frontend JS modules with node --check
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
JS_DIR="$SCRIPT_DIR/../src/main/resources/static/modules"

if [[ ! -d "$JS_DIR" ]]; then
  echo "ERROR: JS directory not found: $JS_DIR" >&2
  exit 1
fi

fail=0
for f in "$JS_DIR"/*.js; do
  if ! node --check "$f" 2>&1; then
    echo "FAIL: $f" >&2
    fail=1
  fi
done

if [[ $fail -ne 0 ]]; then
  echo "One or more JS files failed syntax check." >&2
  exit 1
fi

echo "All JS files passed syntax check."