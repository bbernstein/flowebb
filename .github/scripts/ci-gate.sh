#!/usr/bin/env bash
# ci-gate decision. Reads the JSON of toJSON(needs) on stdin.
# Exits 1 if any needed job's result is "failure" or "cancelled".
# "success" and "skipped" both count as pass.
set -euo pipefail

bad="$(jq -r 'to_entries[] | select(.value.result == "failure" or .value.result == "cancelled") | "\(.key)=\(.value.result)"')"

if [ -n "$bad" ]; then
  echo "ci-gate FAILED. Non-passing jobs:" >&2
  echo "$bad" >&2
  exit 1
fi
echo "ci-gate passed"
