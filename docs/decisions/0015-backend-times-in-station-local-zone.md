# 0015: The backend handles all tide times in the station's local zone

**Status:** Accepted

**Date:** 2024-12-31

## Context

The Go rewrite first parsed NOAA local times as UTC, so an extreme at 05:19
local came out as 05:19 UTC. The web app and the device depend on times in the
station's own zone, which was a stated hard requirement. Later (2025-01-29) a
cache test failed only in the evening: after midnight UTC but before local
midnight, "today" differed between UTC and local time.

## Decision

- Request NOAA data with `time_zone=lst_ldt` (station local standard or
  daylight time).
- Build a location from the station's offset
  (`time.FixedZone` in `backend/internal/tide/service.go`, offset from
  `parseTimeZoneOffset` in `backend/internal/station/noaa.go`) and parse every
  NOAA time in it.
- Compute day boundaries, "today" and "now" in that location. A cache "day" is
  a station-local day ([0009](0009-prediction-cache-per-station-day.md)).
  Never use the server's zone or UTC for parsing or day grouping.
- Responses carry a local-time string and `timeZoneOffsetSeconds`. The web app
  ([0011](0011-web-displays-station-local-time.md)) and firmware
  ([0026](0026-device-graphql-contract.md)) rely on them.
- Tests fix the clock and zone explicitly and format mock NOAA payloads as
  `YYYY-MM-DD HH:MM`. They never depend on the host's current date.

**Note:** two things here need verifying before anyone changes this code.
`parseTimeZoneOffset` turns NOAA's whole-hour standard-time correction into a
fixed offset, while `lst_ldt` times include daylight saving, so during DST the
parsed instants may be off by an hour, and half-hour zones are not
representable. The date-range code also uses `Truncate(24*time.Hour)`, which
truncates on UTC boundaries, not station-local ones.

## Alternatives rejected

- Parse in UTC or server time: shifts every value by the station offset.
- UTC day keys and ranges: cached days would not line up with NOAA's local-day
  data.
- Tests using the real current time: flaky around UTC midnight.

## Platforms

backend (constrains web and firmware through the API)

## Source

Summarized claude.ai project history: "Implementing NOAA Tide Prediction API in
Go" (2024-12-30), "Fixing Timezone Handling in Go Backend for Tide Data"
(2024-12-31), "Troubleshooting UTC Time Mismatch in Unit Test" (2025-01-29).
