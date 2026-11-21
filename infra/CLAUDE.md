# infra/: Terraform for all AWS resources

Read the root `CLAUDE.md` first.

## Stack

- Terraform 1.5.0 (CI pins it), AWS provider, tflint v0.44.1.
- Remote state: S3 bucket `flowebb-terraform-state-bucket`, lock table `terraform-state-lock`.
- Region `us-east-1`.

## Directory map

| Path | Contents |
|---|---|
| `terraform/environments/prod/` | **the prod root module**: `main.tf`, `variables.tf`, `terraform.tfvars.example` |
| `terraform/modules/compute/` | Lambda functions, API Gateway |
| `terraform/modules/storage/` | S3 buckets, DynamoDB tables |
| `terraform/modules/edge_networking/` | CloudFront, DNS records |
| `terraform/modules/base_networking/` | VPC, subnets |
| `terraform/modules/iam/` | IAM roles and policies |
| `terraform/modules/github_actions/` | GitHub OIDC provider and the CI role `flowebb-github-actions-prod` |
| `terraform/modules/lambda_build/` | Lambda build configuration |
| `terraform/global/route53/` | hosted zone |
| `terraform/bootstrap/` | one-time state bucket and lock table setup |

## Commands

Start from `infra/`. The block works if you paste it as is.

```bash
# Lint (from infra/terraform)
cd terraform
terraform fmt -check -recursive    # use `terraform fmt -recursive` to fix
tflint --init
tflint --recursive

# Plan (from infra/terraform/environments/prod): read-only and safe
cd environments/prod               # relative to infra/terraform
terraform init
terraform plan
```

A local `plan` needs AWS credentials and a local `terraform.tfvars` (copy it
from `terraform.tfvars.example`; it is gitignored). CI runs `plan` on every
infra PR and fails if the plan shows changes, so the owner must review them.

## Rules

- **Never run `terraform apply` (or `destroy`, `import`, `state rm/mv`, or
  `force-unlock`) locally.** Apply happens only through
  `.github/workflows/deploy-infra.yml`, dispatched with the owner's approval.
- `plan`, `fmt`, `validate`, and `tflint` are safe.
- State is remote. Never commit state or copy it locally.
- The GitHub OIDC trust list is the `github_repos` variable plus
  `github_oidc_extra_subjects`, both in `terraform/environments/prod/main.tf`.
  Changing either changes which repos can deploy to AWS, so get the owner's
  approval first. `bbernstein/flowebb` authenticates through GitHub's ID-based
  subject in `github_oidc_extra_subjects`. Never remove that entry, even when
  trimming `github_repos`.
- **Merging an infra PR.** A PR that intentionally changes infrastructure makes
  `infra-plan` fail (the plan has changes), so `ci-gate` fails. This is by
  design. The owner reviews the plan summary in the `infra-plan` log, merges with
  admin bypass, then dispatches `deploy-infra` from `main`. Agents must not loop
  trying to make that check pass. Stop and ask the owner.
- Renaming or removing a resource that backend, web, or CI depends on (Lambda
  name `flowebb-graphql-prod`, buckets, DynamoDB tables, the CloudFront
  distribution) is a cross-platform change. Check `.github/workflows/` and
  `backend/` for references.
- There are no unit tests for Terraform. `fmt`, `tflint`, and a clean `plan`
  are the verification.

## Never commit secrets or private data

The root rule applies in full. Here: never commit `terraform.tfvars` (only
`*.tfvars.example` with placeholders), `.terraform/`, `*.tfstate*`, plan files
(`tfplan`), AWS account IDs, or ARNs that contain them.

## Decisions

See `docs/decisions/` (records will be added there).
