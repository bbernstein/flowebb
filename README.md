# FlowEbb

FlowEbb is a tide-tracking platform. A GraphQL API serves NOAA tide
predictions and station data. A web app charts and maps them, and a small
ESP32 device shows whether the tide is rising or falling with a single LED.

- API: `https://api.flowebb.com/graphql` (contract: [`backend/graph/schema.graphql`](backend/graph/schema.graphql))
- Architecture: [`docs/architecture.md`](docs/architecture.md)
- Rules for contributors and AI agents: [`CLAUDE.md`](CLAUDE.md)

## Layout

| Directory | What | Stack |
|---|---|---|
| [`backend/`](backend/README.md) | GraphQL API on AWS Lambda | Go, gqlgen, DynamoDB, S3 |
| [`web/`](web/README.md) | Static web app on S3 + CloudFront | Next.js, React, Apollo, MUI |
| [`infra/`](infra/README.md) | All AWS resources | Terraform |
| [`firmware/`](firmware/README.md) | Tide LED device | ESP32-S3, PlatformIO, NeoPixel |
| `docs/` | Architecture, decisions, how to add a platform | |
| `.github/workflows/` | CI (`verify.yml`) and manual deploys | GitHub Actions |

iOS and watchOS apps will live under `apps/` (see [`docs/adding-a-platform.md`](docs/adding-a-platform.md)).

## Quick start

```bash
git clone https://github.com/bbernstein/flowebb
cd flowebb
```

Then follow the README of the platform you are working on:

- **Backend:** `cd backend`, run `go tool gqlgen generate`, then `go test ./...`. See [`backend/README.md`](backend/README.md) and [`backend/CLAUDE.md`](backend/CLAUDE.md).
- **Web:** `cd web && npm ci && npm run dev`. See [`web/README.md`](web/README.md).
- **Infra:** `cd infra/terraform/environments/prod && terraform init && terraform plan`. See [`infra/README.md`](infra/README.md).
- **Firmware:** `cd firmware`, create `wifi_credentials.h` from the template, then `pio run -t upload`. See [`firmware/README.md`](firmware/README.md).

Open `flowebb.code-workspace` in VS Code for one folder per platform.

## History

Each platform directory was imported from the original `flowebb-go`,
`flowebb-web`, `flowebb-infra`, and `flowebb-esp` repos (to be archived) with full history, so
`git log` and `git blame` work on every file.

