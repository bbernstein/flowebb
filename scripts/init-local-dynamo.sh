#!/bin/bash

export PAGER=""

# Create stations cache table with composite key
aws dynamodb create-table \
    --table-name station-list-cache \
    --attribute-definitions \
        AttributeName=listId,AttributeType=S \
        AttributeName=partitionId,AttributeType=N \
    --key-schema \
        AttributeName=listId,KeyType=HASH \
        AttributeName=partitionId,KeyType=RANGE \
    --provisioned-throughput \
        ReadCapacityUnits=5,WriteCapacityUnits=5 \
    --endpoint-url http://localhost:8000

# Create location queries cache table
aws dynamodb create-table \
    --table-name location-queries-cache \
    --attribute-definitions \
        AttributeName=latitude,AttributeType=N \
        AttributeName=longitude,AttributeType=N \
    --key-schema \
        AttributeName=latitude,KeyType=HASH \
        AttributeName=longitude,KeyType=RANGE \
    --provisioned-throughput \
        ReadCapacityUnits=5,WriteCapacityUnits=5 \
    --endpoint-url http://localhost:8000

aws dynamodb create-table \
    --table-name stations-cache \
    --attribute-definitions \
        AttributeName=stationId,AttributeType=S \
    --key-schema \
        AttributeName=stationId,KeyType=HASH \
    --provisioned-throughput \
        ReadCapacityUnits=5,WriteCapacityUnits=5 \
    --endpoint-url http://localhost:8000

# Create harmonic constants cache table
aws dynamodb create-table \
    --table-name harmonic-constants-cache \
    --attribute-definitions \
        AttributeName=stationId,AttributeType=S \
    --key-schema \
        AttributeName=stationId,KeyType=HASH \
    --provisioned-throughput \
        ReadCapacityUnits=5,WriteCapacityUnits=5 \
    --endpoint-url http://localhost:8000

# Enable TTL for all tables
aws dynamodb update-time-to-live \
    --table-name station-list-cache \
    --time-to-live-specification "Enabled=true, AttributeName=ttl" \
    --endpoint-url http://localhost:8000

aws dynamodb update-time-to-live \
    --table-name location-queries-cache \
    --time-to-live-specification "Enabled=true, AttributeName=ttl" \
    --endpoint-url http://localhost:8000

aws dynamodb update-time-to-live \
    --table-name stations-cache \
    --time-to-live-specification "Enabled=true, AttributeName=ttl" \
    --endpoint-url http://localhost:8000

aws dynamodb update-time-to-live \
    --table-name harmonic-constants-cache \
    --time-to-live-specification "Enabled=true, AttributeName=ttl" \
    --endpoint-url http://localhost:8000

echo "Tables created successfully!"

# Optional: List tables to verify creation
aws dynamodb list-tables --endpoint-url http://localhost:8000
