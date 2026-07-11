#!/bin/bash

# Exit on any error
set -e

SERVICES=(
  "frontend:./frontend"
  "api-gateway:./services/api-gateway"
)

for item in "${SERVICES[@]}" ; do
    IMAGE_NAME="sagar8/infra-${item%%:*}"
    BUILD_PATH="${item#*:}"
    
    echo "----------------------------------------"
    echo "Building $IMAGE_NAME:latest..."
    echo "----------------------------------------"
    docker build --platform linux/amd64 -t "${IMAGE_NAME}:latest" "${BUILD_PATH}"
    
    echo "----------------------------------------"
    echo "Pushing $IMAGE_NAME:latest..."
    echo "----------------------------------------"
    docker push "${IMAGE_NAME}:latest"
done

echo "All images built and pushed successfully!"
