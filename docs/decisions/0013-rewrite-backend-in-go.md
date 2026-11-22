# 0013: Rewrite the backend from Kotlin/Ktor on the JVM to Go

**Status:** Accepted

**Date:** 2024-12-30

## Context

The backend started in November 2024 as a Ktor app, then became Java 11 Lambda
functions (tides and stations) behind API Gateway, with DynamoDB caching and
NOAA as the data source. On Lambda the JVM had slow cold starts and high memory
use.

## Decision

- Rewrite in Go, one Lambda at a time (stations first), keeping the Kotlin
  version until the Go one was validated.
- Go Lambdas run on the `provided.al2` custom runtime with a `bootstrap`
  handler on arm64 (`infra/terraform/modules/compute/main.tf`). The separate
  tides and stations functions were later replaced by one GraphQL Lambda
  ([0025](0025-graphql-api-with-gqlgen.md)).
- Standard Go layout: `cmd/` for Lambda entry points, `internal/` for private
  packages, `pkg/` for shared ones (for example `pkg/http/client`), and no
  `src/` directory. Tests are table-driven, with `httptest` servers standing in
  for NOAA.
- The module path must match where the code is hosted. It changed when the code
  moved to its own repo (2025-01-30) and again on the return to the monorepo:
  it is now `github.com/bbernstein/flowebb/backend` (`backend/go.mod`). Any
  future move means rewriting every import.

**Note:** the gains quoted at the time (cold start from about 2s to 100ms,
memory from 512MB to 64MB) were assistant estimates, not measurements.

## Alternatives rejected

- Stay on Kotlin/Ktor on the JVM: cold-start and memory cost on Lambda.
- A big-bang rewrite: rejected in favor of migrating one function at a time and
  comparing results.
- A `src/` directory, or keeping the old module path after a move: not
  idiomatic, and imports would not resolve.

## Platforms

backend, infra

## Source

Summarized claude.ai project history: "Migrating Java Backend to Go for
Improved Performance" (2024-12-30), "Typical Go Application Directory
Structure" (2025-01-30).
