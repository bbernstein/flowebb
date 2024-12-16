#!/bin/bash

export PAGER=""

set -e

# Configuration
ENVIRONMENT=$1
if [ -z "$ENVIRONMENT" ]; then
    echo "Usage: $0 <environment>"
    exit 1
fi

# Load environment-specific variables
ENV_FILE=".env.$ENVIRONMENT"
if [ ! -f "$ENV_FILE" ]; then
    echo "Error: Environment file $ENV_FILE not found"
    exit 1
fi

# shellcheck disable=SC1090
source "$ENV_FILE"

# Build the Next.js application
echo "Building Next.js application..."
cd frontend
npm ci
npm run build

# Sync the build output to S3
echo "Deploying to S3..."
aws s3 sync out/ "s3://${FRONTEND_BUCKET_NAME}" \
    --delete \
    --cache-control "public, max-age=31536000, immutable" \
    --exclude "index.html" \
    --exclude "404.html"

# Sync HTML files with different cache settings
aws s3 sync out/ "s3://${FRONTEND_BUCKET_NAME}" \
    --delete \
    --cache-control "public, max-age=0, must-revalidate" \
    --exclude "*" \
    --include "index.html" \
    --include "404.html"

# Invalidate CloudFront cache
echo "Invalidating CloudFront cache..."
aws cloudfront create-invalidation \
    --distribution-id "${CLOUDFRONT_DISTRIBUTION_ID}" \
    --paths "/*"

echo "Deployment complete!"
