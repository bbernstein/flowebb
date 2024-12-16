#!/bin/bash

export PAGER=""

# Exit on any error
set -e

# Config
LAMBDA_FUNCTIONS=("flowebb-stations-prod" "flowebb-tides-prod")
JAR_FILE="backend/build/libs/tides-be.jar"

# Build the JAR
echo "Building JAR..."
cd backend
./gradlew clean build
cd ..

# Check if build succeeded and JAR exists
if [ ! -f "$JAR_FILE" ]; then
    echo "Error: JAR file not found at $JAR_FILE"
    exit 1
fi

# Update each Lambda function
for function_name in "${LAMBDA_FUNCTIONS[@]}"; do
    echo "Updating Lambda function: $function_name"
    aws lambda update-function-code \
        --function-name "$function_name" \
        --zip-file "fileb://$JAR_FILE"

    # Wait for the update to complete
    echo "Waiting for function update to complete..."
    aws lambda wait function-updated \
        --function-name "$function_name"
done

echo "All Lambda functions updated successfully!"
