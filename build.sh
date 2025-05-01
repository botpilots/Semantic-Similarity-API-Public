#!/bin/bash

# Exit immediately if a command exits with a non-zero status.
set -e

# === Configuration ===
# The final Docker image name and tag
IMAGE_NAME="quarkus/semsim-amd64:latest"
# Path to the Dockerfile for the native application runtime
DOCKERFILE_PATH="src/main/docker/Dockerfile.jvm"

# === Script Execution ===

echo "INFO: Starting Quarkus JVM build process..."

echo "INFO: 1. Cleaning previous build artifacts (target directory)..."
rm -rf target

echo "INFO: 2. Building JVM JAR using Maven..."
# Standard Maven package command for JVM build
./mvnw package

echo "INFO: 3. Building the final application Docker image ('${IMAGE_NAME}')..."
# This uses the Dockerfile specified in DOCKERFILE_PATH.
# Ensure this Dockerfile uses a compatible base image (like openjdk)

docker build --platform linux/amd64 -f "${DOCKERFILE_PATH}" -t "${IMAGE_NAME}" .

echo "INFO: Build process completed successfully!"
echo "INFO: Final application image created: ${IMAGE_NAME}" 