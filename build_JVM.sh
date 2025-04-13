#!/bin/bash

# Exit immediately if a command exits with a non-zero status.
set -e

# === Configuration ===
# The final Docker image name and tag for the JVM version
IMAGE_NAME="quarkus/semsim-jvm:latest"
# Path to the Dockerfile for the JVM application runtime
# Assumes Quarkus generated this standard JVM Dockerfile
DOCKERFILE_PATH="src/main/docker/Dockerfile.jvm"

# === Script Execution ===

echo "INFO: Starting Quarkus JVM build process..."

echo "INFO: 1. Cleaning previous build artifacts (target directory)..."
rm -rf target

echo "INFO: 2. Building standard JVM JAR using Maven..."
# This step builds the standard runnable JAR file.
./mvnw package

echo "INFO: 3. Verifying the built JAR file exists..."
if [ ! -f target/quarkus-app/quarkus-run.jar ]; then
    echo "ERROR: Expected JAR file target/quarkus-app/quarkus-run.jar not found!"
    exit 1
fi
echo "INFO: Found target/quarkus-app/quarkus-run.jar"

echo "INFO: 4. Building the final application Docker image ('${IMAGE_NAME}')..."
# This uses the Dockerfile specified in DOCKERFILE_PATH.
# Ensure this Dockerfile uses a compatible base image with a JRE (e.g., ubi-minimal with java package, eclipse-temurin, amazoncorretto).
if [ ! -f "${DOCKERFILE_PATH}" ]; then
    echo "ERROR: JVM Dockerfile not found at ${DOCKERFILE_PATH}"
    echo "INFO: You might need to run './mvnw package' once manually to generate it, or check the path."
    exit 1
fi
docker build -f "${DOCKERFILE_PATH}" -t "${IMAGE_NAME}" .

echo "INFO: JVM build process completed successfully!"
echo "INFO: Final application image created: ${IMAGE_NAME}"
