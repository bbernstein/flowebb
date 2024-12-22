#!/bin/bash

export PAGER=""

# Create stations cache table with composite key
aws dynamodb create-table \
    --table-name stations-cache \
    --attribute-definitions \
        AttributeName=stationId,AttributeType=S \
    --key-schema \
        AttributeName=stationId,KeyType=HASH \
    --provisioned-throughput \
        ReadCapacityUnits=5,WriteCapacityUnits=5 \
    --endpoint-url http://localhost:8000

# Create tide predictions cache table with composite key
aws dynamodb create-table \
    --table-name tide-predictions-cache \
    --attribute-definitions \
        AttributeName=stationId,AttributeType=S \
        AttributeName=date,AttributeType=S \
    --key-schema \
        AttributeName=stationId,KeyType=HASH \
        AttributeName=date,KeyType=RANGE \
    --provisioned-throughput \
        ReadCapacityUnits=5,WriteCapacityUnits=5 \
    --endpoint-url http://localhost:8000

# Enable TTL for all tables
aws dynamodb update-time-to-live \
    --table-name stations-cache \
    --time-to-live-specification "Enabled=true, AttributeName=ttl" \
    --endpoint-url http://localhost:8000

aws dynamodb update-time-to-live \
    --table-name tide-predictions-cache \
    --time-to-live-specification "Enabled=true, AttributeName=ttl" \
    --endpoint-url http://localhost:8000

echo "Tables created successfully!"

# Optional: List tables to verify creation
aws dynamodb list-tables --endpoint-url http://localhost:8000
