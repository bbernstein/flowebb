variable "www_domain" {
  description = "Landing page domain; also the S3 bucket name"
  type        = string
}

variable "apex_domain" {
  description = "Apex domain that redirects to the www domain"
  type        = string
}

variable "project_name" {
  description = "Project name, used in resource names"
  type        = string
}

variable "environment" {
  description = "Environment name, used in resource names"
  type        = string
}

variable "certificate_arn" {
  description = "ARN of the us-east-1 ACM certificate covering both domains"
  type        = string
}

variable "route53_zone_id" {
  description = "ID of the Route53 hosted zone for both domains"
  type        = string
}

variable "cloudfront_logs_bucket" {
  description = "Name of the S3 bucket that receives CloudFront access logs"
  type        = string
}
