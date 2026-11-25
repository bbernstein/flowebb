variable "project_name" {
  description = "Project name"
  type        = string
}

variable "environment" {
  description = "Environment name"
  type        = string
}

variable "github_org" {
  description = "GitHub organization name"
  type        = string
}

variable "github_repos" {
  description = "List of GitHub repository names"
  type        = list(string)
}

variable "github_oidc_extra_subjects" {
  description = "Additional OIDC sub patterns (e.g. GitHub's ID-based format repo:<owner>@<owner_id>/<repo>@<repo_id>:*)"
  type        = list(string)
  default     = []
}

variable "frontend_bucket_arn" {
  description = "ARN of the frontend S3 bucket"
  type        = string
}

variable "terraform_state_bucket" {
  description = "Name of the S3 bucket for Terraform state"
  type        = string
}

variable "terraform_state_lock_table" {
  description = "Name of the DynamoDB table for Terraform state locking"
  type        = string
}

variable "www_bucket_name" {
  description = "Name of the S3 bucket for the www landing site"
  type        = string
}
