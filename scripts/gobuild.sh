#!/bin/bash

set -e

# Start in project root directory
ROOT_DIR=$(pwd)

# Clean up any existing build artifacts
rm -rf .aws-sam/build
rm -rf backend-go/.aws-sam/build

# Create root .aws-sam directory structure
mkdir -p .aws-sam/build/StationsFunction/
mkdir -p .aws-sam/build/TidesFunction/

cd backend-go

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
