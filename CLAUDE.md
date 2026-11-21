# FlowEbb

FlowEbb is a tide-tracking platform. A Go GraphQL API on AWS Lambda serves NOAA
tide predictions and stations at `https://api.flowebb.com/graphql`. A Next.js
static site and an ESP32 LED device consume it. Terraform manages all AWS
resources. See `docs/architecture.md` for components and data flow.

## Never commit secrets or private data

This repo is public. Everything committed is visible to everyone, forever.

Never commit:
- tokens, passwords, API keys, private keys (`*.pem`, `id_rsa*`)
- AWS credentials, or AWS account details beyond what is already public
- Wi-Fi credentials (`firmware/src/config/wifi_credentials.h`)
- `.env*` files (except `*.example`)
- `terraform.tfvars`, Terraform state (`*.tfstate*`), `.terraform/`
- raw claude.ai export content or chat transcripts (`conversations*.json`)
- customer or user data, and personal info: names, emails, phone numbers, addresses

Never print secret values to the terminal, logs, files, or chat.

For config, commit a template (`*.example`, `*.template`) with placeholder
values, and keep the real file gitignored. If a value you need is sensitive,
stop and ask the owner. Do not guess, invent, or inline it.

`bash scripts/check-repo-rules.sh` (also run in CI) fails if a known sensitive
file is tracked. `gitleaks` runs in CI on every PR. Neither replaces judgment.

## Routing: which platform does a request touch?

| Request area | Directory | Stack | Rules |
|---|---|---|---|
| UI, pages, charts, maps, styling | `web/` | Next.js 15, React 18, Apollo, MUI, Leaflet, Highcharts | `web/CLAUDE.md` |
| API, GraphQL schema, tide/station data, NOAA client, caching, Lambda code | `backend/` | Go 1.23, gqlgen, AWS Lambda (arm64), DynamoDB, S3 | `backend/CLAUDE.md` |
| AWS resources, DNS, CloudFront, IAM, Lambda config, CI deploy roles | `infra/` | Terraform 1.5 | `infra/CLAUDE.md` |
| ESP32 device, LED behavior, Wi-Fi, on-device tide logic | `firmware/` | ESP32-S3, PlatformIO/Arduino, NeoPixel | `firmware/CLAUDE.md` |
| CI workflows, repo rules, docs | `.github/`, `scripts/`, `docs/` | GitHub Actions, bash | this file |
| Future: iPhone, Apple Watch | `apps/ios/`, `apps/watchos/` (not created yet) | Swift | see `docs/adding-a-platform.md` |

A request that needs new or changed data in a client (web, firmware, apps)
is also a `backend/` change. Read the target platform's `CLAUDE.md` before
editing it.

## The API contract

`backend/graph/schema.graphql` is the source of truth for every client.
Changing it is a cross-platform change.

Make backward-compatible changes only: add fields, types, and optional
arguments. Do not remove, rename, or change the type or nullability of
an existing field or argument. Devices in the field and the deployed web
site cannot update in lockstep with the backend. To retire a field, mark it
`@deprecated`, migrate every client, and remove it in a later PR only
after the owner confirms that no deployed client still reads it.

No client uses schema-based codegen, so CI cannot catch a contract break.
Before changing a field, search for it in `web/src/` and `firmware/src/`.

## Cross-platform change protocol

1. Identify every affected platform from the routing table.
2. Change the contract first: edit `backend/graph/schema.graphql` and keep
   it backward compatible.
3. Dispatch one agent per affected platform **in parallel** using the
   definitions in `.claude/agents/` (`backend`, `web`, `infra`, `firmware`).
   Give each one the agreed schema change and the exact task for its
   platform. Each works only inside its own directory.
4. Collect their reports. Check that each ran its platform's lint and tests.
5. Ship one branch and one PR for the whole feature.
6. Deploy order: infra → backend → web/apps. Firmware goes last, and the
   backend must never require a firmware update to work.

## Git rules

- Never commit to `main`. Work on a branch and open a PR.
- PRs are squash-merged. (The one-time history import was the only
  merge-commit exception.)
- Before committing, run lint and tests for every platform you changed.
- Every code change needs unit tests that cover it.
- If a change makes an existing test fail, ask the owner before changing
  the test.
- Write a descriptive commit message stating what changed and why.

## Commands quick reference

Run each from its platform directory. Full details are in each platform's
`CLAUDE.md`.

| Platform | Install / generate | Lint | Test | Build / run |
|---|---|---|---|---|
| `backend/` | `go install github.com/99designs/gqlgen@v0.17.64`, then `rm -rf graph/generated graph/model && gqlgen generate` | `go run github.com/golangci/golangci-lint/cmd/golangci-lint@v1.64.8 run --timeout=5m` | `go test -race ./...` (≥80% coverage enforced in CI) | `go build ./...` |
| `web/` | `npm ci` | `npm run lint` | `npm test` | `npm run dev` (port 3000), `npm run build` |
| `infra/terraform/` | `tflint --init` | `terraform fmt -check -recursive`, `tflint --recursive` | `terraform plan` from `environments/prod/` (read-only) | apply only via `deploy-infra.yml` |
| `firmware/` | `cp src/config/wifi_credentials.h.template src/config/wifi_credentials.h` | none yet | none yet | `pio run`, `pio run -t upload`, `pio device monitor` |
| repo root | | | `bash scripts/check-repo-rules.test.sh` | `bash scripts/check-repo-rules.sh` |

CI is `.github/workflows/verify.yml`. It runs path-filtered jobs per
platform, and `ci-gate` is the required check. Deploy workflows
(`deploy-backend.yml`, `deploy-web.yml`, `deploy-infra.yml`) are manual. Never
dispatch one without the owner's go-ahead.

## Decisions

Check `docs/decisions/` before changing behavior in an area. Add a record
there when you make a non-obvious choice.

## Adding a platform

Follow `docs/adding-a-platform.md`.
