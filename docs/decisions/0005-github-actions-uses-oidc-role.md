# 0005: GitHub Actions assumes an AWS role through OIDC, never static keys

**Status:** Accepted

**Date:** 2024-12-16

## Context

When the frontend deploy workflow was added, the question was whether to store
IAM user access keys as GitHub secrets. The Terraform already defined a GitHub
OIDC provider and a CI role. In January 2025 plans from Actions broke and the
role trust had to be re-established, which showed how easy the setup is to
misconfigure.

## Decision

- Every workflow uses `aws-actions/configure-aws-credentials` with
  `role-to-assume` from a repository secret, and declares
  `permissions: id-token: write`.
- The OIDC provider and role are defined in
  `infra/terraform/modules/github_actions/`. Its trust policy limits which
  repositories (by `sub` claim) may assume it.
- No long-lived AWS access keys are stored in GitHub. Account IDs are masked in
  logs.
- Changing the trust list changes who can deploy to AWS, so it needs the
  owner's approval. See [0034](0034-oidc-id-based-subject.md) for the ID-based
  subject the monorepo depends on.

## Alternatives rejected

- An IAM user with an access key in GitHub secrets: long-lived, needs manual
  rotation, and has a larger blast radius if leaked.

## Platforms

infra

## Source

Summarized claude.ai project history: "Deploying Next.js Frontend to AWS with
Terraform" (2024-12-16).
