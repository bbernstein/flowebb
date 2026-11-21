#!/usr/bin/env bash
# Tests for scripts/check-repo-rules.sh.
# Builds throwaway git repos in a temp dir and checks that the rules script
# passes on a compliant tree and fails, naming the problem, on each violation.
# Usage (from the repo root): bash scripts/check-repo-rules.test.sh
set -uo pipefail

SCRIPT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/check-repo-rules.sh"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

PLATFORMS=(backend web infra firmware)
HEADING='## Never commit secrets or private data'
pass=0
fail=0

# Create a compliant repo at $1.
make_repo() {
  local dir="$1"
  mkdir -p "$dir"
  git -C "$dir" init -q
  {
    echo "# Root rules"
    echo
    echo "$HEADING"
    echo
    echo "Routing: backend/ web/ infra/ firmware/"
  } >"$dir/CLAUDE.md"
  for p in "${PLATFORMS[@]}"; do
    mkdir -p "$dir/$p"
    printf '# %s\n\n%s\n' "$p" "$HEADING" >"$dir/$p/CLAUDE.md"
  done
  # Allowed templates.
  mkdir -p "$dir/infra/terraform/environments/prod" "$dir/firmware/src/config" "$dir/web"
  echo 'project_name = "example"' >"$dir/infra/terraform/environments/prod/terraform.tfvars.example"
  echo '// template' >"$dir/firmware/src/config/wifi_credentials.h.template"
  echo 'NEXT_PUBLIC_API_URL=http://localhost:8080' >"$dir/web/.env.example"
  echo 'NEXT_PUBLIC_API_URL=http://localhost:8080' >"$dir/web/.env.local.example"
  echo 'X=placeholder' >"$dir/.env.template"
  git -C "$dir" add -A
}

# Run the script in $1; sets $out and $code.
run_check() {
  out="$(cd "$1" && bash "$SCRIPT" 2>&1)"
  code=$?
}

ok() { echo "PASS: $1"; pass=$((pass + 1)); }
ko() { echo "FAIL: $1"; printf '    %s\n' "$out"; fail=$((fail + 1)); }

# Expect the script to fail in $2 with output containing $3.
expect_fail() {
  local name="$1" dir="$2" needle="$3"
  run_check "$dir"
  if [ "$code" -ne 0 ] && grep -qF -- "$needle" <<<"$out"; then
    ok "$name"
  else
    ko "$name (exit $code, expected non-zero and output containing '$needle')"
  fi
}

# 1. Compliant tree passes.
make_repo "$WORK/ok"
run_check "$WORK/ok"
if [ "$code" -eq 0 ]; then ok "compliant tree passes"; else ko "compliant tree passes (exit $code)"; fi

# 2. Missing platform CLAUDE.md.
make_repo "$WORK/missing"
git -C "$WORK/missing" rm -q --cached firmware/CLAUDE.md
rm "$WORK/missing/firmware/CLAUDE.md"
expect_fail "missing platform CLAUDE.md" "$WORK/missing" "missing firmware/CLAUDE.md"

# 3. Missing secrets heading in a platform CLAUDE.md.
make_repo "$WORK/heading"
echo "# web" >"$WORK/heading/web/CLAUDE.md"
expect_fail "missing secrets heading" "$WORK/heading" "web/CLAUDE.md is missing heading"

# 3b. Missing secrets heading in the root CLAUDE.md.
make_repo "$WORK/rootheading"
echo "backend/ web/ infra/ firmware/" >"$WORK/rootheading/CLAUDE.md"
expect_fail "missing root secrets heading" "$WORK/rootheading" "violation: CLAUDE.md is missing heading"

# 3c. Root CLAUDE.md does not mention a platform.
make_repo "$WORK/route"
printf '%s\nbackend/ web/ infra/\n' "$HEADING" >"$WORK/route/CLAUDE.md"
expect_fail "root CLAUDE.md missing platform" "$WORK/route" "does not mention firmware/"

# 4. Tracked web/.env.local.
make_repo "$WORK/env"
echo 'X=1' >"$WORK/env/web/.env.local"
git -C "$WORK/env" add -f web/.env.local
expect_fail "tracked web/.env.local" "$WORK/env" "web/.env.local"

# 5. Tracked terraform.tfvars.
make_repo "$WORK/tfvars"
echo 'x = 1' >"$WORK/tfvars/infra/terraform/environments/prod/terraform.tfvars"
git -C "$WORK/tfvars" add -f infra/terraform/environments/prod/terraform.tfvars
expect_fail "tracked terraform.tfvars" "$WORK/tfvars" "infra/terraform/environments/prod/terraform.tfvars"

# 6. Tracked wifi_credentials.h.
make_repo "$WORK/wifi"
echo '// creds' >"$WORK/wifi/firmware/src/config/wifi_credentials.h"
git -C "$WORK/wifi" add -f firmware/src/config/wifi_credentials.h
expect_fail "tracked wifi_credentials.h" "$WORK/wifi" "firmware/src/config/wifi_credentials.h"

# 7. Tracked backend/.env.
make_repo "$WORK/dotenv"
echo 'X=1' >"$WORK/dotenv/backend/.env"
git -C "$WORK/dotenv" add -f backend/.env
expect_fail "tracked backend/.env" "$WORK/dotenv" "backend/.env"

# 7b. Tracked backend/.envrc and backend/.env-prod.
for name in .envrc .env-prod; do
  make_repo "$WORK/dotenv$name"
  echo 'X=1' >"$WORK/dotenv$name/backend/$name"
  git -C "$WORK/dotenv$name" add -f "backend/$name"
  expect_fail "tracked backend/$name" "$WORK/dotenv$name" "backend/$name"
done

# 8. Tracked Terraform plan file.
make_repo "$WORK/tfplan"
echo 'plan' >"$WORK/tfplan/infra/terraform/environments/prod/tfplan"
git -C "$WORK/tfplan" add -f infra/terraform/environments/prod/tfplan
expect_fail "tracked tfplan" "$WORK/tfplan" "infra/terraform/environments/prod/tfplan"

# 9. Non-ASCII path is reported unquoted (not missed).
make_repo "$WORK/unicode"
mkdir -p "$WORK/unicode/web/café"
echo 'X=1' >"$WORK/unicode/web/café/.env.local"
git -C "$WORK/unicode" add -f "web/café/.env.local"
expect_fail "tracked non-ASCII .env path" "$WORK/unicode" "web/café/.env.local"

# 10. Not a git work tree.
mkdir -p "$WORK/notrepo"
expect_fail "outside a git work tree" "$WORK/notrepo" "not a git work tree"

echo
echo "$pass passed, $fail failed"
[ "$fail" -eq 0 ]
