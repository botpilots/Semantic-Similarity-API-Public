#!/bin/bash

# Exit immediately if a command exits with a non-zero status.
set -e

# === Configuration ===
# Adjust REGION, NAMES, or RESOURCE specs if needed.
# NOTE: Consider using different ECR/ECS names (e.g., adding '-jvm') to avoid conflicts
# if you plan to deploy both native and JVM versions simultaneously.

# AWS Region
AWS_REGION="eu-west-1" # Choose your desired AWS region (e.g., us-east-1, eu-west-1)

# ECR (Elastic Container Registry) Configuration
ECR_REPOSITORY_NAME="semsim-repo" # Choose a name for your ECR repository (e.g., semsim-jvm-repo)

# ECS (Elastic Container Service) Configuration
ECS_CLUSTER_NAME="semsim-cluster"   # Choose a name for your ECS cluster (e.g., semsim-jvm-cluster)
ECS_SERVICE_NAME="semsim-service"   # Choose a name for your ECS service (e.g., semsim-jvm-service)
ECS_TASK_FAMILY="semsim-task"     # Choose a family name for your ECS task definitions (e.g., semsim-jvm-task)

# Application Configuration
CONTAINER_PORT=8080 # The port your application listens on inside the container
# Adjusted vCPU and Memory for standard JVM Quarkus application
TASK_CPU="512"    # CPU units (512 = 0.5 vCPU)
TASK_MEMORY="1024" # Memory in MiB (1024 = 1 GB)

# Local Docker Image Configuration
# The name and tag of the local JVM Docker image built by build_JVM.sh
LOCAL_IMAGE_NAME="quarkus/semsim-jvm:latest"

# === Prerequisites Check ===
echo "INFO: Ensure you have:"
echo "1. AWS CLI installed and configured for IAM Identity Center (SSO)."
echo "2. Logged in via IAM Identity Center: 'aws sso login' (or 'aws sso login --profile <your-profile>')."
echo "3. Docker installed and running."
echo "4. Sufficient IAM permissions for ECR and ECS via your SSO role."
read -p "Press Enter to continue if prerequisites are met..."

# === Script Execution ===

echo "INFO: Retrieving AWS Account ID from active credentials..."
AWS_ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text --region "${AWS_REGION}")
if [ -z "$AWS_ACCOUNT_ID" ]; then
  echo "ERROR: Failed to retrieve AWS Account ID. Ensure you are logged in via 'aws sso login' and have permissions."
  exit 1
fi
echo "INFO: Using AWS Account ID: ${AWS_ACCOUNT_ID}"

echo "INFO: Configuring Docker authentication for AWS ECR..."
aws ecr get-login-password --region "${AWS_REGION}" | docker login --username AWS --password-stdin "${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com"

echo "INFO: Checking/Creating ECR repository '${ECR_REPOSITORY_NAME}'..."
aws ecr describe-repositories --repository-names "${ECR_REPOSITORY_NAME}" --region "${AWS_REGION}" > /dev/null 2>&1 || \
aws ecr create-repository \
    --repository-name "${ECR_REPOSITORY_NAME}" \
    --region "${AWS_REGION}" \
    --image-scanning-configuration scanOnPush=true \
    --image-tag-mutability MUTABLE \
    --tags Key=Project,Value=SemanticSimilarity Key=ManagedBy,Value=DeployScript > /dev/null

echo "INFO: Constructing ECR image URI..."
ECR_IMAGE_URI="${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com/${ECR_REPOSITORY_NAME}:latest"
echo "  Local Image: ${LOCAL_IMAGE_NAME}"
echo "  Remote ECR Image URI: ${ECR_IMAGE_URI}"

echo "INFO: Tagging local Docker image..."
docker tag "${LOCAL_IMAGE_NAME}" "${ECR_IMAGE_URI}"

echo "INFO: Pushing image to AWS ECR..."
docker push "${ECR_IMAGE_URI}"

echo "INFO: Checking/Creating ECS cluster '${ECS_CLUSTER_NAME}' (Fargate only)..."
# Use list-clusters and grep to check for existence
CLUSTER_ARN="arn:aws:ecs:${AWS_REGION}:${AWS_ACCOUNT_ID}:cluster/${ECS_CLUSTER_NAME}"

if aws ecs list-clusters --region "${AWS_REGION}" | grep -q "${CLUSTER_ARN}"; then
    echo "INFO: Cluster '${ECS_CLUSTER_NAME}' already exists."
