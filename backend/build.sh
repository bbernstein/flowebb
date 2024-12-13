#!/bin/bash

# Exit on any error
set -e

# Build with Gradle
./gradlew clean build

# Copy the JAR to the correct location for SAM
mkdir -p .aws-sam/build/TidesFunction/
mkdir -p .aws-sam/build/StationsFunction/

cp build/libs/tides-be-0.0.1.jar .aws-sam/build/TidesFunction/
cp build/libs/tides-be-0.0.1.jar .aws-sam/build/StationsFunction/
