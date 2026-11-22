# 0012: Highcharts for the tide chart; accessibility module currently disabled

**Status:** Accepted

**Date:** 2024-12-28

## Context

The tide chart is Highcharts through `highcharts-react-official` in
`web/src/components/TideChart.tsx`. Highcharts warned that its accessibility
module was not loaded. Loading the module under Next.js (initializing at module
load, in an effect, a bare `require`, a dynamic import) caused load errors.

## Decision

- Keep Highcharts: a spline of predictions, extreme markers, a current-time
  plot line, and a tooltip that snaps to high and low points.
- The chart options carry an `accessibility` block with a description, new-data
  announcements and a point description format, but it sets `enabled: false`.
  That silences the warning, and the descriptive text is inert.
- To enable real accessibility support, load the module on the client only.
  Initializing it at module load or during prerender broke the build before.

## Alternatives rejected

- Ignore the warning: noisy in the console.
- Module-level `require` or effect-time initialization: runtime errors under
  Next.js.

## Platforms

web

## Source

Summarized claude.ai project history: "Improving Highcharts Accessibility"
(2024-12-28).
