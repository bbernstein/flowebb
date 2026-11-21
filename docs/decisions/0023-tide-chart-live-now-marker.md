# 0023: Tide chart "now" marker ticks each minute; range shifts every six hours

**Status:** Accepted

**Date:** 2025-01-20

## Context

The current-time line moved only on page refresh, and the chart's time window
went stale in tabs left open for a long time. Earlier (2025-01-11) a response
with null extremes hid the whole chart.

## Decision

- `TideChart` keeps the current time in state and updates it every minute.
- When the time crosses a six-hour mark (00:00, 06:00, 12:00, 18:00, station
  local time), the prediction range is recomputed so the visible window shifts.
- Predictions are drawn even when the API returns no extremes. Null extremes
  must not hide the chart.

## Alternatives rejected

- Update only on page load: a stale marker.
- Recompute the range every minute: unnecessary work, since only the six-hour
  boundaries change it.

## Platforms

web

## Source

Summarized claude.ai project history: "Handling Null Tide Extremes in
Frontend" (2025-01-11), "Updating Tide Chart Current Time and Refreshing Data"
(2025-01-20).