else
    echo "INFO: Cluster '${ECS_CLUSTER_NAME}' not found, creating..."
    aws ecs create-cluster --cluster-name "${ECS_CLUSTER_NAME}" --region "${AWS_REGION}" --settings name=containerInsights,value=enabled
    # Check if create-cluster succeeded
    if [ $? -ne 0 ]; then
        echo "ERROR: Failed to create cluster '${ECS_CLUSTER_NAME}'. Check permissions and AWS CLI output above."
        exit 1
    fi
    echo "INFO: Cluster '${ECS_CLUSTER_NAME}' created successfully."
fi

echo "INFO: Registering ECS Task Definition '${ECS_TASK_FAMILY}' (JVM)..."
# Define the task definition JSON. Removed runtimePlatform for default x86_64 architecture.
TASK_DEFINITION_JSON=$(cat <<EOF
{
    "family": "${ECS_TASK_FAMILY}",
    "networkMode": "awsvpc",
    "requiresCompatibilities": ["FARGATE"],
    "cpu": "${TASK_CPU}",
    "memory": "${TASK_MEMORY}",
    "runtimePlatform": {
        "operatingSystemFamily": "LINUX",
        "cpuArchitecture": "ARM64"
    },
    "executionRoleArn": "arn:aws:iam::${AWS_ACCOUNT_ID}:role/ecsTaskExecutionRole",
    "containerDefinitions": [
        {
            "name": "${ECS_SERVICE_NAME}",
            "image": "${ECR_IMAGE_URI}",
            "cpu": ${TASK_CPU},
            "memory": ${TASK_MEMORY},
            "essential": true,
            "portMappings": [
                {
                    "containerPort": ${CONTAINER_PORT},
                    "hostPort": ${CONTAINER_PORT},
                    "protocol": "tcp"
                }
            ],
            "logConfiguration": {
                "logDriver": "awslogs",
                "options": {
                    "awslogs-group": "/ecs/${ECS_TASK_FAMILY}",
                    "awslogs-region": "${AWS_REGION}",
                    "awslogs-stream-prefix": "ecs"
                }
            }
        }
    ]
}
EOF
)

# --- DEBUGGING ---
echo "--- DEBUG: Generated Task Definition JSON ---"
echo "${TASK_DEFINITION_JSON}"
echo "--- END DEBUG ---"
# Optional: Validate JSON if jq is installed
if command -v jq &> /dev/null; then
    echo "${TASK_DEFINITION_JSON}" | jq .
    if [ $? -ne 0 ]; then
        echo "ERROR: Generated JSON is invalid according to jq. Check script variables and heredoc syntax."
        # exit 1 # Optional: Exit if JSON is invalid before calling AWS
    fi
else
    echo "WARN: 'jq' command not found. Cannot validate generated JSON locally."
fi

# Register the task definition
TASK_DEFINITION_ARN=$(aws ecs register-task-definition \
    --region "${AWS_REGION}" \
    --cli-input-json "${TASK_DEFINITION_JSON}" \
    --query 'taskDefinition.taskDefinitionArn' --output text)

echo "INFO: Registered Task Definition ARN: ${TASK_DEFINITION_ARN}"

# Before creating/updating the service, we need the default VPC subnets and a security group.
echo "INFO: Retrieving default VPC subnets..."
# Attempt to get the default VPC ID
VPC_ID=$(aws ec2 describe-vpcs --filters Name=isDefault,Values=true --query 'Vpcs[0].VpcId' --output text --region ${AWS_REGION})
if [ -z "$VPC_ID" ] || [ "$VPC_ID" == "None" ]; then
    echo "ERROR: Could not find a default VPC in region ${AWS_REGION}. Manual VPC/Subnet configuration required."
    exit 1
fi
echo "INFO: Found default VPC ID: ${VPC_ID}"

# Get subnets in the default VPC across common AZs for the region
SUBNETS=$(aws ec2 describe-subnets --filters "Name=vpc-id,Values=${VPC_ID}" "Name=availability-zone,Values=${AWS_REGION}a,${AWS_REGION}b,${AWS_REGION}c" --query 'Subnets[*].SubnetId' --output text | tr '\t' ',')
if [ -z "$SUBNETS" ]; then
    # Fallback: Try getting any subnet in the default VPC if specific AZs didn't return results
    SUBNETS=$(aws ec2 describe-subnets --filters "Name=vpc-id,Values=${VPC_ID}" --query 'Subnets[0:2].SubnetId' --output text | tr '\t' ',')
    if [ -z "$SUBNETS" ]; then
        echo "ERROR: Could not find any suitable subnets in the default VPC \(${VPC_ID}\) in region ${AWS_REGION}. Networking setup might be required."
        exit 1
    fi
    echo "WARN: Could not find subnets in standard AZs \(a,b,c\). Using first available subnets: ${SUBNETS}"
