# 0014: Local versus production behavior is set by the SAM template

**Status:** Accepted

**Date:** 2024-12-30

## Context

The Go backend runs locally under AWS SAM and in production on Lambda.
Conditional values for the two (environment name, DynamoDB endpoint, CORS
origins) did not behave as expected at first, and local runs always hit the
real DynamoDB. The Kotlin version had used DynamoDB Local in Docker.

## Decision

- `backend/template.yaml` has an `IsLocal` condition that sets:
  - `ENV`: `development` locally, `prod` otherwise. Code treats an unset `ENV`
    as production.
  - `DYNAMODB_ENDPOINT`: the DynamoDB Local container locally, an empty string
    otherwise.
  - `ALLOWED_ORIGINS`: a localhost origin locally, the production site otherwise.
- `backend/internal/cache/dynamo_client.go` uses `DYNAMODB_ENDPOINT` when it is
  non-empty and the default AWS endpoint otherwise, without the deprecated
  endpoint-resolver APIs.
- For the production branch, use `!If` with an empty string. `AWS::NoValue`
  inside the condition did not work for this variable in testing.
- Local runs share a Docker network with DynamoDB Local
  (`backend/docker-compose.yml`). Unit tests do not need it.
- Development logging is verbose and console-formatted. Production logging is
  structured JSON at info level.

Production Lambda settings come from Terraform
(`infra/terraform/modules/compute/main.tf`), not from this template.

## Alternatives rejected

- Detecting the environment ad hoc in code: harder to test and easy to get
  wrong.
- Using the production table locally: risks polluting real cache data.
- `AWS::NoValue` for the production case: did not behave as expected.

## Platforms

backend

## Source

Summarized claude.ai project history: "Handling Local vs Production
Environments in Backend-Go" (2024-12-30), "Optimizing Tide Prediction Caching
in Go Backend" (2024-12-31).
