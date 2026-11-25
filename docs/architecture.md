# Architecture

## Components

| Component | Directory | Runs on |
|---|---|---|
| GraphQL API | `backend/` | AWS Lambda (Go, arm64) behind API Gateway at `https://api.flowebb.com/graphql` |
| Web app | `web/` | Static Next.js export in S3, served by CloudFront |
| Landing page | `www/` | Static HTML in S3, own CloudFront distribution, `www.flowebb.com` (apex redirects) |
| Tide LED device | `firmware/` | ESP32-S3 with one NeoPixel LED |
| Infrastructure | `infra/` | Terraform, with remote state in S3 (`flowebb-terraform-state-bucket`) and lock table `terraform-state-lock` |
| CI/CD | `.github/workflows/` | GitHub Actions (`verify.yml` on PRs; `deploy-*.yml` on push to `main` or manual dispatch) |
| Future clients | `apps/ios/`, `apps/watchos/` | not created yet (see `adding-a-platform.md`) |

## Data flow

```
NOAA Tides & Currents API
        │  (station list, tide predictions)
        ▼
Go Lambda (backend/)  ── GraphQL at api.flowebb.com/graphql
  ├─ in-memory LRU cache
  ├─ DynamoDB: tide prediction cache (TTL)
  └─ S3: station list cache
        │
        ├──────────────► Web app (web/): static site on S3 + CloudFront,
        │                 browser calls the API with Apollo Client
        │
        ├──────────────► Landing page (www/): static HTML on its own S3 +
        │                 CloudFront at www.flowebb.com; no API calls, it only
        │                 links to the web app
        │
        └──────────────► ESP32 device (firmware/): wakes, polls the same
                          endpoint over Wi-Fi, sets LED color, deep-sleeps
```

The API exposes two queries: `stations(lat, lon, limit)` and
`tides(stationId, startDateTime, endDateTime)`. Responses include water level,
tide type (rising/falling), predictions, and high/low extremes.

## The API contract

`backend/graph/schema.graphql` is the single contract for every client. No
client generates code from it, so contract breaks are not caught by CI.
Changes must be backward compatible because deployed web builds and devices
in the field update on their own schedules. See the root `CLAUDE.md`.

## Infrastructure and deploys

- Terraform in `infra/` owns every AWS resource: Lambda, API Gateway, DynamoDB,
  S3 buckets, CloudFront, Route53, IAM, and the GitHub OIDC provider.
- CI authenticates to AWS through GitHub OIDC by assuming the role
  `flowebb-github-actions-prod`. No long-lived AWS keys are stored in GitHub.
  The repos allowed to assume it are listed in the `github_repos` variable and
  `github_oidc_extra_subjects` in `infra/terraform/environments/prod/main.tf`
  (`bbernstein/flowebb` uses the ID-based subject in the latter; never remove it).
- Each deploy workflow runs on push to `main`, filtered to its own platform
  directory and workflow file, and can also be dispatched manually from `main`
  (`workflow_dispatch`):
  - `deploy-infra.yml` runs `terraform apply`.
  - `deploy-backend.yml` builds `cmd/graphql` and updates Lambda `flowebb-graphql-prod`.
  - `deploy-web.yml` runs `web/scripts/deploy-frontend.sh prod` (S3 sync + CloudFront invalidation).
  - `deploy-www.yml` runs `www/scripts/deploy.sh` (S3 sync + CloudFront invalidation for the landing page).
- Deploy order for a cross-platform change: infra → backend → web/apps → firmware.
