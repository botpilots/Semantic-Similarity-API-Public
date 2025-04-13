#!/bin/bash

# Exit immediately if a command exits with a non-zero status.
set -e

# === Configuration ===
# The final Docker image name and tag
IMAGE_NAME="quarkus/semsim-arm64:latest"
# Path to the Dockerfile for the native application runtime
DOCKERFILE_PATH="src/main/docker/Dockerfile.native-micro"

# === Script Execution ===

echo "INFO: Starting Quarkus native ARM64 build process..."

echo "INFO: 1. Cleaning previous build artifacts (target directory)..."
rm -rf target

echo "INFO: 2. Building native Linux ARM64 executable using Maven container build..."
# This step uses a builder container (like ubi9-quarkus-mandrel-builder)
# to compile the native executable FOR Linux ARM64, even when run on macOS ARM64.
# This solves the 'exec format error' when running the executable in a Linux container.
./mvnw package -Dnative -Dquarkus.native.container-build=true

echo "INFO: 3. Verifying the built executable architecture (should be ELF 64-bit aarch64)..."
# Use 'file' command to check the resulting artifact
file target/*-runner

echo "INFO: 4. Building the final application Docker image ('${IMAGE_NAME}')..."
# This uses the Dockerfile specified in DOCKERFILE_PATH.
# Ensure this Dockerfile uses a compatible base image (like ubi9/ubi-minimal)
# to avoid GLIBC errors encountered during troubleshooting.
docker build -f "${DOCKERFILE_PATH}" -t "${IMAGE_NAME}" .

echo "INFO: Build process completed successfully!"
echo "INFO: Final application image created: ${IMAGE_NAME}" 