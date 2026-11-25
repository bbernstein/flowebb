#!/usr/bin/env bash
# Deploy www/site/ to S3 and invalidate CloudFront. Run from www/.
# Needs env WWW_BUCKET_NAME and WWW_CLOUDFRONT_DISTRIBUTION_ID, and the aws CLI.
# Skips (exit 0) when either is unset, so the workflow stays green before the
# secrets exist. Never prints the bucket name or distribution ID.
set -euo pipefail

if [ -z "${WWW_BUCKET_NAME:-}" ] || [ -z "${WWW_CLOUDFRONT_DISTRIBUTION_ID:-}" ]; then
  echo "WWW_* secrets not set; skipping deploy"
  exit 0
fi

DEST="s3://${WWW_BUCKET_NAME}"

# Assets first, so new HTML never references a file that is not uploaded yet.
aws s3 sync site/ "$DEST" --delete \
  --cache-control "public, max-age=86400" \
  --exclude "*.html" --exclude "*.txt" --exclude "*.xml"

aws s3 sync site/ "$DEST" --delete \
  --cache-control "public, max-age=0, must-revalidate" \
  --exclude "*" --include "*.html" --include "*.txt" --include "*.xml"

INVALIDATION_ID="$(aws cloudfront create-invalidation \
  --distribution-id "$WWW_CLOUDFRONT_DISTRIBUTION_ID" --paths "/*" \
  --query Invalidation.Id --output text)"
aws cloudfront wait invalidation-completed \
  --distribution-id "$WWW_CLOUDFRONT_DISTRIBUTION_ID" --id "$INVALIDATION_ID"
echo "Deploy complete"
