# 0009: Cache predictions per station-day in DynamoDB behind an in-memory LRU

**Status:** Accepted

**Date:** 2024-12-20

## Context

Every tide request called NOAA, and an early version refetched the whole day
for each data point. The stack is serverless with sporadic traffic, so a cache
had to cost nothing when idle and survive Lambda cold starts. The first Go
rewrite cached only in process memory, which is lost whenever a container
stops. NOAA predictions for a given day stay valid for a long time.

## Decision

- Layers, behind a cache service the tide service uses without knowing the
  details: in-memory LRU, then DynamoDB, then NOAA. Results fetched from NOAA
  are written back to both layers, with DynamoDB writes batched and done off
  the request path.
- DynamoDB holds one record per station per calendar day (partition key station
  id, sort key date), containing that day's 6-minute predictions and extremes,
  with a DynamoDB TTL attribute. "Day" means a day in the station's time zone
  ([0015](0015-backend-times-in-station-local-zone.md)).
- A request reads every day in its range from the cache, merges them, and
  filters to the requested window. For missing days it makes one NOAA call per
  data type (predictions, extremes) covering the span from the first to the
  last missing day, even if that refetches some cached days. One contiguous call
  is preferred over one call per gap. See `getPredictionsForDateRange` in
  `backend/internal/tide/service.go`.
- Requests are limited to 30 days (an invalid-range error beyond that).
- A separate GraphQL response LRU (`backend/internal/cache/graphql_cache.go`)
  sits in front of the resolvers.
- Code: `backend/internal/cache/lru_cache.go`,
  `backend/internal/cache/dynamo_prediction_cache.go`,
  `backend/internal/models/dynamo_prediction_cache.go`.
- Sizes and TTLs come from `CACHE_*` variables
  ([0018](0018-cache-sizes-and-ttls-from-env.md)). The station list is cached
  in S3, not DynamoDB ([0002](0002-station-list-cached-as-one-s3-object.md)).

## Alternatives rejected

- Redis or ElastiCache: a base running cost that does not fit sporadic use.
- S3 for predictions: higher latency, no built-in TTL, awkward for per-day
  lookups.
- Lambda memory only: not shared between instances and lost on cold start. It
  is kept only as the front layer.
- Keying records by the start date of the requested range: ranges differ
  between requests, so entries rarely hit.
- One NOAA call per missing gap, or one per missing day (the Kotlin version,
  later parallelized with coroutines): more API calls. The owner chose fewer
  calls over minimal data transfer.
- Blocking the response on cache writes: adds latency for no user benefit.

## Platforms

backend, infra

## Source

Summarized claude.ai project history: "Efficient Caching of NOAA API Data"
(2024-12-20), "Parallelizing NOAA API Calls in Tide Level Endpoint"
(2024-12-24), "Optimizing Tide Prediction Caching in Go Backend" (2024-12-31),
"Implementing Memory-Based Caching with LRU" (2025-01-01).
