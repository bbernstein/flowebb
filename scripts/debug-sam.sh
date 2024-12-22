#!/bin/bash

export PAGER=""

# Check if a function name was provided
FUNCTION_TO_DEBUG=${1}

if [ -z "$FUNCTION_TO_DEBUG" ]; then
  echo "Not debugging, add function to debug: TidesFunction, StationsFunction"
else
  echo "Debugging function: $FUNCTION_TO_DEBUG"
fi

cd backend && ./gradlew build && cd .. && \
sam build && \
sam local start-api \
  --warm-containers EAGER \
  --docker-network sam-network \
  --port 8080 \
  --debug-port 5005 \
  --debug-function "$FUNCTION_TO_DEBUG" \
  --container-host 0.0.0.0 \
  --container-host-interface 0.0.0.0
