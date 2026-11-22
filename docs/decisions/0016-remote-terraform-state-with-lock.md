# 0016: Remote Terraform state in S3 with a DynamoDB lock table

**Status:** Accepted

**Date:** 2024-12-31

## Context

Terraform ran from CI and, at the time, from a developer machine, so state had
to be shared and protected from concurrent writes. A cancelled GitHub Actions
run once left a stale lock that blocked every later plan.

## Decision

- State is in an S3 bucket with a DynamoDB lock table (`terraform-state-lock`),
  configured in the backend block of
  `infra/terraform/environments/prod/main.tf` (key `prod/terraform.tfstate`).
  The CI role can access both.
- `infra/terraform/bootstrap/` is the one-time setup for the state storage,
  but see the Note: its bucket name does not match the backend's.
- The apply job in `.github/workflows/deploy-infra.yml` uses
  `concurrency: deploy-infra` with `cancel-in-progress: false`, so two applies
  never overlap and a new run waits instead of killing one in progress.
- Its "Cleanup Terraform Lock" step deletes the lock item only when the run was
  cancelled (`if: cancelled()`, `continue-on-error: true`). A killed run is the
  only case that leaves a stale lock. On a handled plan or apply failure,
  Terraform releases its own lock.
- Do not widen cleanup to failures or `always()`, do not move it before `plan`,
  and do not use `-lock=false`. PR plans from `verify.yml` take the same lock
  and are outside the deploy concurrency group, so a broader cleanup could
  delete a lock a concurrent plan still holds.
- State is never committed or copied locally.

**Note:** the original chat ran cleanup unconditionally or before plan. A later
version ran it `if: always()`. Both were narrowed to `if: cancelled()` on
2026-11-21. Keep the cleanup condition and the non-cancelling concurrency group
together.

**Note:** `infra/terraform/bootstrap/main.tf` creates a bucket named
`flowebb-terraform-state`, but the prod backend uses
`flowebb-terraform-state-bucket`. Only the lock table name matches. The bucket
in use was presumably created another way; confirm before re-running bootstrap
or relying on it to recreate state storage.

## Alternatives rejected

- Local state files: not shareable between CI and the owner, and easy to lose
  or leak.
- Disabling locking (`-lock=false`): no protection against concurrent applies.
- Deleting the lock before every plan, or after every run or failure: could
  break a lock held by a live plan or apply.

## Platforms

infra

## Source

Summarized claude.ai project history: "Troubleshooting GitHub Action Lambda
Function Update" (2024-12-31).
