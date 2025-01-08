#!/bin/bash

set -e

export PAGER=""

# Function to cleanup all processes and containers
cleanup() {
    echo "Shutting down..."

    # Kill all child processes in this session
    pkill -P $$

    # Kill any processes using port 8080
    if command -v lsof >/dev/null 2>&1; then
        PIDS=$(lsof -t -i:8080 2>/dev/null)
        if [ ! -z "$PIDS" ]; then
            echo "Killing processes on port 8080: $PIDS"
            kill $PIDS 2>/dev/null || true
        fi
    fi

    # Kill SAM processes specifically
    pkill -f "sam local" 2>/dev/null || true

    # Stop docker containers
    docker-compose down

    # Exit
    exit 0
}

# Handle cleanup on script termination
trap cleanup SIGINT SIGTERM EXIT

# Start DynamoDB Local and Admin UI
docker-compose up -d

# Create DynamoDB tables if they don't exist
./scripts/init-local-dynamo.sh

# this was needed for some reason for me on mac m2 docker desktop
export DOCKER_HOST=unix://${HOME}/.docker/run/docker.sock

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
