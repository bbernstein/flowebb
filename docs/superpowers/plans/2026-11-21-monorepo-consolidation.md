# FlowEbb Monorepo Consolidation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Merge flowebb-go, flowebb-web, flowebb-infra and flowebb-esp, with full history, into one new public repo `github.com/bbernstein/flowebb`. Add rules files, CI and docs so an AI partner can route and parallelize work by platform. Then retire the old repos safely.

**Architecture:** Each old repo is freshly cloned, rewritten with `git filter-repo --to-subdirectory-filter <dir>`, and merged (`--allow-unrelated-histories`) onto a branch of the new repo. Mechanical fix-ups follow as separate commits: CI moves to the root, the Go module is renamed, and docs and rules are added. The import PR is merged with a merge commit, and the repo then returns to squash-only. Deploys stay manual until production is verified.

**Tech Stack:** git + git-filter-repo, gh CLI, AWS CLI, GitHub Actions (dorny/paths-filter, golangci-lint, gitleaks), Go 1.23 + gqlgen v0.17.64, Next.js 15 / Jest, Terraform 1.5.0 + tflint 0.44.1, PlatformIO.

**Spec:** `docs/superpowers/specs/2026-11-21-monorepo-consolidation-design.md` (read it first; section numbers below refer to it).

## Global Constraints

- New repo: `bbernstein/flowebb`, **public**. Directories: `backend/` (flowebb-go), `web/` (flowebb-web), `infra/` (flowebb-infra), `firmware/` (flowebb-esp). Do not create `apps/` yet.
- Go module path becomes exactly `github.com/bbernstein/flowebb/backend`.
- **Never commit secrets or private data:** tokens, passwords, keys, AWS credentials, Wi-Fi credentials, `.env*` (except `*.example`), `terraform.tfvars`, Terraform state, raw claude.ai export content, or customer/personal info (names, emails, phones, addresses). Never print secret values to the terminal, logs, files, or chat.
- Never commit to `main`. All work goes on branch `monorepo-import` (Tasks 2–7), then on short follow-up branches.
- The import PR is merged **only** with "Create a merge commit". After that, the repo is squash-merge only.
- No application behavior changes. Version pins and config changes are allowed only where needed to get CI green, and each one is listed in the PR body.
- Deploy workflows are `workflow_dispatch` only until Task 9 re-enables push triggers.
- Never run `terraform apply` outside the dispatched deploy workflow, and never without the owner's go-ahead. `terraform plan` is safe.
- Outward-facing or irreversible actions (deleting the IAM key, setting secrets, archiving repos, dispatching deploys, merging) need explicit owner confirmation at the time. The plan's approval does not cover them.
- Coverage: backend CI keeps its ≥80% gate. Web CI does not enforce coverage. Document this truthfully.
- Working area: `~/src/flowebb-migration/` (clones, scan reports, tools; never committed). The new repo is assembled at `~/src/flowebb-new/` and moved to `~/src/flowebb/` in Task 10.
- Claude export: `~/Documents/Claude/Export`. Use only `conversations.json` and `projects/*.json`; the ` 2` copies are duplicates.

## Review Focus

1. **Import PR squash-merged by mistake** → history lost. Task 6 sets the repo to merge-commit only before merge. After merge, `git log --oneline origin/main -- backend/go.mod` must list more than one commit (Task 6, Step 7).
2. **Gitignored local config gets committed** (`.env.local`, `terraform.tfvars`, `wifi_credentials.h`) during cutover or a later PR. `scripts/check-repo-rules.sh` fails CI on tracked forbidden files (Task 5), and Task 10 checks `git status --ignored`.
3. **Module rename misses the `@goModel` directive** in `schema.graphql`, so gqlgen regenerates against the old path. Backend CI deletes the generated code and regenerates it from clean before building (Task 3 and Task 4).
4. **Path-filtered jobs skip, and the required check can't tell "skipped" from "failed"**. `ci-gate` passes when every job is success or skipped, and fails on any failure or cancel (Task 4, Step 4 test).
5. **Deploy scripts assume the repo root as cwd** (`web/scripts/deploy-frontend.sh` uses `out/` and `.env.$ENV` relative paths). Deploy jobs set `working-directory`, and Task 8 verifies production end-to-end.

