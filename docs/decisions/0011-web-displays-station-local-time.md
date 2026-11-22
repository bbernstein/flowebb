# 0011: The web app shows every time in the station's local zone

**Status:** Accepted

**Date:** 2024-12-23

## Context

Tide times were first shown in UTC, then in the viewer's browser time zone.
Both are wrong for a station somewhere else.

## Decision

- The tides API returns `timeZoneOffsetSeconds`, and the frontend shifts every
  displayed time by it: high and low times, the prediction series, x-axis
  labels, tooltips, the current-time marker, and day boundaries (station local
  midnight).
- The helpers live in one module, `web/src/utils/dateTime.tsx`
  (`formatStationTime`, `formatStationDateTime`, and a day-bounds helper), used
  by `TideInfo` and `TideChart`. Do not reimplement formatting per component.
- If the offset is null, display falls back to the browser's local time.
- The selected station's offset is stored with the last-station record
  ([0017](0017-persist-last-location-and-station.md)), so times render correctly
  before nearby stations are fetched.
- The offset is a number of seconds, not an IANA zone name. The backend decides
  it ([0015](0015-backend-times-in-station-local-zone.md)).

**Note:** the backend currently derives the offset from NOAA's whole-hour
standard-time correction, so daylight saving time and half-hour zones are not
reflected in it. Check [0015](0015-backend-times-in-station-local-zone.md)
before relying on the offset for DST-sensitive display.

## Alternatives rejected

- Show UTC: confusing for a chart of a distant station.
- Use the browser's time zone: wrong whenever the viewer is not at the station.
- Formatting logic duplicated in each component: replaced by the shared module.

## Platforms

web

## Source

Summarized claude.ai project history: "Tide Chart API Response with Timezone
Offset", "Updating TideChart to Display Local Time", "Displaying
Location-Specific Timezones in Frontend Components" (2024-12-20 to 2024-12-23).
