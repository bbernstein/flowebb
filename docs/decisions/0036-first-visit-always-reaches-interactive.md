# 0036: A first visit always ends on the map, with a location fallback

**Status:** Accepted

**Date:** 2026-11-25

## Context

A visitor with no saved location could stay on the loading spinner forever
(issue #13). The mount effect in `web/src/app/page.tsx` cleared `loading`
only for returning visitors. The geolocation branches and the "no
geolocation" case never did. Some browsers also never call either
geolocation callback when the visitor dismisses the prompt. The effect also
re-ran whenever `fetchStations` changed identity, which happens on every
`StationProvider` render.

## Decision

- Every first-load path goes through one helper that fetches stations, selects
  a station, and clears `loading` in a `finally`. A failed or empty fetch shows
  an error, but the map and **Use My Location** button still appear.
- A first visit selects the nearest station, the same as a map click
  ([0029](0029-auto-select-nearest-station.md)). A returning visitor keeps the
  stored station if it is still in the fetched list.
- If geolocation is unavailable or denied, the app uses Seattle and saves it,
  as before. If the prompt gets no answer within 10 seconds, the app uses
  Seattle but does not save it, so the next visit asks again. An answer that
  arrives after the fallback is ignored.
- The first-load logic runs once, guarded by a ref.

## Alternatives rejected

- No timeout: a dismissed prompt leaves the spinner up in some browsers.
- Showing the map at the default location while the prompt is open: the map
  would jump once the answer arrives.

## Platforms

web
