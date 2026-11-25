# Landing page: private S3 bucket behind CloudFront (OAC), with the apex
# domain redirected to www by a CloudFront Function.

resource "aws_s3_bucket" "www" {
  bucket = var.www_domain
}

resource "aws_s3_bucket_versioning" "www" {
  bucket = aws_s3_bucket.www.id

  versioning_configuration {
    status = "Enabled"
  }
}

resource "aws_s3_bucket_public_access_block" "www" {
  bucket = aws_s3_bucket.www.id

  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_cloudfront_origin_access_control" "www" {
  name                              = "${var.project_name}-www-${var.environment}"
  description                       = "Origin access control for ${var.www_domain}"
  origin_access_control_origin_type = "s3"
  signing_behavior                  = "always"
  signing_protocol                  = "sigv4"
}

resource "aws_cloudfront_function" "apex_redirect" {
  name    = "${var.project_name}-apex-redirect-${var.environment}"
  runtime = "cloudfront-js-2.0"
  comment = "Redirect ${var.apex_domain} to ${var.www_domain}, keeping path and query"
  publish = true
  code    = file("${path.module}/apex-redirect.js")
}

data "aws_cloudfront_cache_policy" "caching_optimized" {
  name = "Managed-CachingOptimized"
}

resource "aws_cloudfront_distribution" "www" {
  enabled             = true
  is_ipv6_enabled     = true
  default_root_object = "index.html"
  aliases             = [var.www_domain, var.apex_domain]
  comment             = "${var.www_domain} landing page"

  origin {
    domain_name              = aws_s3_bucket.www.bucket_regional_domain_name
    origin_id                = "www"
    origin_access_control_id = aws_cloudfront_origin_access_control.www.id
  }

  default_cache_behavior {
    allowed_methods        = ["GET", "HEAD"]
    cached_methods         = ["GET", "HEAD"]
    target_origin_id       = "www"
    viewer_protocol_policy = "redirect-to-https"
    compress               = true
    cache_policy_id        = data.aws_cloudfront_cache_policy.caching_optimized.id

    function_association {
      event_type   = "viewer-request"
      function_arn = aws_cloudfront_function.apex_redirect.arn
    }
  }

  # A private bucket answers 403 for a missing key, so map both codes.
  custom_error_response {
    error_code         = 403
    response_code      = 404
    response_page_path = "/404.html"
  }

  custom_error_response {
    error_code         = 404
    response_code      = 404
    response_page_path = "/404.html"
  }

  logging_config {
    include_cookies = false
    bucket          = "${var.cloudfront_logs_bucket}.s3.amazonaws.com"
    prefix          = "www/"
  }

  viewer_certificate {
    acm_certificate_arn      = var.certificate_arn
    ssl_support_method       = "sni-only"
    minimum_protocol_version = "TLSv1.2_2021"
  }

  restrictions {
    geo_restriction {
      restriction_type = "none"
    }
  }
}

resource "aws_s3_bucket_policy" "www" {
  bucket = aws_s3_bucket.www.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Sid       = "AllowCloudFrontServicePrincipalReadOnly"
        Effect    = "Allow"
        Principal = { Service = "cloudfront.amazonaws.com" }
        Action    = "s3:GetObject"
        Resource  = "${aws_s3_bucket.www.arn}/*"
        Condition = {
          StringEquals = {
            "AWS:SourceArn" = aws_cloudfront_distribution.www.arn
          }
        }
      }
    ]
  })

  depends_on = [aws_s3_bucket_public_access_block.www]
}

locals {
  record_names = {
    www  = var.www_domain
    apex = var.apex_domain
  }
  records = {
    for pair in setproduct(keys(local.record_names), ["A", "AAAA"]) :
    "${pair[0]}_${pair[1]}" => { name = local.record_names[pair[0]], type = pair[1] }
  }
}

resource "aws_route53_record" "www" {
  for_each = local.records

  zone_id = var.route53_zone_id
  name    = each.value.name
  type    = each.value.type

  alias {
    name                   = aws_cloudfront_distribution.www.domain_name
    zone_id                = aws_cloudfront_distribution.www.hosted_zone_id
    evaluate_target_health = false
  }
}
