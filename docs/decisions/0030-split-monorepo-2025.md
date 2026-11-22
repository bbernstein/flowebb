# 0030: Split the original monorepo into backend, web and infra repos

**Status:** Superseded by [0033](0033-monorepo-reunified-2026.md)

**Date:** 2025-01-27

## Context

The project began in December 2024 as one repo holding the Next.js frontend,
the backend and the Terraform code. By late January 2025 it was too large to
share as context with claude.ai chats and Projects.

## Decision

- Split by area into three repos: backend (Go), web (frontend) and infra
  (Terraform and deploy scripts), each with its own CI and README. History was
  carried over with `git filter-repo`.
- The original repo was marked deprecated with a README pointing to the new
  ones (2025-01-31). A separate ESP32 device repo already existed.
- Each claude.ai Project (Go, Web, Infra, Device) mirrored one repo.
- The only motivation was tooling context size, not team or deployment
  boundaries. That is why the split was later reversed.

## Alternatives rejected

- Stay a monorepo: too much code to give claude.ai as context at once.
- A fourth shared "platform" repo for common CI templates and tooling: proposed
  as optional, not adopted.

## Platforms

cross-cutting

## Source

Summarized claude.ai project history: "Refactoring Monorepo into
Microservices" (2025-01-27), "Deprecating Project, Splitting into 3 Repos"
(2025-01-31).
