#!/usr/bin/env bash
# Enforces repo hygiene rules. Run from the repo root:
#   bash scripts/check-repo-rules.sh
# Exits 1 and prints one "violation:" line per problem.
#
# Adding a platform? Add its directory to PLATFORMS (see docs/adding-a-platform.md).
set -uo pipefail

PLATFORMS=(backend web www infra firmware)
HEADING='## Never commit secrets or private data'

# Tracked paths matching any of these (ERE) are forbidden.
FORBIDDEN=(
  '(^|/)\.env[^/]*$'
  '\.tfvars$'
  '\.tfvars\.json$'
  '\.tfstate'
  '(^|/)tfplan$'
  'wifi_credentials\.h$'
  '\.pem$'
  '\.(key|p12)$'
  'id_rsa'
  'conversations.*\.json$'
)

if [ "$(git rev-parse --is-inside-work-tree 2>/dev/null)" != "true" ]; then
  echo "violation: not a git work tree; run from the repo root"
  exit 1
fi

violations=0
violation() {
  echo "violation: $*"
  violations=$((violations + 1))
}

has_heading() {
  grep -qxF -- "$HEADING" "$1"
}

# Root CLAUDE.md: secrets heading and a route to every platform.
if [ ! -f CLAUDE.md ]; then
  violation "missing CLAUDE.md"
else
  has_heading CLAUDE.md || violation "CLAUDE.md is missing heading '$HEADING'"
  for p in "${PLATFORMS[@]}"; do
    grep -qF -- "$p/" CLAUDE.md || violation "CLAUDE.md does not mention $p/"
  done
fi

# Each platform has a CLAUDE.md with the secrets heading.
for p in "${PLATFORMS[@]}"; do
  f="$p/CLAUDE.md"
  if [ ! -f "$f" ]; then
    violation "missing $f"
  elif ! has_heading "$f"; then
    violation "$f is missing heading '$HEADING'"
  fi
done

# No sensitive files tracked by git.
while IFS= read -r path; do
  [ -z "$path" ] && continue
  for pattern in "${FORBIDDEN[@]}"; do
    if grep -qE -- "$pattern" <<<"$path"; then
      # Any basename starting with .env is forbidden, except the template
      # forms .env*.example and .env*.template.
      if [ "$pattern" = '(^|/)\.env[^/]*$' ] && [[ "$path" == *.example || "$path" == *.template ]]; then
        continue
      fi
      violation "sensitive file is tracked: $path (matches $pattern)"
    fi
  done
# quotePath=false keeps non-ASCII paths unquoted so the patterns still match.
done < <(git -c core.quotePath=false ls-files)

if [ "$violations" -gt 0 ]; then
  echo "$violations repo rule violation(s)."
  exit 1
fi
echo "Repo rules OK."
