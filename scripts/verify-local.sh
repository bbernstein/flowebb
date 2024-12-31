#!/bin/bash
set -e

export PAGER=""

# Frontend checks
cd frontend && npm run lint
cd ..

# Backend Go checks
cd backend-go && golangci-lint run ./...
cd ..

# Terraform checks
cd infrastructure/terraform
terraform fmt -check -recursive
tflint --init
tflint --recursive
cd ../..
