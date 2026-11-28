# Issue #9: station time handling during DST and half-hour zones

Status: investigation complete (2026-11-27). Owner chose option A (lat/lon
lookup) and no schema change. Implementation plan is at the end of this file.

## Findings (verified against the live NOAA API)

1. **DST bug confirmed.** `time_zone=lst_ldt` returns daylight time in summer.
   Boston 8443970 on 2026-07-01: high tide `00:38` with `lst_ldt`, `04:38` with
   `gmt`, so the real offset is -4 h. The backend parses `00:38` with
   `time.FixedZone(-5 h)` (from `timeZoneCorr`), which gives 05:38Z. **Every
   timestamp is one hour late while DST is in effect** (about 8 months a year,
   at most US stations).
2. **Impact.**
   - Firmware uses the UTC `timestamp` against NTP time, so the LED runs one
     hour behind the real tide during DST.
   - Web shifts `timestamp` by `timeZoneOffsetSeconds` (-5 h). Extreme and
     curve labels look right (the two errors cancel), but the "now" marker,
     current level and rising/falling are computed one hour off.
   - `localTime` strings come out right, because they are formatted back in
     the same wrong fixed zone.
3. **Half-hour zones:** none exist. All 3,462 NOAA prediction stations have a
   whole-hour `timeZoneCorr` (-11 to +13). Not a real risk.
4. **Transition days:** NOAA `lst_ldt` skips 02:00–02:54 on spring-forward
   (230 points) and returns only 240 points on fall-back, so one of the two
   01:xx hours is lost. `gmt` always returns 240 unambiguous points.
5. **`Truncate(24*time.Hour)`** in `GetCurrentTideForStation` truncates on
   UTC midnight. For the extremes path it still always includes the previous
   local day, so it over-fetches but does not drop data. It is sloppy, not
   broken. Replace it with local-date arithmetic during the fix.
6. **DST source data:** the bulk NOAA lists (`tidepredstations.json`,
   `stations.json?type=tidepredictions`) carry only `timezonecorr`. Only the
   per-station endpoint (`/mdapi/prod/webapi/stations/{id}.json`) has
   `observedst`.
7. **Cache:** wrong timestamps already sit in DynamoDB/LRU. Dynamo TTL is
   2 days (`CACHE_DYNAMO_TTL_DAYS`), so bad entries expire soon after deploy.
   Bumping the cache key also works.

## Fix outline (common to every option)

- Request NOAA with `time_zone=gmt` and parse as UTC, so instants are always
  correct, including transition days.
- Give each station a real `*time.Location` (IANA, DST-aware). Use it for
  `localTime` strings, day grouping and cache day keys, and for
  `timeZoneOffsetSeconds` (offset at "now").
- Embed tzdata (`import _ "time/tzdata"`) so the Lambda does not depend on
  OS zoneinfo.
- Tests: a DST summer date, both transition days, a non-DST station (Hawaii),
  and a positive-offset station.
- Schema: no breaking change. Optional additive `timeZone: String` (IANA name)
  for later client use.

## Open decision: how to get each station's zone

- A. Look up lat/lon to IANA with an embedded boundary library
  (e.g. `github.com/ringsaturn/tzf`). Most accurate, adds a dependency and a
  few MB to the binary.
- B. Map `timeZoneCorr` plus state/region to IANA (US states use US rules;
  HI and the Pacific islands are fixed). No dependency, but a hand-kept
  table with edge cases (Cuba, Mexico, Bermuda).
- C. Call the NOAA per-station endpoint for `observedst` (cached), then map
  corr + DST to a US-rules zone. Uses NOAA's own flag, but one extra NOAA
  call per new station.

## Decision (owner, 2026-11-27)

- Option A: lat/lon to IANA via an embedded boundary library.
- No schema change in this PR. Existing fields keep their names and types.
  `timeZoneOffsetSeconds` and `Station.timeZoneOffset` now carry the
  DST-aware offset in effect at request time.

## Implementation plan (branch `fix/9-station-dst-time`)

### Task 1: `backend/internal/timezone` package

- `ForStation(lat, lon float64, stdOffsetSeconds int) *time.Location`
  returns the station's IANA zone.
- Use `github.com/ringsaturn/tzf`, with the finder built once (lazy
  `sync.Once`; it is costly). Pick the lite or default data and report the
  binary size change.
- Safety fallback to `time.FixedZone(stdOffsetSeconds)` when the lookup
  returns no zone, returns an `Etc/*` ocean zone, or returns a zone whose
  standard offset (min of the 1 Jan / 1 Jul offsets) differs from NOAA's
  `timeZoneCorr`. NOAA's correction stays authoritative for the standard
  offset.
- `import _ "time/tzdata"` so the Lambda does not need OS zoneinfo.
- Unit tests: Boston (America/New_York), San Francisco, Honolulu (no DST),
  Guam (+10), a far-offshore point (fallback), a mismatched correction
  (fallback).
- One-off check, not committed: run every station in NOAA
  `tidepredstations.json` through it and report counts per zone, plus how
  many fell back and why.

### Task 2: tide service uses real instants and the station zone

`backend/internal/tide/service.go`:

- Location = `timezone.ForStation(...)` instead of `time.FixedZone`.
- NOAA requests use `time_zone=gmt`, with `begin_date` and `end_date` as
  time-qualified GMT bounds covering the local days
  (`yyyyMMdd HH:mm`, space URL-encoded: verified to work). Start = first
  local midnight in UTC, end = (last local day + 1) midnight in UTC minus
  1 minute. Parse times as UTC.
- Group by local day in the station zone. This already happens; keep it.
- Replace both `Truncate(24*time.Hour)` calls with local-date arithmetic
  (`time.Date(y, m, d, 0, 0, 0, 0, loc)`).
- `TimeZoneOffsetSeconds` = offset of `now` in the station zone.
- Tests (fixed clock, mock NOAA in GMT): a DST summer day (Boston, 00:38 EDT
  high gives timestamp 04:38Z, localTime `...T00:38:00`, offset -14400), a
  winter day (offset -18000), the spring-forward and fall-back days (local
  day grouping and count), a non-DST station (Honolulu), and a
  positive-offset station (Guam). Assert the NOAA request URL uses `gmt` and
  the right bounds.

### Task 3: stations resolver reports the current offset

`graph/schema.resolvers.go` (Station mapping): `timeZoneOffset` = current
offset from `timezone.ForStation`. Tests updated or added. Ask the owner
before changing any existing test expectation.

### Task 4: docs

- New ADR 0038, "Backend fetches NOAA in GMT and uses a lat/lon-derived IANA
  zone".
- Update the notes in 0015 and 0011.
- Close the findings here.

Deploy: backend only. Stale cache entries expire by TTL (DynamoDB 2 days).
