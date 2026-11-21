# FlowEbb Monorepo Consolidation — Design

**Date:** 2026-11-21
**Status:** Draft for approval (builder-checked; owner decisions folded in)

## 1. Intent

FlowEbb is a tide-tracking platform that today lives in four separate public
GitHub repos (`flowebb-go`, `flowebb-web`, `flowebb-infra`, `flowebb-esp`),
plus an abandoned predecessor (`flowebb-orig`). It was built before Claude Code,
using five claude.ai Projects (FlowEbb, FlowEbb Go, FlowEbb Webapp,
FlowEbb Infra, FlowEbb Device).

The owner wants one place to work from — `~/src/flowebb` — where an AI coding
partner can take a request like "add X to the frontend" or "show next high tide
on the device", know which platform(s) it touches, and dispatch parallel agents
per platform. Future platforms (iPhone app, watchOS app) will be added, so the
structure must make adding a platform cheap and obvious.

**Decision:** consolidate into a single new public monorepo,
`github.com/bbernstein/flowebb`, with each platform in its own top-level
directory, **preserving each repo's full commit history** under its new
directory. The old repos are archived (read-only) on GitHub.

## 2. Goals, non-goals, anti-goals

**Goals**
- One repo, one clone, one PR for cross-platform changes (e.g. a GraphQL
  schema change consumed by web, firmware, and later iOS/watchOS).
- Full per-file history preserved (`git log backend/...`, `git blame` work).
- A root `CLAUDE.md` that routes work to the right platform and describes how
  to coordinate multi-platform changes; a `CLAUDE.md` per platform with that
  platform's commands and rules.
- Strong, explicit rule in every rules file: **never commit secrets or private
  data** (tokens, passwords, keys, credentials, Wi-Fi credentials, customer or
  personal information).
- Existing CI (lint, test, 80% coverage gates, deploys) works from the monorepo
  with per-directory path filters.
- Design history from the old claude.ai Projects captured as decision records,
  so agents don't undo past nuances.

**Non-goals (this effort)**
- No application behavior changes. The migration is mechanical: moves, the Go
  module path rename, CI path changes, docs.
- No iOS/watchOS code yet — only the slot and the "how to add a platform" doc.
- No upgrades of dependencies/actions beyond what's needed to make CI pass.
- No firmware CI (PlatformIO build) yet — recorded as a follow-up.

**Anti-goals (failure even if it "works")**
- A secret, `.env.local`, `terraform.tfvars`, `wifi_credentials.h`, or raw chat
  transcript lands in the public repo.
- History flattened (e.g. PR squash-merged) — defeats the point.
- Merging the migration PR triggers an unreviewed `terraform apply` or deploy.
- Production (`api.flowebb.com`, the web site, devices in the field) breaks.

## 3. Current state (facts gathered 2026-11-21)

| Repo | Stack | Deploy | Notes |
|---|---|---|---|
| flowebb-go | Go 1.23, gqlgen GraphQL, AWS Lambda (arm64), DynamoDB/S3 caches | `ci.yml` on push to main → `aws lambda update-function-code flowebb-graphql-prod` | Module path `github.com/bbernstein/flowebb-go`, in 34 tracked files including `go.mod` and `graph/schema.graphql` (`@goModel(model: "github.com/bbernstein/flowebb-go/graph.Resolver")`). Only backend CI enforces 80% coverage. One uncommitted local change: gqlgen version header in `graph/schema.resolvers.go` (noise from `gqlgen@latest`; discard). |
| flowebb-web | Next.js 15, React 18, Apollo, MUI, Leaflet, Highcharts, Jest | `ci.yml` → `scripts/deploy-frontend.sh prod` (S3 + CloudFront) | Local `.env.local` (gitignored). CI runs plain `jest` — `jest.config.js` has a 70% threshold that CI never enforces. No GraphQL codegen. |
| flowebb-infra | Terraform 1.5, S3 state `flowebb-terraform-state-bucket`, DynamoDB lock | `ci.yml` on push to main → `terraform apply -auto-approve` | Local `terraform/environments/prod/terraform.tfvars` (gitignored). OIDC trust list `github_repos = ["flowebb", "flowebb-web", "flowebb-go", "flowebb-infra"]` — **`flowebb` is already trusted**, so the new repo can assume the AWS role with no Terraform change. |
| flowebb-esp | ESP32-S3, PlatformIO/Arduino, NeoPixel | Manual (`pio run -t upload`) | Local `src/config/wifi_credentials.h` (gitignored). Calls `https://api.flowebb.com/graphql`. No CI. |

