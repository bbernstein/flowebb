# 0007: Map waits for the stored location and fits bounds to the click plus stations

**Status:** Accepted

**Date:** 2024-12-16

## Context

On first load the map briefly showed the default city, then jumped to the
location saved in the browser. After a click, the map fit only the station
pins and could leave the clicked point off screen. In January 2025 a production
client-side error led to restructuring the map controller.

## Decision

- The map shows a loading spinner (MUI `CircularProgress`, the `isLoading` prop
  on `Map`) until the initial location is known. It never renders the default
  location first.
- A `MapController` child component calls Leaflet `fitBounds` over the user or
  clicked location plus all nearby stations, with 50px padding and `maxZoom`
  12. A single point uses `setView` at zoom 10.
- Bounds creation is wrapped in try/catch and falls back to `setView`, because
  invalid coordinates caused a runtime error.
- Refits happen on first render and when new stations arrive, not on every
  render.
- Code: `web/src/components/Map.tsx`, `web/src/app/page.tsx`.

## Alternatives rejected

- Render the default location, then jump: a visible flash of the wrong place.
- Fit bounds to stations only: the selected location could be outside the view.
- Refit on every render: fights the user's panning.

## Platforms

web

## Source

Summarized claude.ai project history: "Improving Initial Map Loading
Experience" and "Adjusting Map Bounds to Include Clicked Location"
(2024-12-16), "Troubleshooting Client-Side Error on Production Instance"
(2025-01-09).
