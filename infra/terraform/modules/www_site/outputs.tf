output "bucket_name" {
  description = "Name of the S3 bucket holding the landing page"
  value       = aws_s3_bucket.www.id
}

output "distribution_id" {
  description = "ID of the CloudFront distribution serving the landing page"
  value       = aws_cloudfront_distribution.www.id
}
