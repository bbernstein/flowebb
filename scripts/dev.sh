#!/bin/bash

export PAGER=""

# Start DynamoDB Local and Admin UI
docker-compose up -d

# Create DynamoDB tables if they don't exist
./scripts/init-local-dynamo.sh

# Start the SAM API in one terminal
echo "Starting SAM API..."
./scripts/gobuild.sh && \
sam local start-api \
  --warm-containers EAGER \
  --docker-network sam-network \
  --port 8080 \
  --parameter-overrides Stage=local \
  --container-host 0.0.0.0 \
  --container-host-interface 0.0.0.0 &
SAM_PID=$!

echo "SAM API started with PID: $SAM_PID"

# Start the Next.js frontend in another terminal
echo "Starting Next.js frontend..."
cd frontend && npm run dev &
NEXT_PID=$!

echo "Next.js frontend started with PID: $NEXT_PID"

# Wait for both processes
wait $SAM_PID $NEXT_PID

# Cleanup function
cleanup() {
    echo "Shutting down..."
    kill $SAM_PID $NEXT_PID
    docker-compose down
    exit 0
}

# Handle cleanup on script termination
trap cleanup SIGINT SIGTERM

# Keep script running
wait
