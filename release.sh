#!/bin/bash

# Exit on any error
set -e

# Variables
IMAGE_NAME="filedpapers"
IMAGE_VERSION="1.0.0-Alpha2"
GHCR_USERNAME="svenkubiak"
REPO_NAME="filedpapers"
GHCR_URL="ghcr.io"

# Full image path
IMAGE_FULL_PATH="$GHCR_URL/$GHCR_USERNAME/$REPO_NAME/$IMAGE_NAME:$IMAGE_VERSION"

mvn clean package

# Ensure Docker is installed
if ! command -v docker &> /dev/null
then
    echo "Docker not found. Please install Docker."
    exit 1
fi

# Build Docker image
echo "Building Docker image..."
docker build -t "$IMAGE_NAME:$IMAGE_VERSION" .

# Tag image for GHCR
echo "Tagging image as $IMAGE_FULL_PATH..."
docker tag "$IMAGE_NAME:$IMAGE_VERSION" "$IMAGE_FULL_PATH"

# Push image to GHCR
echo "Pushing image to GitHub Container Registry..."
docker push "$IMAGE_FULL_PATH"

# Verify the push
if [ $? -eq 0 ]; then
    echo "Image successfully pushed to $IMAGE_FULL_PATH"
else
    echo "Failed to push the image. Please check the logs."
    exit 1
fi

echo "Done!"
