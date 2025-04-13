#!/bin/bash

# Exit immediately if a command exits with a non-zero status.
set -e

# === Configuration ===
# TODO: Fill in these values before running the script.

# Google Cloud Project ID
GCP_PROJECT_ID="semsim-456712"

# Artifact Registry Configuration
# Choose a region for your Artifact Registry (e.g., us-central1, europe-west1)
ARTIFACT_REGISTRY_REGION="europe-north2"
# Name for your Artifact Registry repository (e.g., my-app-repo)
ARTIFACT_REGISTRY_REPO_NAME="semsim-repo"

# Cloud Run Configuration
# Name for your Cloud Run service (e.g., semsim-api)
CLOUD_RUN_SERVICE_NAME="semsim-api"
# Choose a region for your Cloud Run service (often the same as Artifact Registry)
CLOUD_RUN_REGION="europe-north2"
# Set to true to allow public access to the service, false for authenticated access only
ALLOW_UNAUTHENTICATED=true

# Local Docker Image Configuration
# The name and tag of the local Docker image you built
LOCAL_IMAGE_NAME="quarkus/semsim-arm64:latest"

# === Prerequisites Check (Informational) ===
echo "INFO: Ensure you have:"
echo "1. Google Cloud SDK ('gcloud') installed and authenticated ('gcloud auth login')."
echo "2. The correct GCP project selected ('gcloud config set project ${GCP_PROJECT_ID}')."
echo "3. Docker installed and running."
echo "4. Permissions in GCP to manage Artifact Registry and Cloud Run."
read -p "Press Enter to continue if prerequisites are met..."

# === Script Execution ===

echo "INFO: Enabling necessary Google Cloud APIs..."
gcloud services enable artifactregistry.googleapis.com run.googleapis.com \
    --project="${GCP_PROJECT_ID}"

echo "INFO: Configuring Docker authentication for Artifact Registry..."
gcloud auth configure-docker "${ARTIFACT_REGISTRY_REGION}-docker.pkg.dev" --quiet

echo "INFO: Checking/Creating Artifact Registry repository '${ARTIFACT_REGISTRY_REPO_NAME}'..."
gcloud artifacts repositories describe "${ARTIFACT_REGISTRY_REPO_NAME}" \
    --project="${GCP_PROJECT_ID}" \
    --location="${ARTIFACT_REGISTRY_REGION}" > /dev/null 2>&1 || \
gcloud artifacts repositories create "${ARTIFACT_REGISTRY_REPO_NAME}" \
    --project="${GCP_PROJECT_ID}" \
    --repository-format=docker \
    --location="${ARTIFACT_REGISTRY_REGION}" \
    --description="Repository for ${CLOUD_RUN_SERVICE_NAME} images"

echo "INFO: Constructing image names..."
REMOTE_IMAGE_NAME="${ARTIFACT_REGISTRY_REGION}-docker.pkg.dev/${GCP_PROJECT_ID}/${ARTIFACT_REGISTRY_REPO_NAME}/${CLOUD_RUN_SERVICE_NAME}:latest"
echo "  Local Image: ${LOCAL_IMAGE_NAME}"
echo "  Remote Image: ${REMOTE_IMAGE_NAME}"

echo "INFO: Tagging local Docker image..."
docker tag "${LOCAL_IMAGE_NAME}" "${REMOTE_IMAGE_NAME}"

echo "INFO: Pushing image to Google Artifact Registry..."
docker push "${REMOTE_IMAGE_NAME}"

echo "INFO: Deploying image to Google Cloud Run..."
# This command deploys a new revision of the service.
# If the service doesn't exist, it will be created with these settings.
# If the service exists, it will be updated. Only the settings specified below
# are actively managed by this script on each run.
DEPLOY_ARGS=(
    "${CLOUD_RUN_SERVICE_NAME}"  # Identifies the service to create/update
    --image="${REMOTE_IMAGE_NAME}" # Specifies the container image for the new revision (updated every time)
    --region="${CLOUD_RUN_REGION}"   # Must match existing service region if updating
    --cpu-architecture=arm64  # Specifies the required CPU architecture (set on create/update)
    --platform=managed        # Specifies the Cloud Run platform (set on create)
    --project="${GCP_PROJECT_ID}" # Specifies the GCP project
)

# Authentication is explicitly set based on the ALLOW_UNAUTHENTICATED variable
if [ "$ALLOW_UNAUTHENTICATED" = true ]; then
    DEPLOY_ARGS+=(--allow-unauthenticated)
    echo "INFO: Service will be publicly accessible."
else
    DEPLOY_ARGS+=(--no-allow-unauthenticated)
    echo "INFO: Service will require authentication (IAM)."
fi

# === Other Cloud Run Settings (Not Managed by this Script) ===
# Settings like Environment Variables, Secrets, CPU/Memory limits, Scaling,
# VPC connectors, Service Accounts etc., are NOT configured here.
# - If creating a new service, Cloud Run uses defaults for these.
# - If updating an existing service, these settings persist from the previous revision
#   unless explicitly overridden by adding more flags to the 'gcloud run deploy' command above
#   (e.g., --set-env-vars=KEY1=VAL1,KEY2=VAL2, --memory=512Mi, --min-instances=1 etc.)
# You can manage these settings via the Google Cloud Console or by adding the appropriate
# flags to the DEPLOY_ARGS array above.

gcloud run deploy "${DEPLOY_ARGS[@]}"

echo "INFO: Deployment process completed!"
echo "INFO: Access your service at the URL provided by the 'gcloud run deploy' command." 