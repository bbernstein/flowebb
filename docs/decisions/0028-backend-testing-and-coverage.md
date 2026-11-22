# 0028: Backend tests: 80% coverage without generated code, injected dependencies, no real NOAA or AWS

**Status:** Accepted

**Date:** 2025-01-26

## Context

In January 2025 the Go backend gained broad unit tests (cache, models, tide
service, station finder, GraphQL handler, Lambda main). Generated gqlgen code
distorted coverage, and logic in `main()` and `init()` resisted testing.

## Decision

- Total coverage must be at least 80%, measured with `-coverpkg` over every
  package except `graph/generated` and `graph/model`. Generated files cannot
  carry ignore markers because they are regenerated. CI enforces this in the
  `backend-test` job; the command is in `backend/CLAUDE.md`.
- Hard-to-test code was refactored for injection: service and station-finder
  factories, an `InitializeService` function instead of logic in `init()`, and
  a shared `models.StationFinder` interface that breaks an import cycle between
  the station and tide packages.
- Tests use mocks and local mock HTTP servers. They never call the real NOAA
  API or AWS, and do not need DynamoDB Local.
- Tests that store several records in a cache assert that each one comes back
  with its own data. (An early batch-save bug stored the address of a reused
  loop variable. Go 1.22+ loop semantics prevent that form, but the test
  pattern stays.)
- When a test fails after a code change, decide whether the code or the test is
  wrong. Do not edit the test just to make it pass.

## Alternatives rejected

- Counting generated code toward coverage: dilutes the number and cannot be
  annotated.
- Logic inside `init()`: cannot be mocked, and its error paths cannot be tested.

## Platforms

backend

## Source

Summarized claude.ai project history: "Implementing Unit Tests for Cache
Service" (2025-01-02), "Debugging LRU Cache Batch Saving Issue" (2025-01-01),
"Improving Testability of GraphQL Main Functions" (2025-01-25), "Resolving
GraphQL Code Generation Errors" (2025-01-26).
