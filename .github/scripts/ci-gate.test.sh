#!/usr/bin/env bash
# Tests for ci-gate.sh. Usage: bash .github/scripts/ci-gate.test.sh
set -u
dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
gate="$dir/ci-gate.sh"
pass=0
fail=0

check() {
  local name="$1" expected="$2" json="$3" actual
  printf '%s' "$json" | bash "$gate" >/dev/null 2>&1
  actual=$?
  if [ "$actual" -eq "$expected" ]; then
    pass=$((pass + 1))
    echo "ok   - $name"
  else
    fail=$((fail + 1))
    echo "FAIL - $name (expected exit $expected, got $actual)"
  fi
}

check "all success" 0 '{"a":{"result":"success"},"b":{"result":"success"}}'
check "success and skipped" 0 '{"a":{"result":"success"},"b":{"result":"skipped"}}'
check "one failure" 1 '{"a":{"result":"success"},"b":{"result":"failure"}}'
check "one cancelled" 1 '{"a":{"result":"success"},"b":{"result":"cancelled"}}'

echo "$pass passed, $fail failed"
[ "$fail" -eq 0 ]
