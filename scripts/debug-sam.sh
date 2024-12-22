#!/bin/bash

export PAGER=""

# Check if a function name was provided
FUNCTION_TO_DEBUG=${1:-TidesFunction}  # Default to TidesFunction if no argument provided

echo "Setting up debugging for $FUNCTION_TO_DEBUG"

cd backend && ./gradlew build && cd .. && \
sam build && \
sam local start-api \
  --warm-containers EAGER \
  --docker-network sam-network \
  --port 8080 \
  --debug-port 5005 \
  --debug-function $FUNCTION_TO_DEBUG \
  --container-host 0.0.0.0 \
  --container-host-interface 0.0.0.0
