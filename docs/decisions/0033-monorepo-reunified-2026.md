# 0033: Return to a single monorepo now that Claude Code reads files on demand

**Status:** Accepted

**Date:** 2026-11-21

## Context

The 2025 split ([0030](0030-split-monorepo-2025.md)) existed only because
claude.ai Projects could not hold the whole codebase in context. Claude Code
reads files on demand, so that limit is gone. Meanwhile the split made
cross-cutting changes (the GraphQL schema, its consumers, the Terraform for the
Lambda) span several repos and PRs.

## Decision

- All platforms live in one public repo: `backend/` (Go GraphQL Lambda),
  `web/` (Next.js), `infra/` (Terraform), `firmware/` (ESP32). Each platform
  directory was imported with its history from its old repo.
- Each directory has its own `CLAUDE.md` with platform rules. The root
  `CLAUDE.md` routes requests to a directory and defines the cross-platform
  change protocol.
- Separate workflows (`verify.yml` with path-filtered jobs, and manually
  dispatched `deploy-backend.yml`, `deploy-web.yml`, `deploy-infra.yml`) keep
  deployment independent ([0004](0004-infra-and-app-deploys-are-separate.md)).
- Do not split again for AI-context reasons. Per-directory `CLAUDE.md` files
  keep context focused.

## Alternatives rejected

- Keep three or four repos: the original reason is gone, and coordinating
  across repos is costly.

## Platforms

cross-cutting

## Source

The 2025 split conversations ("Refactoring Monorepo into Microservices",
2025-01-27; "Deprecating Project, Splitting into 3 Repos", 2025-01-31) and the
2026-11-21 monorepo consolidation recorded in this repo's `README.md` and
`CLAUDE.md`.
