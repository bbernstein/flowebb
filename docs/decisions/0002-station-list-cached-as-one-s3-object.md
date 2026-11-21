# 0002: Cache the full NOAA station list as one S3 object

**Status:** Accepted

**Date:** 2024-12-13

## Context

The full NOAA station list is large. The first serverless version split it
across many DynamoDB items of about 100 stations each. That was awkward to read
back and buggy: at one point only the first cached result was remembered, and
the rest of the stations were never searched.

## Decision

- Cache the whole station list as one JSON object in S3, with an in-process
  memory cache in front of it. The record carries a last-updated time. When it
  expires, the list is refetched from NOAA and written back to S3 off the
  request path.
- Nearest-station search always runs over the full cached list. Only the list
  is cached, never per-latitude/longitude results.
- Code: `backend/internal/cache/s3_station_cache.go`,
  `backend/internal/cache/stations.go`, `backend/internal/station/noaa.go`.
- The TTL is `CACHE_STATION_LIST_TTL_DAYS` (see
  [0018](0018-cache-sizes-and-ttls-from-env.md)).

This is one layer of the backend cache. Predictions are cached separately in
DynamoDB ([0009](0009-prediction-cache-per-station-day.md)).

## Alternatives rejected

- DynamoDB items of 100 stations each: clumsy, many reads, and error-prone.
- A single DynamoDB item: the list exceeds the item size limit.
- Caching results per exact latitude/longitude: an early version returned the
  first cached answer for every location.
- Memory-only caching: the full list would be refetched from NOAA on every
  Lambda cold start.

## Platforms

backend, infra

## Source

Summarized claude.ai project history: "Caching issue with /api/stations
endpoint" and "Caching Full Station List for Efficient Lookups" (2024-12-12),
"Efficient Storage of Large Data Sets in AWS" and "Efficient DynamoDB Storage
for Large Station Lists" (2024-12-13), "Caching Station List in S3 for Go
Backend" (2025-01-08).
