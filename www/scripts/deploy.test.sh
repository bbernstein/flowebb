#!/usr/bin/env bash
# Tests for scripts/deploy.sh using a stub `aws` on PATH (never touches AWS).
# Usage (from www/): bash scripts/deploy.test.sh
set -uo pipefail

SCRIPT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/deploy.sh"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

mkdir -p "$WORK/bin"
LOG="$WORK/aws.log"
cat >"$WORK/bin/aws" <<'STUB'
#!/usr/bin/env bash
echo "$*" >>"$AWS_STUB_LOG"
if [ "${AWS_STUB_FAIL_S3:-}" = 1 ] && [ "${1:-}" = s3 ]; then exit 1; fi
if [ "${2:-}" = create-invalidation ]; then echo I123; fi
exit 0
STUB
chmod +x "$WORK/bin/aws"

pass=0
fail=0
OUT=""
CODE=0

# run_script [VAR=value ...]: run deploy.sh with a clean env plus overrides.
run_script() {
  : >"$LOG"
  OUT="$(env -i PATH="$WORK/bin:$PATH" HOME="$WORK" AWS_STUB_LOG="$LOG" "$@" bash "$SCRIPT" 2>&1)"
  CODE=$?
}

check() { # name, condition-exit-status
  if [ "$2" -eq 0 ]; then echo "ok   - $1"; pass=$((pass + 1)); else echo "FAIL - $1"; fail=$((fail + 1)); fi
}

line_no() { grep -nxF -- "$1" "$LOG" | head -1 | cut -d: -f1; }

NOCACHE='public, max-age=0, must-revalidate'
DAY='public, max-age=86400'
HTML_SYNC="s3 sync site/ s3://B --delete --cache-control $NOCACHE --exclude * --include *.html --include *.txt --include *.xml"
ASSET_SYNC="s3 sync site/ s3://B --delete --cache-control $DAY --exclude *.html --exclude *.txt --exclude *.xml"
INVAL="cloudfront create-invalidation --distribution-id D --paths /* --query Invalidation.Id --output text"
WAIT="cloudfront wait invalidation-completed --distribution-id D --id I123"

run_script WWW_CLOUDFRONT_DISTRIBUTION_ID=D
[ $CODE -eq 0 ] && [[ "$OUT" == *skipping* ]] && [ ! -s "$LOG" ]
check "skips when bucket secret missing" $?

run_script WWW_BUCKET_NAME=B
[ $CODE -eq 0 ] && [[ "$OUT" == *skipping* ]] && [ ! -s "$LOG" ]
check "skips when distribution secret missing" $?

run_script WWW_BUCKET_NAME=B WWW_CLOUDFRONT_DISTRIBUTION_ID=D
[ $CODE -eq 0 ]
check "deploy succeeds" $?
grep -qxF -- "$HTML_SYNC" "$LOG"
check "syncs html/txt/xml with no-cache" $?
grep -qxF -- "$ASSET_SYNC" "$LOG"
check "syncs assets with one-day cache" $?
grep -qxF -- "$INVAL" "$LOG" && grep -qxF -- "$WAIT" "$LOG"
check "invalidates and waits" $?
a=$(line_no "$ASSET_SYNC"); h=$(line_no "$HTML_SYNC"); i=$(line_no "$INVAL"); w=$(line_no "$WAIT")
[ -n "$a" ] && [ -n "$h" ] && [ -n "$i" ] && [ -n "$w" ] && [ "$a" -lt "$h" ] && [ "$h" -lt "$i" ] && [ "$i" -lt "$w" ]
check "orders assets, html, invalidation, wait" $?
[[ "$OUT" != *B* || "$OUT" != *s3://* ]] && ! grep -qE '(^|[^A-Za-z])(D|B)([^A-Za-z]|$)' <<<"$OUT"
check "does not echo bucket or distribution id" $?

run_script WWW_BUCKET_NAME=B WWW_CLOUDFRONT_DISTRIBUTION_ID=D AWS_STUB_FAIL_S3=1
[ $CODE -ne 0 ]
check "fails when aws fails" $?

echo
echo "$pass passed, $fail failed"
[ "$fail" -eq 0 ]
