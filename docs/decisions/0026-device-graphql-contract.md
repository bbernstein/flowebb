# 0026: The device is a thin GraphQL client and depends on fixed tide fields

**Status:** Accepted

**Date:** 2025-01-22

## Context

The API moved to GraphQL in this period
([0025](0025-graphql-api-with-gqlgen.md)). Devices are flashed by hand and
updated rarely, so they cannot follow API changes.

## Decision

- `firmware/src/services/TideService.cpp` POSTs a `GetTides` query to
  `TIDE_API_ENDPOINT` (set through the credentials template) with `stationId`,
  `startDateTime` and `endDateTime` (local-time ISO strings, a window of about
  12 hours).
- It reads `localTime`, `waterLevel`, `tideType`, `timeZoneOffsetSeconds`, and
  `extremes { type timestamp height }` (timestamps in milliseconds).
- Removing or renaming any of these breaks deployed devices. Additive changes
  are safe. The LED logic needs `extremes` to compute progress between the last
  and next extreme ([0031](0031-led-color-semantics.md)).
- The server owns tide prediction. The device does no tide computation.

## Alternatives rejected

- Computing tides on the device, or keeping the older REST endpoint: not
  pursued; the device stays a thin client.

## Platforms

firmware, backend

## Source

Summarized claude.ai project history: "Migrating API to GraphQL: Frontend and
Backend Changes" (2025-01-22).
