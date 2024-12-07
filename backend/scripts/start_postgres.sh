#!/bin/bash

# Check if a PostgreSQL container is already running
if [ "$(docker ps -q -f name=tides_pg)" ]; then
    echo "PostgreSQL container is already running."
else
    echo "Starting a new PostgreSQL container..."
    docker run --name tides_pg -e POSTGRES_USER=bb -e POSTGRES_PASSWORD=bb2468 -e POSTGRES_DB=bbdb -p 5432:5432 -d postgres:latest
fi
