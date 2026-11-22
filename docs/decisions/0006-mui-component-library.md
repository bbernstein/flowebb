# 0006: Use Material UI for web components, not Tailwind or shadcn/ui

**Status:** Accepted

**Date:** 2024-12-16

## Context

The first frontend used Tailwind CSS and shadcn/ui cards. The owner chose to
standardize on one component library with built-in theming.

## Decision

- Use `@mui/material` and `@mui/icons-material` with MUI `styled` components
  for all UI (TideInfo, page layout, map overlays, spinners).
- Tailwind and PostCSS were removed, and the shadcn card imports replaced.
- New components are MUI-based by default. Leaflet map styling is overridden
  through MUI `styled` wrappers.

## Alternatives rejected

- Keep Tailwind plus shadcn/ui: the owner preferred a single library with
  theming built in.

## Platforms

web

## Source

Summarized claude.ai project history: "Refactor Frontend to Use Material-UI"
(2024-12-16).
