# 0038: Backend fetches NOAA in GMT and uses a lat/lon-derived IANA zone

**Status:** Accepted

**Date:** 2026-11-27

## Context

Issue #9. The backend requested NOAA data with `time_zone=lst_ldt` but parsed
each time with a fixed offset built from NOAA's whole-hour standard-time
correction ([0015](0015-backend-times-in-station-local-zone.md)). `lst_ldt`
includes daylight saving, so while DST was in effect (about 8 months a year)
every timestamp was one hour late. Verified against the live API: Boston
8443970 on 2026-07-01 has a high tide at `00:38` with `lst_ldt` and `04:38`
with `gmt`, so the real offset was -4 h, but the backend parsed `00:38` at -5 h
and got 05:38Z. A fixed offset also cannot represent transition days, and
`Truncate(24*time.Hour)` cut days on UTC boundaries, not station-local ones.

## Decision

- Request NOAA with `time_zone=gmt` and parse as UTC, so instants are correct
  on every day, including transition days. `begin_date` and `end_date` carry
  a time of day (`yyyyMMdd HH:mm`), set to the station-local day boundaries
  converted to UTC.
- Each station gets a DST-aware IANA `*time.Location`, found from its lat/lon
  with the embedded boundary library `github.com/ringsaturn/tzf`
  (`backend/internal/timezone`). Day grouping, cache day keys, and local-time
  strings use that location.
- Fallback: if the lookup fails, returns an `Etc/*` zone, or its standard
  offset disagrees with NOAA's `timeZoneCorr`, the station gets a fixed zone
  at NOAA's offset (the old behavior). Of the 3462 NOAA stations checked, 80
  (about 2.3%) fall back. Causes: non-US stations where NOAA's correction is a
  placeholder rather than a real local offset, and a few US border stations
  that NOAA lists on a neighboring zone (Florida panhandle, Aleutians, parts
  of Alaska). Those still lack DST.
- `timeZoneOffsetSeconds` and `Station.timeZoneOffset` are the offset in
  effect at request time. Names, types, and nullability are unchanged.
- Lookups are memoized per coordinate, and `timezone.Warm()` builds the finder
  at Lambda init so the first request does not pay for it.
- `time/tzdata` is embedded, so the Lambda does not depend on OS zoneinfo.
- tzf requires a newer Go, so `backend/go.mod` moved from Go 1.23.4 to 1.25.0.
  The linux/arm64 `bootstrap` binary grew from 20.07 MB to 31.75 MB (+11.7 MB,
  +58%); gzipped, from 9.2 MB to 14.4 MB. The boundary data in tzf is most of it.

## Alternatives rejected

- Map NOAA's `timeZoneCorr` plus state or region to an IANA zone by hand: no
  dependency, but a hand-kept table with edge cases (Cuba, Mexico, Bermuda).
- Ask the NOAA per-station endpoint for `observedst`, then map to a US-rules
  zone: uses NOAA's own flag, but costs an extra NOAA call per new station.
- Keep `time_zone=lst`: instants are correct with the fixed offset, but
  local-time strings and day boundaries still need the station's DST rules,
  so a per-station zone is needed anyway.
- Keep `lst_ldt` and fix the parsing: the instants are only recoverable if
  the DST state of each time is known, which is the problem to be solved.

## Consequences

- Cache entries written by the old code hold shifted times. They clear by TTL
  (DynamoDB `CACHE_DYNAMO_TTL_DAYS`, default 2). Deploy is backend only.
- One offset per response is still approximate for a multi-day range that
  crosses a DST change. Local-time strings are exact. If clients need more, add
  an optional `timeZone` (IANA name) field later; that is backward compatible.
- Stations that fall back to a fixed offset keep the old DST error.
- The tzf finder holds about 45 MB of heap, so check Lambda memory headroom at
  deploy.
- CI: the golangci-lint step uses `install-mode: goinstall`, so lint is built
  with the Go toolchain that setup-go reads from `go.mod`. The prebuilt
  v1.64.8 binary is built with Go 1.24 and may refuse a module targeting 1.25.

## Platforms

backend (constrains web and firmware through the API)

## Source

Issue #9; investigation and owner decision in
`docs/plans/issue-9-dst-investigation.md`.
