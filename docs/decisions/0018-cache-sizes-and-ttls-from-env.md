# 0018: Cache sizes and TTLs come from CACHE_* environment variables

**Status:** Accepted

**Date:** 2025-01-01

## Context

Cache lifetimes were scattered: a hard-coded 7-day DynamoDB validity and a
15-minute LRU default in code, plus a TTL attribute enabled on the DynamoDB
tables in Terraform. The owner wanted one place to tune every cache parameter.

## Decision

- One `CacheConfig` in `backend/internal/config/cache.go` reads `CACHE_*`
  variables, with code defaults: tide LRU 1000 entries and 15 minutes
  (`CACHE_TIDE_LRU_TTL_MINUTES`), DynamoDB prediction TTL 2 days
  (`CACHE_DYNAMO_TTL_DAYS`), station list TTL 2 days
  (`CACHE_STATION_LIST_TTL_DAYS`), GraphQL response LRU 5000 entries and 60
  minutes, batch size 25 with 3 retries. The LRU and DynamoDB layers can each be
  turned off.
- `backend/template.yaml` overrides some of these for local runs (shorter TTLs).
- The config is built where services are constructed and passed in, not read
  from a hidden global. Tests must construct it explicitly, or they hit
  nil-pointer panics.

**Note:** `backend/internal/cache/dynamo_prediction_cache.go` still has a
`cacheValidityDays = 7` constant used when saving a single record, while batch
saves use the configured TTL. Check which path applies before changing TTLs.

## Alternatives rejected

- Hard-coded constants per cache: cannot be tuned without a rebuild.
- A single long (7-day) DynamoDB TTL: the original behavior, later shortened to
  a 2-day default.

## Platforms

backend, infra

## Source

Summarized claude.ai project history: "Setting TTL for DynamoDB and LRU
Caches" (2025-01-01).
