# 0004: Infra and application deploys are separate; Terraform ignores Lambda code

**Status:** Accepted

**Date:** 2024-12-15

## Context

At first Terraform built and uploaded the Lambda artifact (a build step run from
Terraform plus a file hash). This was fragile: path problems, wrong packaging
that caused class-not-found errors, and every infra apply rebuilt the backend.
A plain script that called the Lambda update-code API just worked. The owner
wanted infra, backend and frontend to deploy independently.

## Decision

- Terraform owns infrastructure only. It creates the Lambda function with a
  placeholder zip unless an artifact path is supplied. The function has
  `lifecycle { ignore_changes = [filename, source_code_hash, publish] }`
  (`infra/terraform/modules/compute/main.tf`), so an apply never overwrites
  deployed code. The placeholder zip must be built from a real file; zipping
  `/dev/null` fails.
- Application code ships through its own workflows:
  `.github/workflows/deploy-backend.yml` builds the binary and calls
  update-function-code, and `deploy-web.yml` syncs the static site to S3 and
  invalidates CloudFront, waiting for the invalidation to finish.
- Infra is applied only by `deploy-infra.yml`.
- All three use the same OIDC role ([0005](0005-github-actions-uses-oidc-role.md)).

In the monorepo each deploy workflow runs on push to `main` when files under
its own platform directory (or the workflow itself) change, and can also be
dispatched manually ([0033](0033-monorepo-reunified-2026.md)).

## Alternatives rejected

- Terraform building and uploading the artifact (`null_resource` build plus a
  file hash): slow, order-dependent, and redeploys code on any infra change.
- One combined pipeline: a code change would risk infra changes, and the
  reverse.

## Platforms

infra, backend, web

## Source

Summarized claude.ai project history: "Updating AWS Lambda Functions with
Terraform" (2024-12-15) and "Separating Infrastructure and Backend Deployment"
(2024-12-20).
