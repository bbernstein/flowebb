# 0034: Trust GitHub's ID-based OIDC subject for the monorepo, applied once locally with Terraform 1.5.0

**Status:** Accepted

**Date:** 2026-11-21

## Context

During the monorepo migration, GitHub issued an ID-based OIDC `sub` claim for
the new repository (numeric owner and repo IDs alongside the names). The role
trust policy matched only name-based subjects, so the new repo could not assume
the CI role ([0005](0005-github-actions-uses-oidc-role.md)).

## Decision

- The `github_actions` module has a `github_oidc_extra_subjects` list
  (`infra/terraform/modules/github_actions/`), combined with the name-based
  `github_repos` patterns in the trust policy and set in
  `infra/terraform/environments/prod/main.tf`.
- The monorepo authenticates through its ID-based entry. Never remove it, even
  when trimming `github_repos`.
- Because CI could not yet assume the role, this one change was applied from a
  local machine with the owner's explicit approval, using Terraform 1.5.0 to
  match the state version. A newer Terraform would upgrade the state format and
  break CI, which pins 1.5.0.
- The normal rule is unchanged: apply only through `deploy-infra.yml`.

## Alternatives rejected

- A wildcard subject in the trust policy: would let unintended repos deploy to
  AWS.
- A local apply with the latest Terraform: silently upgrades the shared state
  version.

## Platforms

infra

## Source

The 2026-11-21 monorepo migration session.