- All four are on `main`, in sync with origin.
- GitHub Actions secrets used: `AWS_ROLE_ARN`, `FRONTEND_BUCKET_NAME`,
  `CLOUDFRONT_DISTRIBUTION_ID`, `TF_VAR_PROJECT_NAME`, `TF_VAR_DOMAIN_NAME`,
  `TF_VAR_FRONTEND_DOMAIN`, `TF_VAR_API_DOMAIN`. These must be recreated in the
  new repo (secret values can't be copied between repos).
- `github.com/bbernstein/flowebb` currently redirects to `flowebb-orig`; the
  owner has approved taking the name (this removes the redirect).
- Each repo has a `.claudesync/` dir (gitignored, ClaudeSync leftovers). No
  repo has a `CLAUDE.md`.
- `git-filter-repo` is installed locally; `gitleaks` and `pio` are not.

## 4. Approaches considered

1. **Git submodules** in a hub repo. Pins exact versions, but every sub-repo
   change needs a pointer-bump commit; detached HEADs confuse agents. Rejected.
2. **Hub repo + manifest/clone script**, sub-repos gitignored. Keeps repos
   independent but cross-platform changes need N branches/PRs and the hub can't
   express "these changes go together". Rejected — the platforms share one API
   contract and one owner; independence buys nothing.
3. **Monorepo with preserved history** — **chosen.** One branch/PR per feature,
   agents share one view of the schema, adding iOS/watchOS is a new directory.
   Costs are one-time: CI path filters, Go module rename, new repo secrets.

**Prior art — this project was a monorepo once.** `flowebb-orig` was a
monorepo (frontend, Kotlin→Go backend, infrastructure). On 2025-01-27 it was
split into separate repos because it had grown "too large to share as context
with genai models" — a limit of claude.ai Projects, where the whole knowledge
base is loaded. Claude Code doesn't have that limit: it reads files on demand,
and per-directory `CLAUDE.md` files and per-platform subagents keep each agent's
context scoped to one platform. So the original reason for splitting no longer
applies. The split used `git filter-repo`, so flowebb-go/web/infra history
already includes their pre-split commits from flowebb-orig; importing them
carries that history forward.

Go in a subdirectory is fully supported: a module is rooted wherever its
`go.mod` lives. `go` commands run from `backend/`; nothing Go-specific touches
the repo root. No `go.work` is needed while there's one Go module.

## 5. Target layout

```
flowebb/
├── CLAUDE.md                 # root rules + routing table (see §7)
├── README.md                 # what FlowEbb is, layout, quick start per platform
├── .gitignore                # root-level ignores (OS, editors, .env*, secrets)
├── .gitleaks.toml            # (optional) allowlist for known false positives
├── flowebb.code-workspace    # VS Code multi-root, one folder per platform
├── .github/workflows/        # all CI, path-filtered per platform (see §8)
├── .claude/agents/           # one subagent definition per platform (see §7.3)
├── docs/
│   ├── architecture.md       # system overview, data flow, API contract location
│   ├── adding-a-platform.md  # checklist for iOS/watchOS etc.
│   ├── decisions/            # ADR-style records mined from old Projects (§9)
│   ├── plans/                # implementation plans
│   └── superpowers/specs/    # design docs (this file)
├── backend/                  # from flowebb-go   (history preserved)
├── web/                      # from flowebb-web  (history preserved)
├── infra/                    # from flowebb-infra (history preserved)
├── firmware/                 # from flowebb-esp  (history preserved)
└── apps/                     # future: apps/ios, apps/watchos (not created now)
```

Each platform directory keeps its own `README.md`, `.gitignore`, and gains a
`CLAUDE.md`. Per-repo `.github/` directories are removed from the platform dirs
(their workflows move to the root).

## 6. Migration mechanics

### 6.1 History import
For each repo, work on a **fresh clone from GitHub** in a scratch directory
(never the local working copies):

```
git clone https://github.com/bbernstein/flowebb-go scratch/backend-src
cd scratch/backend-src && git filter-repo --to-subdirectory-filter backend
```
(likewise web, infra, firmware). Filter-repo rewrites only the path prefix;
authors, dates, and messages are kept. Tags, if any, get prefixed
(`--tag-rename '':'backend-'`) to avoid collisions.

Assemble in a new local repo: start from an initial commit, then for each
rewritten repo `git fetch` it and `git merge --allow-unrelated-histories`
with a message like `Import flowebb-go history into backend/`. Then apply the
fix-up commits (§6.3) on top.

### 6.2 Branching and the first merge
Owner rule: never commit to `main` directly.
- Create the GitHub repo with `gh repo create bbernstein/flowebb --public
  --add-readme`, so GitHub creates `main` with one placeholder commit.
- All import + fix-up work goes on branch `monorepo-import`, based on that
  `main` commit.
- Open a PR. **It must be merged with "Create a merge commit"** (not squash,
  not rebase) or the imported history is lost. Before that merge, set the repo
  to allow merge commits only, so a squash can't happen by accident.
- **Right after the import merge, switch the repo back to squash-merge only**
  (owner decision: everyday PRs are squash-merged, as in the old repos).

### 6.3 Fix-up commits on the branch (in this order, one commit each)
1. Remove per-platform `.github/` dirs; add root `.github/workflows/` (§8).
2. Go module rename: `github.com/bbernstein/flowebb-go` →
   `github.com/bbernstein/flowebb/backend` in `go.mod`, all imports,
   `graph/schema.graphql` (`@goModel` directive), and any scripts. Verify with `gqlgen generate`, `go build ./...`,
   `go test ./...`, `golangci-lint run` from `backend/`.
3. Path fixes in scripts/docs that assumed a repo root (e.g.
   `web/scripts/deploy-frontend.sh`, `backend/scripts/*.sh`, `backend/template.yaml`
   / `samconfig.toml` if they use relative paths, READMEs' clone instructions).
4. Root `.gitignore`, `README.md`, `CLAUDE.md`, per-platform `CLAUDE.md`,
   `.claude/agents/*`, `docs/architecture.md`, `docs/adding-a-platform.md`,
   updated `flowebb.code-workspace`, this design doc.
5. Remove committed noise found during the move (e.g. tracked `coverage.out`,
   if tracked) — only if tracked; no behavior change.

### 6.4 Secret scanning (gate before first push)
- Install `gitleaks` (`brew install gitleaks`) and run `gitleaks git` over the
  **assembled history** and `gitleaks dir` over the working tree.
- The old repos are already public, so any real finding in history is already
  exposed: the remedy is to **rotate the credential** and tell the owner, then
  decide (with the owner) whether to also scrub it with filter-repo before push.
- Add a `gitleaks` job to CI on every PR (§8).

### 6.5 Credential cleanup (owner decision: remove access keys from all old repos)
Findings (2026-11-21, read-only checks):
- Only **flowebb-orig** holds static AWS keys (`AWS_ACCESS_KEY_ID`,
  `AWS_SECRET_ACCESS_KEY`, set 2024-12-16). The other old repos hold only
  `AWS_ROLE_ARN` (OIDC role, not a credential) and config identifiers.
- In AWS, the IAM user `bbernstein` has a key created 2024-12-14 that is
  already **Inactive** and was never used — almost certainly the one stored in
  flowebb-orig (secret values can't be read back to confirm). The user's other
  active key (2023) is their personal CLI key and is not touched.
- flowebb-orig tracks `.env.prod`, but it contains only bucket name,
  CloudFront distribution ID and API URL — identifiers, not credentials.
Steps:
- Delete the inactive 2024-12-14 access key from IAM (owner confirms the
  key ID suffix first; deleting can't be undone, which is the point).
- Delete `AWS_ACCESS_KEY_ID` / `AWS_SECRET_ACCESS_KEY` from flowebb-orig now.
- After cutover is verified, delete **all** Actions secrets from all five old
  repos before archiving (archived repos can't run Actions; nothing needs them).
- Run gitleaks over the full history of **all five** repos (flowebb-orig too,
  since it stays public); handle findings per §6.4.

### 6.6 GitHub repo setup
- Recreate Actions secrets in `bbernstein/flowebb`. The root Terraform module
  does not output the role ARN or bucket name, so take `AWS_ROLE_ARN`,
  `FRONTEND_BUCKET_NAME`, `CLOUDFRONT_DISTRIBUTION_ID` from the AWS CLI
  (IAM role / S3 / CloudFront lookups — local AWS credentials work) or
  `terraform state show`; TF_VAR_* from the local `terraform.tfvars`.
  flowebb-web's unused `NEXT_PUBLIC_API_URL` secret is not recreated. The builder
  sets them with `gh secret set` after showing the owner which names it will set
  (never echoing values into logs, files, or chat). If any value can't be
  derived, ask the owner.
- Branch protection on `main`: require PR + passing checks.

### 6.7 Deploy safety for the first merge
Because every path is "new", path-filtered deploy workflows would all fire on
the first merge to `main` — including `terraform apply -auto-approve`.
- In the migration PR, deploy workflows are **`workflow_dispatch` only** (no
  `push` trigger).
- Before merging, the PR's verify workflows must pass, including
  `terraform plan` from `infra/` showing **no resource changes**. A clean plan
  also confirms the re-entered TF_VAR_* secrets match production. If the plan
  shows any resource add/change/destroy (possible: provider floats because
  `.terraform.lock.hcl` is gitignored; CI pins TF 1.5.0; old run history has
  expired), **stop and show the owner the plan** — do not merge or apply.
  Purely cosmetic output (e.g. provider-version notes) is fine.
- After merge, the owner (or builder with approval) manually dispatches each
  deploy once and verifies production: `api.flowebb.com/graphql` responds, the
  website loads and shows tides.
- A small follow-up PR adds the `push: main` + `paths:` triggers back.

### 6.8 Local cutover of `~/src/flowebb`
- Move the old clones (`flowebb-go`, `flowebb-web`, `flowebb-infra`,
  `flowebb-esp`, `flowebb-orig`) and `tmp/` to `~/src/flowebb-legacy/` (not
  deleted). Keep this design doc and any `docs/` written here.
- Make `~/src/flowebb` the clone of the new repo (e.g. `git init`, add remote,
  fetch, check out `main`; or clone to a temp dir and move `.git` in).
- Copy local-only, gitignored config into the new layout:
  `web/.env.local`, `infra/terraform/environments/prod/terraform.tfvars`,
  `firmware/src/config/wifi_credentials.h`. Confirm `git status` shows them as
  ignored, not untracked.

### 6.9 Archive old repos
After production is verified from the monorepo:
- Set each old repo's description to "Moved to bbernstein/flowebb (<dir>/)"
  via `gh repo edit` (no commits to their `main`), then `gh repo archive`.
  Applies to flowebb-go, flowebb-web, flowebb-infra, flowebb-esp, flowebb-orig.
- Confirm with the owner before archiving (outward-facing, but reversible).
- Follow-up (separate infra PR): remove the old repo names from
  `github_repos`, leaving `["flowebb"]`.

## 7. Rules files

### 7.1 Root `CLAUDE.md`
Must contain:
- **What FlowEbb is** in 3–4 lines, and a pointer to `docs/architecture.md`.
- **Routing table**: request area → directory → stack → its CLAUDE.md.
  - UI, pages, charts, maps → `web/`
  - API, GraphQL schema, tide/station data, caching, Lambda code → `backend/`
  - AWS resources, DNS, CloudFront, IAM, Lambda config, CI deploy roles → `infra/`
  - ESP32 device, LED behavior, Wi-Fi, on-device tide logic → `firmware/`
  - Future: `apps/ios/`, `apps/watchos/`
- **The API contract**: `backend/graph/schema.graphql` is the source of truth
  for every client. A change to it is a cross-platform change.
- **Cross-platform change protocol**: (1) identify affected platforms from the
  routing table; (2) change the contract first (backend schema), keep it
  backward compatible because firmware in the field and deployed web can't
  update atomically; (3) dispatch one agent per affected platform in parallel
  (using `.claude/agents/`), each working only in its directory; (4) one
  branch, one PR; (5) deploy order: infra → backend → web/apps; firmware last
  and never required for backend to work.
- **Secrets & privacy rule (prominent, near the top):** never commit tokens,
  passwords, API keys, AWS account details beyond what's already public,
  Wi-Fi credentials, `.env*` files, `terraform.tfvars`, Terraform state,
  customer/user data, or personal information (names, emails, phone numbers,
  addresses). Use templates (`*.example`, `*.template`) for config. If a needed
  value is sensitive, stop and ask. This repo is public.
- **Git rules:** never commit to `main`; branch + PR; PRs are squash-merged
  (the one-time history import was the only merge-commit exception); run the
  affected platforms' lint + tests before committing.
- **Decision records:** check `docs/decisions/` before changing behavior in an
  area; add a record when making a non-obvious choice.
- How to add a platform → `docs/adding-a-platform.md`.

### 7.2 Per-platform `CLAUDE.md` (`backend/`, `web/`, `infra/`, `firmware/`)
Each contains: stack and versions; directory map; exact commands (install,
dev, lint, test, coverage, build, deploy) run from that directory; coverage
threshold (backend: 80% enforced in CI; web: state truthfully that CI does
not enforce coverage yet and the jest config says 70%; new code must still
have tests per the owner's rules); platform-specific rules;
a one-line restatement of the secrets rule naming that platform's sensitive
files; links to relevant `docs/decisions/`. Platform specifics to capture:
- backend: gqlgen regenerate workflow (generated files `generated.go`,
  `*_gen.go` are gitignored and rebuilt by `gqlgen generate`), Docker-based local DynamoDB for tests, module path.
- web: `NEXT_PUBLIC_API_URL`, static export to S3/CloudFront.
- infra: never run `terraform apply` locally without the owner's approval;
  `plan` is safe; state is remote; OIDC trust list.
- firmware: `wifi_credentials.h` from template, never committed; device
  depends on API fields — don't break them.

### 7.3 `.claude/agents/`
One subagent definition per platform (`backend.md`, `web.md`, `infra.md`,
`firmware.md`): scoped description ("Use for changes under `web/` …"), told to
read that directory's `CLAUDE.md` first, to stay inside its directory except
for reading the schema/docs, and to report back what it changed and how it
verified it. These are what the root agent dispatches in parallel.

## 8. CI design (root `.github/workflows/`)

Per platform, split as today into verify (PRs) and deploy (main):

| Workflow | Trigger | `paths` | Notes |
|---|---|---|---|
| `backend-verify.yml` | pull_request | `backend/**`, workflow file | `defaults.run.working-directory: backend`; `cache-dependency-path: backend/go.sum`; golangci-lint `working-directory: backend`; 80% coverage gate kept |
| `backend-deploy.yml` | workflow_dispatch (push added in follow-up) | same | same build + Lambda update |
| `web-verify.yml` | pull_request | `web/**` | `cache-dependency-path: web/package-lock.json` |
| `web-deploy.yml` | workflow_dispatch (push later) | `web/**` | |
| `infra-verify.yml` | pull_request | `infra/**` | fmt, tflint, plan from `infra/terraform/...` |
| `infra-deploy.yml` | workflow_dispatch (push later) | `infra/**` | keep lock-cleanup step |
| `secrets-scan.yml` | pull_request (all paths) | — | gitleaks |

Web has no schema-based type checking, so CI cannot catch contract breaks
today; the root CLAUDE.md's protocol (backward-compatible schema changes) is
the guard. Schema-driven client codegen is a follow-up.

Existing CI is likely already broken (`golangci-lint-action@v3` with
`version: latest` pulls golangci-lint v2, which rejects the v1 `.golangci.yml`;
`gqlgen@latest` may need newer Go). Pinning versions or making the minimal
config change to get CI green is in scope; record each such change in the PR.

If branch protection requires checks, path-filtered workflows that don't run
would block PRs; handle with a single always-running "ci-gate" job or by only
requiring `secrets-scan` — builder's choice, documented in the workflow.

## 9. Mining the old claude.ai Projects

Separate phase; can run after the migration merges.
- Export is at `~/Documents/Claude/Export` (outside the repo).
  `conversations.json` and `conversations 2.json` are byte-identical; the
  ` 2` directories are duplicates. Use `projects/*.json` and
  `conversations.json` only.
- `projects/*.json` holds the five FlowEbb projects (FlowEbb, FlowEbb Go,
  Flowebb Webapp, FlowEbb Infra, FlowEbb Device), each with `prompt_template`
  (project instructions) and `docs` (synced files: 145/69/40/32/28).
- **Conversations carry no project ID**, so they're selected by keyword
  (flowebb, tide, NOAA, ESP32, NeoPixel, plus FlowEbb-specific topics) on
  title, summary and early messages. A first pass found ~144 candidates,
  2024-11-23 → 2025-01-31, including the Kotlin/Ktor era, harmonic-vs-NOAA
  tide calculation, caching, Terraform/CI, GraphQL migration, the ESP32 device,
  logo, and the repo split. The builder shows the owner the candidate list
  (titles + dates only) to confirm/remove before extraction.
- Builder groups the confirmed conversations by platform and dispatches one agent per
  platform (plus one for cross-cutting/early history) to extract decisions.
- Output: `docs/decisions/NNNN-<slug>.md`, each with: context, decision,
  alternatives rejected, platform(s) affected, source (project name +
  conversation date — no raw quotes of sensitive content). Plus
  `docs/decisions/README.md` index grouped by platform.
- Every record is reviewed for secrets/personal data before commit. Raw
  exports are never committed.
- `flowebb-orig` can be read the same way for history, but only decisions still
  true in the current system are recorded (or marked "superseded").
- Per-platform CLAUDE.md files are updated to link the relevant records.

## 10. Verification (definition of done)

- `git log --follow backend/go.mod` (and one file per platform) shows original
  commits.
- From each platform dir: lint and tests pass locally (backend with Docker;
  web `npm ci && npm run lint && npm test && npm run build`; infra `terraform
  fmt -check -recursive`, `tflint`, `terraform plan` = no changes; firmware
  `pio run` if PlatformIO is available, else noted as unverified).
- gitleaks clean on history and tree (or findings handled per §6.4).
- PR checks green; merged with a merge commit.
- Manual deploys succeed; `api.flowebb.com/graphql` and the website work.
- Local `~/src/flowebb` is the new clone with local config restored and ignored.
- Old repos: all Actions secrets deleted, described, archived (after owner
  confirmation); inactive IAM key deleted; repo set back to squash-merge.
- Memory file records status for future sessions.

## 11. Left to the builder

- Exact wording/structure of README, CLAUDE.md files, architecture doc (within §7).
- Whether to also add an `AGENTS.md` pointer for non-Claude tools.
- Handling of tags during import, and the ci-gate approach (§8).
- Upgrading `actions/checkout@v3` etc. only if required to pass.
- Order of fix-up commits may be adjusted if a step depends on another.

## 12. Follow-ups (not this effort)

- Re-enable push-to-main deploy triggers (small PR, right after cutover).
- Trim OIDC `github_repos` to `["flowebb"]`.
- Pin `gqlgen` version in CI instead of `@latest` (source of the generated-header
  churn seen locally).
- Enforce web test coverage in CI (and decide the threshold).
- Schema-driven GraphQL codegen for web (catches contract breaks in CI).
- Firmware CI (`pio run`) and an `apps/ios`, `apps/watchos` scaffold when started.
