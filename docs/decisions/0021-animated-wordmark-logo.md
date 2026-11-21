# 0021: Logo is an animated SVG wordmark, "flow" rising and "ebb" receding

**Status:** Accepted

**Date:** 2025-01-19

## Context

The app needed a minimal brand mark that conveys the name: "flow" as a rising
tide and "ebb" as a receding one.

## Decision

- A wordmark SVG (viewBox 300x100) with blue gradient text. The words move on
  tide-like paths over a seamless 8-second loop, with a subtle animated wave
  line.
- A static variant uses the animation's first frame (words at maximum
  separation).
- The web app uses `web/public/logo-animated.svg` through `next/image` in
  `web/src/app/page.tsx`, 30px tall with about 50px of space above the content
  to keep the header compact. Keep the 3:1 aspect ratio when resizing.

## Alternatives rejected

- A static-only logo: the motion carries the flow and ebb idea.
- An abrupt loop restart: looked jarring, so the loop was made seamless.

## Platforms

web

## Source

Summarized claude.ai project history: "Minimalist Tidal Logo for FlowEbb" and
"Adding SVG Logo to Frontend" (2025-01-19).
