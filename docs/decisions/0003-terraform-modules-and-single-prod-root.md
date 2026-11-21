# 0003: Terraform layout: per-concern modules, one prod root, no cycles

**Status:** Accepted

**Date:** 2024-12-15

## Context

The AWS stack (Lambda, API Gateway, S3, DynamoDB, CloudFront, DNS, IAM) is
managed in Terraform. The first layout wired things together awkwardly from the
root. While adding GitHub Actions deploys (2024-12-19), `terraform apply`
repeatedly failed with dependency cycles: the CI role needed the CloudFront
distribution and bucket, compute and networking needed IAM outputs, and the
ACM certificate and Lambda build formed another loop.

## Decision

- Each concern is a module under `infra/terraform/modules/`: `compute`,
  `storage`, `edge_networking`, `base_networking`, `iam`, `github_actions`,
  `lambda_build`.
- Networking is split to break cycles. `base_networking` holds the ACM
  certificate and its DNS validation. `edge_networking` holds CloudFront and the
  API domain. The GitHub Actions role is its own module (`github_actions`),
  separate from the Lambda execution role in `iam`. The Lambda build is not a
  dependency of the other modules. Do not merge these modules back together
  without checking for cycles.
- The only deployable root is `infra/terraform/environments/prod/` (`main.tf`,
  `variables.tf`, and a placeholder `terraform.tfvars.example`; the real tfvars
  file is gitignored).
- The Route53 hosted zone has its own root, `infra/terraform/global/route53/`,
  because DNS is long-lived and shared. The state bucket and lock table are
  created once from `infra/terraform/bootstrap/` (see
  [0016](0016-remote-terraform-state-with-lock.md)).
- Domain and project names are variables that CI supplies, not hard-coded
  values.
- Use a current Terraform. An old 0.13 install caused misleading errors early
  on. CI now pins 1.5.0.

## Alternatives rejected

- One flat Terraform directory: hard to reuse, and dependencies get tangled.
- One networking module with IAM outputs fed back in: produced cycles.
- The hosted zone inside the prod root: ties long-lived DNS to resources that
  may be recreated.
- An IAM policy created by hand in the AWS console: rejected to keep everything
  in Terraform.

## Platforms

infra

## Source

Summarized claude.ai project history: "Organizing Terraform Code for Domain and
SSL Certificate" (2024-12-14), "Updating AWS Lambda Functions with Terraform"
(2024-12-15), "Automating Infrastructure and Deployment with GitHub Actions"
(2024-12-19).