else
    echo "INFO: Using subnets: ${SUBNETS}"
fi

# Ensure the required CloudWatch Log Group exists
echo "INFO: Checking/Creating CloudWatch Log Group '/ecs/${ECS_TASK_FAMILY}'..."
aws logs describe-log-groups --log-group-name-prefix "/ecs/${ECS_TASK_FAMILY}" --region "${AWS_REGION}" | grep -q "/ecs/${ECS_TASK_FAMILY}" || \
aws logs create-log-group --log-group-name "/ecs/${ECS_TASK_FAMILY}" --region "${AWS_REGION}"

echo "INFO: Checking/Creating Security Group for the service..."
SG_NAME="${ECS_SERVICE_NAME}-sg"
SG_ID=$(aws ec2 describe-security-groups --filters Name=group-name,Values=${SG_NAME} Name=vpc-id,Values=${VPC_ID} --query 'SecurityGroups[0].GroupId' --output text --region ${AWS_REGION})
if [ "$SG_ID" == "None" ] || [ -z "$SG_ID" ]; then
    echo "INFO: Creating Security Group '${SG_NAME}'..."
    SG_ID=$(aws ec2 create-security-group --group-name "${SG_NAME}" --description "Allow inbound traffic on port ${CONTAINER_PORT} for ${ECS_SERVICE_NAME}" --vpc-id "${VPC_ID}" --query 'GroupId' --output text --region ${AWS_REGION})
    # Allow inbound traffic on the container port from anywhere (adjust 'CidrIp' as needed for security)
    aws ec2 authorize-security-group-ingress --group-id "${SG_ID}" --protocol tcp --port ${CONTAINER_PORT} --cidr 0.0.0.0/0 --region ${AWS_REGION}
    echo "INFO: Created Security Group ID: ${SG_ID}"
else
    echo "INFO: Using existing Security Group ID: ${SG_ID}"
fi

echo "INFO: Attempting to update ECS Service '${ECS_SERVICE_NAME}' first..."
# Try updating the service first. Capture stderr to check for specific errors.
UPDATE_OUTPUT=$(aws ecs update-service \
    --cluster "${ECS_CLUSTER_NAME}" \
    --service "${ECS_SERVICE_NAME}" \
    --task-definition "${TASK_DEFINITION_ARN}" \
    --force-new-deployment \
    --desired-count 1 \
    --region "${AWS_REGION}" 2>&1) # Redirect stderr to stdout and capture

UPDATE_EXIT_CODE=$?

# Check if update succeeded (exit code 0)
if [ ${UPDATE_EXIT_CODE} -eq 0 ]; then
    echo "INFO: Service '${ECS_SERVICE_NAME}' updated successfully."
# Check if update failed specifically because the service was not found
elif echo "${UPDATE_OUTPUT}" | grep -q "ServiceNotFoundException"; then
    echo "INFO: Service '${ECS_SERVICE_NAME}' not found. Creating..."
    # Attempt to create the service
    CREATE_OUTPUT=$(aws ecs create-service \
        --cluster "${ECS_CLUSTER_NAME}" \
        --service-name "${ECS_SERVICE_NAME}" \
        --task-definition "${TASK_DEFINITION_ARN}" \
        --launch-type "FARGATE" \
        --desired-count 1 \
        --network-configuration "awsvpcConfiguration={subnets=[${SUBNETS// /, }],securityGroups=[${SG_ID}],assignPublicIp=ENABLED}" \
        --region "${AWS_REGION}" 2>&1) # Capture output/error

    CREATE_EXIT_CODE=$?

    if [ ${CREATE_EXIT_CODE} -eq 0 ]; then
        echo "INFO: Service '${ECS_SERVICE_NAME}' created successfully."
    else
        echo "ERROR: Failed to create service '${ECS_SERVICE_NAME}'."
        echo "AWS CLI Output: ${CREATE_OUTPUT}"
        exit 1
    fi
# Handle other update errors
else
    echo "ERROR: Failed to update service '${ECS_SERVICE_NAME}'."
    echo "AWS CLI Output: ${UPDATE_OUTPUT}"
    exit 1
fi

echo "INFO: Deployment initiated for service '${ECS_SERVICE_NAME}' in cluster '${ECS_CLUSTER_NAME}'."
echo "INFO: It might take a few minutes for the service to become stable and the task to be running."
echo "INFO: Check the AWS Management Console \(ECS section\) for status and the public IP/DNS of your running task."

echo "INFO: Script finished." 