---

### Task 1: Credential cleanup and history secret scan

**Files:** none in any repo. Reports go to `~/src/flowebb-migration/scan/` (never committed).

- [ ] **Step 1: Confirm the IAM key with the owner.** Run `aws iam list-access-keys --user-name bbernstein --query 'AccessKeyMetadata[].[Status,CreateDate,AccessKeyId]' --output text` and show **only** the status, date and last 4 characters. Expected: one `Inactive 2024-12-14 …CO7J` and one `Active 2023-04-29 …RGV6`. Ask the owner to confirm that `…CO7J` (inactive, created 2024-12-14) is the key to delete. Never touch `…RGV6`.
- [ ] **Step 2: After confirmation, delete it:** `aws iam delete-access-key --user-name bbernstein --access-key-id <full id from step 1, not echoed>`. Verify that `list-access-keys` now shows only the `…RGV6` key.
- [ ] **Step 3: Remove the static keys from flowebb-orig:** `gh secret delete AWS_ACCESS_KEY_ID -R bbernstein/flowebb-orig` and `gh secret delete AWS_SECRET_ACCESS_KEY -R bbernstein/flowebb-orig`. Verify that `gh secret list -R bbernstein/flowebb-orig` no longer lists either.
- [ ] **Step 4: Scan all five histories.** `brew install gitleaks`. For each of flowebb-orig, flowebb-go, flowebb-web, flowebb-infra, flowebb-esp: `git clone --mirror https://github.com/bbernstein/<r> ~/src/flowebb-migration/scan/<r>.git`, then `gitleaks git ~/src/flowebb-migration/scan/<r>.git --redact --report-path ~/src/flowebb-migration/scan/<r>.json`. Expected: `no leaks found` for each.
- [ ] **Step 5: Handle findings (only if any).** Report each finding to the owner as repo, file, commit and rule ID, with redacted values only. Any real credential gets rotated by the owner. Ask whether to also scrub it from history. Do not continue to Task 2 until the owner decides. Note false positives for a `.gitleaks.toml` allowlist in Task 5.

### Task 2: Create the repo and import the four histories

**Files:**
- Create (via merges): `backend/**`, `web/**`, `infra/**`, `firmware/**` on branch `monorepo-import` in `~/src/flowebb-new`

**Interfaces:**
- Produces: local repo `~/src/flowebb-new` on branch `monorepo-import`, with remote `origin = https://github.com/bbernstein/flowebb`. All later tasks work here.

- [ ] **Step 1: Create the GitHub repo** (owner already approved name and visibility): `gh repo create bbernstein/flowebb --public --add-readme --description "FlowEbb tide platform: backend, web, infra, firmware"`. This removes the old `flowebb` → `flowebb-orig` redirect, as approved. Then `git clone https://github.com/bbernstein/flowebb ~/src/flowebb-new && cd ~/src/flowebb-new && git switch -c monorepo-import`.
- [ ] **Step 2: Rewrite each source repo.** For each pair (flowebb-go→backend, flowebb-web→web, flowebb-infra→infra, flowebb-esp→firmware): `git clone https://github.com/bbernstein/<repo> ~/src/flowebb-migration/src/<repo>` (a fresh clone, never `~/src/flowebb/<repo>`), then `cd` into it and run `git filter-repo --to-subdirectory-filter <dir>`. Expected: `git ls-files | cut -d/ -f1 | sort -u` prints only `<dir>`.
- [ ] **Step 3: Merge each into the branch**, in order backend, web, infra, firmware: `git remote add <repo> ~/src/flowebb-migration/src/<repo> && git fetch <repo> && git merge --allow-unrelated-histories --no-edit -m "Import <repo> history into <dir>/" <repo>/main && git remote remove <repo>`.
- [ ] **Step 4: Verify the import.**
  - `git rev-list --count HEAD` must equal 1 + 34 + 50 + 50 + 5 + 4 = **144**.
  - `git log --oneline -- backend/go.mod | tail -1` shows a December 2024 commit.
  - `ls` shows `README.md backend firmware infra web`.
  - `gitleaks git . --redact` reports no leaks.
