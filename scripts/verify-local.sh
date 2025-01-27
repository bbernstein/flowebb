#!/bin/bash
set -e

export COVERAGE_THRESHOLD=90
export PAGER=""

# Frontend checks
cd frontend && npm run lint && npm run test && npm run build
cd ..

# Backend Go checks
cd backend-go
golangci-lint run ./...
go test -race ./...


mkdir -p test-results
# Run tests with coverage and enforce minimum threshold (e.g., 80%)
ENV=development
go test -covermode=atomic -coverpkg="$(go list ./... | grep -v graph/generated | grep -v graph/model | tr '\n' ',')" ./... -coverprofile=test-results/coverage.out
# Check if coverage meets threshold
COVERAGE=$(go tool cover -func=test-results/coverage.out | grep total | awk '{print $3}' | sed 's/%//')
echo "Code coverage: $COVERAGE%"
THRESHOLD=$COVERAGE_THRESHOLD
if (( $(echo "$COVERAGE < $THRESHOLD" | bc -l) )); then
  echo "Code coverage $COVERAGE% is below threshold of $THRESHOLD%"
  exit 1
fi
cd ..

# Terraform checks
cd infrastructure/terraform
terraform fmt -check -recursive
tflint --init
tflint --recursive
cd ../..

