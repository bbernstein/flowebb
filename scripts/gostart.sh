#!/bin/bash

export PAGER=""

# this was needed for some reason for me on mac m2 docker desktop
export DOCKER_HOST=unix://${HOME}/.docker/run/docker.sock

./scripts/gobuild.sh && \
sam local start-api \
  --warm-containers EAGER \
  --docker-network sam-network \
  --port 8080 \
  --parameter-overrides Stage=local \
  --container-host 0.0.0.0 \
  --container-host-interface 0.0.0.0
