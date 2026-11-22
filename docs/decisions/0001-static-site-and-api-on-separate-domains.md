# 0001: Static site on S3 and CloudFront, API on API Gateway and Lambda

**Status:** Accepted

**Date:** 2024-12-12

## Context

Clients needed a way to reach both the Next.js app and the Lambda backend in
AWS, and both had to run locally. Locally the two defaulted to the same port,
and browser calls to the local API hit CORS errors. Later (2024-12-20) the
production site called `localhost` and failed CORS because the API URL was not
set in the production build.

## Decision

- CloudFront serves the frontend as static files from S3. The frontend is a
  static export (`output: 'export'` in `web/next.config.ts`), so it has no
  server, API routes, SSR or server actions.
- The API is API Gateway in front of Lambda, on its own `api` subdomain. Route53,
  ACM, CloudFront and the API domain are all in Terraform
  (`infra/terraform/modules/edge_networking/`, `base_networking/`).
- CORS is controlled by the `ALLOWED_ORIGINS` environment variable on the Lambda
  (`infra/terraform/modules/compute/main.tf`), set to the frontend domain.
- The web app reads the API base URL from `NEXT_PUBLIC_API_URL`
  (`web/src/config/environment.ts`), and `web/src/apollo/client.ts` appends
  `/graphql`. The value is inlined at build time, so the deploy workflow
  (`.github/workflows/deploy-web.yml`) must set it for the build. It cannot be
  changed at runtime and must never hold a secret.
- Locally, the API and Next.js run on different ports (Next.js on 3000, the
  API on 8080).

## Alternatives rejected

- AWS Amplify for the frontend: offered as the managed option. S3 plus
  CloudFront was chosen instead.
- Same-origin proxying of the API through CloudFront: not pursued. A separate
  API domain with CORS was used.
- Runtime configuration of the API URL: not possible in a static export without
  a server.
- Relying on a local `.env` file in CI: it was not loaded in the deploy build,
  which caused the production `localhost` bug.

## Platforms

web, infra, backend (cross-cutting)

## Source

Summarized claude.ai project history: "Structuring Frontend and Backend Access
in AWS" (2024-12-12) and "Debugging CORS Failure in Frontend-Backend
Integration" (2024-12-20).