- [ ] **Step 5: Push the branch:** `git push -u origin monorepo-import`. Do not open a PR yet.

### Task 3: Go module rename and backend verification

**Files:**
- Modify: `backend/go.mod`, `backend/graph/schema.graphql` (`@goModel` directive), and every tracked file under `backend/` containing `github.com/bbernstein/flowebb-go`. There are 34; list them with `git grep -l`.
- Modify: `backend/README.md` (clone and path instructions). Also update `backend/scripts/*.sh`, `backend/template.yaml` and `backend/samconfig.toml` if they name `flowebb-go` or assume the repo root.

- [ ] **Step 1: Capture the failing check.** `cd backend && git grep -n 'bbernstein/flowebb-go'` lists matches, and that is the failing state. After the change the same command must print nothing. Note that `flowebb-go` may legitimately remain only in README prose that points to the archived repo.
- [ ] **Step 2: Rewrite the path:** `git grep -l 'github.com/bbernstein/flowebb-go' | xargs sed -i '' 's#github.com/bbernstein/flowebb-go#github.com/bbernstein/flowebb/backend#g'`. Fix any README or script references by hand.
- [ ] **Step 3: Regenerate from clean and build:** `rm -rf graph/generated graph/model && go mod download && go run github.com/99designs/gqlgen generate && go mod tidy && go build ./... && go vet ./...`. Expected: exit 0, and `git status` shows no changes to `go.mod`/`go.sum` beyond the module line.
- [ ] **Step 4: Run the tests with coverage.** `docker compose up -d`, then the coverage command from `backend/.github/workflows/verify.yml` ("Generate test coverage" step; this file still exists until Task 4), run from `backend/`. Expected: all pass, and total coverage is ≥ 80%. Then `docker compose down`.
- [ ] **Step 5: Lint with a pinned version.** `go run github.com/golangci/golangci-lint/cmd/golangci-lint@v1.64.8 run --timeout=5m`. The v1 line accepts the existing `.golangci.yml`. Expected: no issues. If the pinned version still fails on existing code, record each fix as CI-only in the PR body.
- [ ] **Step 6: Commit:** `git commit -am "backend: rename Go module to github.com/bbernstein/flowebb/backend"`. The body lists the files touched and notes that the `@goModel` directive in schema.graphql was updated.

### Task 4: Root CI workflows

**Files:**
- Delete: `backend/.github/`, `web/.github/`, `infra/.github/`
- Create: `.github/workflows/verify.yml`, `.github/workflows/deploy-backend.yml`, `.github/workflows/deploy-web.yml`, `.github/workflows/deploy-infra.yml`

**Interfaces:**
- Produces: the required status check named **`ci-gate`**, used by Task 6 for branch protection. Repo secrets consumed (set in Task 6): `AWS_ROLE_ARN`, `FRONTEND_BUCKET_NAME`, `CLOUDFRONT_DISTRIBUTION_ID`, `TF_VAR_PROJECT_NAME`, `TF_VAR_DOMAIN_NAME`, `TF_VAR_FRONTEND_DOMAIN`, `TF_VAR_API_DOMAIN`.
- Consumes: `scripts/check-repo-rules.sh` from Task 5. The `repo-rules` job runs it, so the two tasks land on the same branch before the PR.

