#!/bin/bash
set -e

export PAGER=""

# Frontend checks
cd frontend && npm run lint
cd ..

# Backend Go checks
cd backend-go
golangci-lint run ./...
go test -race ./...
go test -coverprofile=test-results/coverage.out ./...
go tool cover -html=test-results/coverage.out -o test-results/coverage.html
cd ..

# Terraform checks
cd infrastructure/terraform
terraform fmt -check -recursive
tflint --init
tflint --recursive
cd ../..

