# 0024: License the firmware under MIT

**Status:** Accepted

**Date:** 2025-01-21

## Context

Before making the device repo public the owner compared common open-source
licenses. The firmware depends on permissively licensed libraries and the
Espressif and Arduino ecosystem.

## Decision

- MIT License, in `firmware/LICENSE`. Only the license text and copyright
  notice must be kept. A short copyright line in source headers is optional.
- The license file covers the firmware directory. The repository root has no
  license file of its own.

## Alternatives rejected

- Apache 2.0: compatible, but adds NOTICE handling, per-file headers and
  change-documentation duties the owner thought too heavy.
- GPL v3 or LGPL v3: copyleft would restrict reuse of a hobby device project
  for no benefit.

## Platforms

firmware

## Source

Summarized claude.ai project history: "Choosing an Open Source License for Your
Project" (2025-01-21).