- [ ] **Step 1: Write `verify.yml`.** Triggers: `pull_request` to `main` and `workflow_dispatch`. Permissions: `id-token: write`, `contents: read`, `pull-requests: read`. Jobs:
  - `changes`: `dorny/paths-filter@v3` with outputs `backend` (`backend/**`), `web` (`web/**`), `infra` (`infra/**`), `firmware` (`firmware/**`), and `workflows` (`.github/workflows/**`). When `workflows` is true, every platform job runs.
  - `backend-lint`, `backend-test`: carry over the old steps. Use `defaults.run.working-directory: backend`, `actions/setup-go@v5` with `go-version-file: backend/go.mod` and `cache-dependency-path: backend/go.sum`, and gqlgen via `go run github.com/99designs/gqlgen generate` after `rm -rf graph/generated graph/model`. Lint uses `golangci/golangci-lint-action@v6` with `version: v1.64.8`, `working-directory: backend`. Tests run `go test -race ./...`, then the 80% coverage gate unchanged, and upload `backend/test-results/*`.
  - `web-test`, `web-build`: `actions/setup-node@v4`, node 22, `cache-dependency-path: web/package-lock.json`, `working-directory: web`. Steps: `npm ci`, `npm run lint`, `npm run test` / `npm run build`.
  - `infra-lint`: `terraform fmt -check -recursive` and tflint v0.44.1 in `infra/terraform`.
  - `infra-plan`: AWS OIDC with `secrets.AWS_ROLE_ARN`, Terraform 1.5.0, then `terraform init` and `terraform plan -detailed-exitcode -out=tfplan` in `infra/terraform/environments/prod`, with the four `TF_VAR_*` env vars. Keep the old lock-cleanup step. Exit code 2 (changes present) must **fail** the job with the message "Terraform plan has changes — owner review required".
  - `secrets-scan`: `gitleaks/gitleaks-action@v2` with `fetch-depth: 0` (always runs).
  - `repo-rules`: runs `scripts/check-repo-rules.sh` (always runs; Task 5).
  - `ci-gate`: `needs:` every job above, `if: always()`. It fails if any needed job's result is `failure` or `cancelled`, and passes otherwise. Skipped counts as pass.
  - All jobs use `actions/checkout@v4`.
- [ ] **Step 2: Write the deploy workflows** (trigger: `workflow_dispatch` only; a comment says "push trigger re-enabled in follow-up after cutover"):
  - `deploy-backend.yml`: the old flowebb-go `ci.yml` steps, with `working-directory: backend` and the pinned gqlgen. Lambda `flowebb-graphql-prod` is unchanged.
  - `deploy-web.yml`: the old flowebb-web `ci.yml`, with `working-directory: web`, `NEXT_PUBLIC_API_URL: https://api.flowebb.com`, and `./scripts/deploy-frontend.sh prod`.
  - `deploy-infra.yml`: the old flowebb-infra `ci.yml`, with `working-directory: infra/terraform/environments/prod` and the lock cleanup kept.
- [ ] **Step 3: Lint the workflows:** `brew install actionlint && actionlint`. Expected: no output.
- [ ] **Step 4: Test the gate logic locally.** Move the `ci-gate` decision into `.github/scripts/ci-gate.sh`, which takes the `toJSON(needs)` JSON on stdin and exits non-zero if any `.result` is `failure` or `cancelled`. Add `.github/scripts/ci-gate.test.sh` covering four cases:
  - all `success` → exit 0
  - `success` + `skipped` → exit 0
  - one `failure` → exit 1
  - one `cancelled` → exit 1

  Run `bash .github/scripts/ci-gate.test.sh`. Expected: `4 passed`.
- [ ] **Step 5: Commit:** `git add -A .github backend web infra && git commit -m "ci: move workflows to repo root with path filters and ci-gate; deploys manual-only"`. The body lists every version pin added and why.

### Task 5: Rules, docs, repo hygiene

**Files:**
- Create: `scripts/check-repo-rules.sh`, `scripts/check-repo-rules.test.sh`
- Create: `CLAUDE.md`, `AGENTS.md` (one line: "See CLAUDE.md — the same rules apply to all AI agents."), `.gitignore`, `flowebb.code-workspace`
- Create: `backend/CLAUDE.md`, `web/CLAUDE.md`, `infra/CLAUDE.md`, `firmware/CLAUDE.md`
- Create: `.claude/agents/backend.md`, `.claude/agents/web.md`, `.claude/agents/infra.md`, `.claude/agents/firmware.md`
- Create: `docs/architecture.md`, `docs/adding-a-platform.md`, `docs/decisions/README.md`
- Copy in: `docs/superpowers/specs/2026-11-21-monorepo-consolidation-design.md`, `docs/superpowers/plans/2026-11-21-monorepo-consolidation.md` (from `~/src/flowebb/docs/`)
- Modify: `README.md` (replace the GitHub placeholder), `firmware/README.md` and `web/README.md` (clone and `cd` instructions now point at `bbernstein/flowebb` and the subdirectory)
- Optional: `.gitleaks.toml`, only if Task 1 found false positives

