#!/bin/bash

export PAGER=""

cd backend && ./gradlew build && cd .. && \
sam build && \
sam local start-api --warm-containers LAZY --docker-network sam-network  --port 8080 --debug-port 5005
