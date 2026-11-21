# 0016: Remote Terraform state in S3 with a DynamoDB lock table

**Status:** Accepted

**Date:** 2024-12-31

## Context

Terraform ran from CI and, at the time, from a developer machine, so state had
to be shared and protected from concurrent writes. A cancelled GitHub Actions
run once left a stale lock that blocked every later plan.

## Decision

- State is in an S3 bucket with a DynamoDB lock table, configured in the
  backend block of `infra/terraform/environments/prod/main.tf` (key
  `prod/terraform.tfstate`). Both are created once by
  `infra/terraform/bootstrap/`. The CI role can access both.
- `.github/workflows/deploy-infra.yml` runs in a concurrency group with
  `cancel-in-progress: false`, so two applies never overlap. Its final
  "Cleanup Terraform Lock" step deletes the lock item (`if: always()`,
  `continue-on-error: true`), so a failed or cancelled run does not leave a
  stale lock.
- Do not move lock cleanup before `plan`, and do not use `-lock=false`.
- State is never committed or copied locally.

**Note:** the draft from the chat said cleanup runs only on failure. The current
workflow runs it after every apply. That is safe only because of the
non-cancelling concurrency group; keep the two together.

## Alternatives rejected

- Local state files: not shareable between CI and the owner, and easy to lose
  or leak.
- Disabling locking (`-lock=false`): no protection against concurrent applies.
- Deleting the lock before every plan: could break a live run's lock.

## Platforms

infra

## Source

Summarized claude.ai project history: "Troubleshooting GitHub Action Lambda
Function Update" (2024-12-31).
