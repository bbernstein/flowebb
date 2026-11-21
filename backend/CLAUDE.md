# backend/: Go GraphQL API

Read the root `CLAUDE.md` first. Run every command below from `backend/`.

## Stack

- Go 1.23.4 (from `go.mod`). Module path: `github.com/bbernstein/flowebb/backend`.
- gqlgen v0.17.64 for the GraphQL server. The schema is `graph/schema.graphql`.
- AWS Lambda (arm64, custom-runtime `bootstrap` binary), function `flowebb-graphql-prod`.
- Caches: LRU in memory, DynamoDB (tide predictions), S3 (station list).
- Data source: the NOAA tides and currents API.

## Directory map

| Path | Contents |
|---|---|
| `cmd/graphql/` | Lambda entry point (the deployed binary) |
| `cmd/stations/`, `cmd/tides/` | standalone handlers (not deployed by CI) |
| `graph/schema.graphql` | **the API contract for every client** |
| `graph/schema.resolvers.go`, `graph/resolver.go` | resolvers (hand-written) |
| `graph/generated/`, `graph/model/` | gqlgen output: regenerate, never hand-edit |
| `internal/tide/` | tide prediction and extremes logic |
| `internal/station/` | NOAA station client |
| `internal/cache/` | LRU, DynamoDB, and S3 caches |
| `internal/config/` | env-driven config (`CACHE_*` variables) |
| `internal/models/`, `internal/api/`, `internal/handler/`, `pkg/http/` | models, handlers, HTTP client |
| `scripts/` | local helpers (DynamoDB init, local run) |
| `docker-compose.yml` | DynamoDB Local + admin UI for local dev |

## Commands

```bash
# One-time: install gqlgen. `go run github.com/99designs/gqlgen` does NOT work here.
go install github.com/99designs/gqlgen@v0.17.64

# Regenerate after any schema.graphql or gqlgen.yml change (and on a fresh clone)
rm -rf graph/generated graph/model && gqlgen generate

# Lint (golangci-lint v1; .golangci.yml is a v1 config, so v2 will reject it)
go run github.com/golangci/golangci-lint/cmd/golangci-lint@v1.64.8 run --timeout=5m

# Test
go test -race ./...

# Coverage (same as CI's backend-test job; must be >= 80%)
mkdir -p test-results
go test -covermode=atomic -coverpkg="$(go list ./... | grep -v graph/generated | grep -v graph/model | tr '\n' ',')" ./... -coverprofile=test-results/coverage.out
go tool cover -func=test-results/coverage.out | grep total

# Build the Lambda binary (what deploy-backend.yml does)
GOOS=linux GOARCH=arm64 CGO_ENABLED=0 go build -o bootstrap ./cmd/graphql

# Optional local DynamoDB for dev (tests do NOT need it)
docker network create sam-network   # once; the compose file expects this external network
docker compose up -d
```

Deploy: `.github/workflows/deploy-backend.yml` runs on push to `main` when
`backend/**` or its own workflow file changes, and can also be dispatched manually from `main`. Only
dispatch it with the owner's go-ahead. Do not deploy from your machine.

## Rules

- Coverage: CI enforces at least 80% total. New code needs unit tests that cover it.
- `generated.go` and `*_gen.go` are gitignored and rebuilt by `gqlgen generate`.
  Never commit them or hand-edit them.
- Schema changes must be backward compatible (see "The API contract" in the
  root `CLAUDE.md`). Firmware reads `tides { localTime waterLevel tideType
  timeZoneOffsetSeconds extremes { type timestamp height } }`. The web app
  reads those plus `nearestStation`, `predictions`, and `stations { ... }`.
  Search `web/src/` and `firmware/src/` before touching any field.
- Tests must not call the real NOAA API or AWS. Use the existing interfaces and mocks.
- The module path is `github.com/bbernstein/flowebb/backend`. Use it for every
  internal import.

## Never commit secrets or private data

The root rule applies in full. Here: never commit `docker/` (DynamoDB Local
data), `.env*`, AWS credentials, or `test-results/` output that contains real data.

## Decisions

Check [`docs/decisions/`](../docs/decisions/README.md) before changing behavior here.

Key decisions:

- [0025: Serve the API as one GraphQL Lambda built with gqlgen](../docs/decisions/0025-graphql-api-with-gqlgen.md)
- [0009: Cache predictions per station-day in DynamoDB behind an in-memory LRU](../docs/decisions/0009-prediction-cache-per-station-day.md)
- [0002: Cache the full NOAA station list as one S3 object](../docs/decisions/0002-station-list-cached-as-one-s3-object.md)
- [0018: Cache sizes and TTLs come from CACHE_* environment variables](../docs/decisions/0018-cache-sizes-and-ttls-from-env.md)
- [0015: The backend handles all tide times in the station's local zone](../docs/decisions/0015-backend-times-in-station-local-zone.md)
- [0008: Use NOAA's predictions, not our own harmonic tide calculation](../docs/decisions/0008-noaa-predictions-not-harmonic-calculation.md)
- [0020: NOAA failures become typed errors, and must not pass as empty data](../docs/decisions/0020-noaa-errors-typed-and-502.md)
- [0028: Backend tests: 80% coverage without generated code, injected dependencies, no real NOAA or AWS](../docs/decisions/0028-backend-testing-and-coverage.md)