- [ ] **Step 1: Write the failing rules test.** `scripts/check-repo-rules.sh` (bash, run from the repo root, exit 1 with a message per violation) asserts:
  - each of `backend web infra firmware` has a `CLAUDE.md`
  - root `CLAUDE.md` and every platform `CLAUDE.md` contain the exact heading `## Never commit secrets or private data`
  - root `CLAUDE.md` mentions each of `backend/`, `web/`, `infra/`, `firmware/`
  - `git ls-files` contains no path matching these patterns:
    - `(^|/)\.env($|\.)` (except names ending `.example`)
    - `\.tfvars$`
    - `\.tfstate`
    - `wifi_credentials\.h$`
    - `\.pem$`
    - `id_rsa`
    - `conversations.*\.json$`

  `scripts/check-repo-rules.test.sh` builds throwaway git repos in `mktemp -d` and asserts the script passes on a compliant tree and fails, naming the problem, for each of: a missing platform CLAUDE.md, a missing heading, a tracked `web/.env.local`, a tracked `infra/terraform/environments/prod/terraform.tfvars`, and a tracked `firmware/src/config/wifi_credentials.h`. The compliant tree must also include `terraform.tfvars.example` and `wifi_credentials.h.template`, which are allowed.
- [ ] **Step 2: Run both:** `bash scripts/check-repo-rules.test.sh` should report all passing (the script logic works), and `bash scripts/check-repo-rules.sh` on the real tree should FAIL with "missing backend/CLAUDE.md" and similar, because the docs don't exist yet.
- [ ] **Step 3: Write the root `CLAUDE.md`** with the content in spec §7.1, in this order:
  1. What FlowEbb is (3–4 lines)
  2. `## Never commit secrets or private data`: the full list from Global Constraints, "this repo is public", use `*.example`/`*.template`, stop and ask if a needed value is sensitive
  3. The routing table
  4. The API contract (`backend/graph/schema.graphql`; backward-compatible changes only, because devices in the field and deployed web can't update in lockstep)
  5. The cross-platform change protocol, including dispatching the `.claude/agents/*` agents in parallel, one per affected platform
  6. Git rules: no commits to main, PRs squash-merged, run the affected platforms' lint and tests before committing
  7. The commands quick reference per platform
  8. "Check `docs/decisions/` before changing behavior; add a record for non-obvious choices"
  9. A pointer to `docs/adding-a-platform.md`
- [ ] **Step 4: Write the four platform `CLAUDE.md` files** per spec §7.2. Each has the same `## Never commit secrets or private data` heading and names that platform's sensitive files:
  - backend: `docker/` data
  - web: `.env.local`, `.env.*`
  - infra: `terraform.tfvars`, `.terraform/`, state
  - firmware: `src/config/wifi_credentials.h`

  Commands are exactly the ones verified in Tasks 3 and 7. Backend says ≥80% coverage is enforced in CI. Web says CI does not enforce coverage and `jest.config.js` sets 70%, and that new code still needs tests. Infra says `apply` only through `deploy-infra.yml` with owner approval, and that the OIDC trust list is in `terraform/environments/prod/main.tf`. Firmware says the device depends on the API fields it reads, so don't break them.
- [ ] **Step 5: Write the four `.claude/agents/*.md` files.** Use frontmatter `name`, `description` ("Use for changes under `<dir>/` — <stack>; dispatch in parallel with other platform agents for cross-platform features"). The body says:
  - read `<dir>/CLAUDE.md` and the root `CLAUDE.md` first
  - edit only inside `<dir>/` (read-only access to `backend/graph/schema.graphql` and `docs/`)
  - run that platform's lint and tests before reporting
  - report the files changed, the commands run and their results

  Omit `tools`, so agents inherit all tools.
- [ ] **Step 6: Write the docs.**
  - `docs/architecture.md`: components and data flow. NOAA API → Go Lambda (GraphQL at `api.flowebb.com/graphql`, DynamoDB prediction cache, S3 station cache, LRU) → Next.js static site on S3 + CloudFront, plus the ESP32 polling the same endpoint. Also: Terraform owns all AWS resources, and CI deploys through the OIDC role `flowebb-github-actions-prod`.
  - `docs/adding-a-platform.md`: a checklist covering:
    - directory under `apps/`
    - its `CLAUDE.md` with the secrets heading
    - the `.claude/agents` entry
    - the routing table row
    - the `changes` filter and jobs in `verify.yml`, plus a `ci-gate` need
    - the directory added to the `check-repo-rules.sh` platform list
    - an architecture doc update
  - `docs/decisions/README.md`: index stub with the record template (Context / Decision / Alternatives rejected / Platforms / Source) and "raw transcripts are never committed".
- [ ] **Step 7: Write the root `.gitignore`** (`.DS_Store`, `.idea/`, `.vscode/` except the workspace file, `.claudesync/`, `.env`, `.env.*`, `!.env.example`, `*.tfvars`, `!*.tfvars.example`, `*.tfstate*`, `.terraform/`). Write `flowebb.code-workspace` with folders `.`, `backend`, `web`, `infra`, `firmware`. Update the root `README.md`: what FlowEbb is, the layout table, quick start per platform pointing to each platform README, and the history note ("imported from the archived flowebb-* repos with full history").
- [ ] **Step 8: Verify.** `bash scripts/check-repo-rules.sh` should exit 0. `bash scripts/check-repo-rules.test.sh` should report all passing. `gitleaks dir . --redact` should report no leaks. Then confirm every relative markdown link resolves: `grep -roh '\]([^)#:]*)' --include=*.md . | sort -u`, and check each path exists relative to its file.
- [ ] **Step 9: Commit:** `git add -A && git commit -m "docs: add root and per-platform CLAUDE.md, agents, architecture, repo-rules check"`.

### Task 6: Repo secrets, settings, PR, and the merge

**Interfaces:**
- Consumes: `ci-gate` check from Task 4. Branch `monorepo-import` (Tasks 2–5) is on origin.

- [ ] **Step 1: Run the web, infra and firmware local checks** (fix path assumptions if any fail, commit as `fix: <dir> paths after move`):
  - `cd web && npm ci && npm run lint && npm test && npm run build`
  - `cd infra/terraform && terraform fmt -check -recursive && tflint --recursive`
  - `cd firmware`: if `pio` is unavailable, `brew install platformio`. Copy `src/config/wifi_credentials.h.template` to `src/config/wifi_credentials.h` (gitignored), then `pio run`. If it can't build, record "firmware build unverified" in the PR body.
- [ ] **Step 2: Gather the secret values without printing them:**
  - `AWS_ROLE_ARN` = `aws iam get-role --role-name flowebb-github-actions-prod --query Role.Arn --output text`
  - `CLOUDFRONT_DISTRIBUTION_ID` and `FRONTEND_BUCKET_NAME`: use the distribution whose aliases include the frontend domain from `terraform.tfvars`, and the S3 bucket behind its origin (`aws cloudfront list-distributions` / `get-distribution-config`)
  - The four `TF_VAR_*` come from `~/src/flowebb/flowebb-infra/terraform/environments/prod/terraform.tfvars` (`project_name`, `domain_name`, `frontend_domain`, `api_domain`)

  Show the owner the list of **names** to be set (not values) and get confirmation. Then pipe each value straight in: `<cmd> | gh secret set <NAME> -R bbernstein/flowebb`. Verify that `gh secret list -R bbernstein/flowebb` shows all 7 names. Do not create `NEXT_PUBLIC_API_URL`.
- [ ] **Step 3: Repo settings for the import:** `gh repo edit bbernstein/flowebb --enable-merge-commit --enable-squash-merge=false --enable-rebase-merge=false --delete-branch-on-merge`. Branch protection on `main`: require a PR and the status check `ci-gate` (`gh api -X PUT repos/bbernstein/flowebb/branches/main/protection` with `required_status_checks.contexts=["ci-gate"]`, `enforce_admins=false`, `required_pull_request_reviews=null`, `restrictions=null`).
- [ ] **Step 4: Open the PR:** `git push && gh pr create --base main --head monorepo-import --title "Consolidate flowebb-go/web/infra/esp into one repo with full history"`. The body covers:
  - the import summary (commit counts per source)
  - the fix-up commits
  - every version pin and CI-only change
  - "**Merge with 'Create a merge commit' only — squash would destroy imported history**"
  - "Deploys are manual-only until production is verified"
  - the attribution footer from the session
- [ ] **Step 5: Iterate until green** (per the owner's CLAUDE.md "iterate on a PR" rules): address reviews and fix failing checks. `infra-plan` must succeed, meaning no changes. If it fails with the plan-has-changes message, **stop**: show the owner the plan's resource summary (from the job log) and wait. Do not merge, apply or "fix" by editing Terraform without the owner.
- [ ] **Step 6: Ask the owner to merge** with "Create a merge commit". Never merge automatically.
- [ ] **Step 7: After merge:** `git fetch && git log --oneline origin/main -- backend/go.mod | wc -l` must be > 1, and `git rev-list --count origin/main` ≥ 144. Then switch to squash-only: `gh repo edit bbernstein/flowebb --enable-squash-merge --enable-merge-commit=false --enable-rebase-merge=false`. Verify with `gh repo view bbernstein/flowebb --json squashMergeAllowed,mergeCommitAllowed` → `true,false`.

### Task 7: Manual deploys and production verification

- [ ] **Step 1: With owner approval for each**, dispatch in order and wait for each to succeed. Infra should be a no-op apply, since the plan was clean.
  1. `gh workflow run deploy-infra.yml -R bbernstein/flowebb --ref main`
  2. `deploy-backend.yml`
  3. `deploy-web.yml`

  Watch each with `gh run watch`.
- [ ] **Step 2: Verify the API:** `curl -s -X POST https://api.flowebb.com/graphql -H 'Content-Type: application/json' -d '{"query":"{ stations(lat: 42.36, lon: -71.05, limit: 1) { id name source } }"}'`. Expected: JSON with `data.stations[0].id` non-empty and no `errors`.
- [ ] **Step 3: Verify the site:** `curl -sI https://<frontend_domain>` → `200`. Load it in a browser (claude-in-chrome) and confirm the map and a tide chart render for a station.
- [ ] **Step 4: Record the result** in the memory file `monorepo-consolidation-status.md` (production verified from the monorepo, with the date).

### Task 8: Re-enable deploy-on-push (follow-up PR)

**Files:**
- Modify: `.github/workflows/deploy-backend.yml`, `deploy-web.yml`, `deploy-infra.yml`

- [ ] **Step 1:** On branch `ci-enable-deploy-triggers`, add `push: branches: [main]` with `paths:` to each deploy workflow (`backend/**` / `web/**` / `infra/**`, plus that workflow's own file). Keep `workflow_dispatch`. Remove the "manual-only" comment.
- [ ] **Step 2:** `actionlint` → no output. Open the PR, get it green, and ask the owner to squash-merge.

### Task 9: Local cutover of `~/src/flowebb`

> Run this last in a session, or from a session started outside `~/src/flowebb`. Moving the session's working directory breaks the shell.

- [ ] **Step 1:** `mv ~/src/flowebb ~/src/flowebb-legacy && mv ~/src/flowebb-new ~/src/flowebb`. The legacy folder keeps the old clones, `tmp/`, the old workspace file and `docs/` (already copied into the repo).
- [ ] **Step 2: Restore the local-only config:**
  - `cp ~/src/flowebb-legacy/flowebb-web/.env.local ~/src/flowebb/web/.env.local`
  - `cp ~/src/flowebb-legacy/flowebb-infra/terraform/environments/prod/terraform.tfvars ~/src/flowebb/infra/terraform/environments/prod/`
  - `cp ~/src/flowebb-legacy/flowebb-esp/src/config/wifi_credentials.h ~/src/flowebb/firmware/src/config/`
- [ ] **Step 3: Verify:** in `~/src/flowebb`, `git status --porcelain` is empty, and `git status --ignored --porcelain` lists all three files with `!!`. `bash scripts/check-repo-rules.sh` exits 0.

### Task 10: Retire the old repos

- [ ] **Step 1: Get owner confirmation** to retire flowebb-go, flowebb-web, flowebb-infra, flowebb-esp and flowebb-orig.
- [ ] **Step 2:** For each repo, delete every Actions secret (`gh secret list -R bbernstein/<r> --json name -q '.[].name' | xargs -I{} gh secret delete {} -R bbernstein/<r>`), then verify that `gh secret list` is empty.
- [ ] **Step 3:** For each repo: `gh repo edit bbernstein/<r> --description "Archived — moved to github.com/bbernstein/flowebb (<dir>/)"`. flowebb-orig's description reads "Archived — original prototype; current code at github.com/bbernstein/flowebb". Then run `gh repo archive bbernstein/<r> --yes`. Verify that `gh repo view bbernstein/<r> --json isArchived` is `true` for all five.

### Task 11: Decision records from the claude.ai export

**Files:**
- Create: `docs/decisions/NNNN-<slug>.md` (numbered from `0001`), and update `docs/decisions/README.md` (index grouped by platform)
- Modify: each platform `CLAUDE.md` and the root `CLAUDE.md` ("Key decisions" links)
- Tools (never committed): `~/src/flowebb-migration/tools/select_conversations.py`, with output in `~/src/flowebb-migration/export-work/`

- [ ] **Step 1: Write `select_conversations.py`** (Python, run with `python3 -I`). It reads `conversations.json` and `projects/*.json` from the export path. It selects conversations whose name, summary or first 6 messages match `flowebb|tide|noaa|esp32|neopixel` (on name or summary), or have ≥3 body matches. It writes `candidates.tsv` (date, title, message count, uuid) and one plain-text file per conversation, plus each FlowEbb project's `prompt_template` and doc names, into `export-work/`. Expected: about 144 candidates between 2024-11-23 and 2025-01-31.
- [ ] **Step 2: The owner confirms the list.** Show the owner `candidates.tsv` as date + title only. They strike unrelated entries (for example, the e-paper weather display) and may add others. Save the result as `confirmed.tsv`.
- [ ] **Step 3: Extract in parallel.** Group the confirmed conversations by platform (backend, web, infra, firmware, cross-cutting/history) using title keywords. Dispatch one agent per group. Each agent reads its conversation files and the matching project docs and writes draft records to `export-work/drafts/<group>/`. Each record follows the template (Context, Decision, Alternatives rejected, Platforms, Source: project name + conversation date), with no raw quotes of credentials or personal data. Decisions no longer true in the current code are marked `Status: superseded by <what>`. Agents check claims against the code in `~/src/flowebb`.
- [ ] **Step 4: Review and number.** Dedupe across groups, number the records chronologically, and copy them to `docs/decisions/`. Fill the README index by platform. Add "Key decisions" links to each CLAUDE.md. Must-cover topics if present in the chats:
  - Kotlin/Ktor → Go rewrite and why
  - harmonic calculation → NOAA API predictions
  - prediction and station caching design (DynamoDB / S3 / LRU, TTLs)
  - REST → GraphQL
  - monorepo → split (2025-01-27) → monorepo again (2026-11-21, this spec)
  - Terraform layout and OIDC deploys
  - firmware LED semantics
  - timezone handling
- [ ] **Step 5: Verify.** `gitleaks dir docs --redact` should report no leaks. `bash scripts/check-repo-rules.sh` should exit 0. Then manually read every record for names, emails, keys and hostnames beyond the public domain.
- [ ] **Step 6:** On branch `docs-decision-records`, commit `docs: add decision records mined from claude.ai project history`, open the PR, get it green, and ask the owner to squash-merge.
- [ ] **Step 7:** Update the memory file. Mark the initiative complete and list the follow-ups from spec §12.
