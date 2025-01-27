#!/bin/bash

set -e

# Start in project root directory
ROOT_DIR=$(pwd)

# Change to backend directory
cd backend-go

echo "Installing gqlgen globally..."
go install github.com/99designs/gqlgen@latest

echo "Installing dependencies..."
go get golang.org/x/tools/go/packages@latest
go get golang.org/x/tools/go/ast/astutil@latest
go get golang.org/x/tools/imports@latest
go get golang.org/x/text/cases@latest
go get golang.org/x/text/language@latest
go get github.com/urfave/cli/v2@latest

echo "Running go mod tidy..."
go mod tidy

echo "Generating GraphQL code..."
# Use the globally installed gqlgen
if [ ! -f gqlgen.yml ]; then
    gqlgen init
fi

gqlgen generate

# Clean up any existing build artifacts
rm -rf ../.aws-sam/build
rm -rf .aws-sam/build

# Create root .aws-sam directory structure
mkdir -p ../.aws-sam/build/GraphQLFunction/
mkdir -p ../.aws-sam/build/StationsFunction/
mkdir -p ../.aws-sam/build/TidesFunction/

# Build the Lambda functions
echo "Building graphql function..."
GOOS=linux GOARCH=arm64 CGO_ENABLED=0 go build -o ../.aws-sam/build/GraphQLFunction/bootstrap ./cmd/graphql

# Build the stations Lambda
echo "Building stations function..."
GOOS=linux GOARCH=arm64 CGO_ENABLED=0 go build -o ../.aws-sam/build/StationsFunction/bootstrap ./cmd/stations

# Build the tides Lambda
echo "Building tides function..."
GOOS=linux GOARCH=arm64 CGO_ENABLED=0 go build -o ../.aws-sam/build/TidesFunction/bootstrap ./cmd/tides

cd "$ROOT_DIR"

# Verify builds
echo "Verifying builds..."
if [ ! -x .aws-sam/build/StationsFunction/bootstrap ]; then
    echo "Error: StationsFunction bootstrap not found or not executable"
    exit 1
fi

if [ ! -x .aws-sam/build/TidesFunction/bootstrap ]; then
    echo "Error: TidesFunction bootstrap not found or not executable"
    exit 1
fi

# Make sure binaries are executable
chmod +x .aws-sam/build/StationsFunction/bootstrap
chmod +x .aws-sam/build/TidesFunction/bootstrap

echo "Build complete!"
