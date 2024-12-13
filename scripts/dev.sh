#!/bin/bash

export PAGER=""

# Start DynamoDB Local and Admin UI
docker-compose up -d

# Create DynamoDB tables if they don't exist
./scripts/init-local-dynamo.sh

# Start the SAM API in one terminal
echo "Starting SAM API..."
sam local start-api --warm-containers LAZY --docker-network sam-network --port 8080 &
SAM_PID=$!

# Start the Next.js frontend in another terminal
echo "Starting Next.js frontend..."
cd frontend && npm run dev &
NEXT_PID=$!

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
