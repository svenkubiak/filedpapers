#!/bin/bash

# Exit on any error
set -e

# Variables
IMAGE_NAME="filedpapers"
GHCR_USERNAME="svenkubiak"
REPO_NAME="filedpapers"
GHCR_URL="ghcr.io"

mvn release:clean
mvn clean verify

# Check if the Maven build was successful
if [ $? -ne 0 ]; then
  echo "Maven build failed! Exiting..."
  exit 1
else
  echo "Maven build succeeded."
fi

mvn versions:set
STATUS=$?
IMAGE_VERSION=$(mvn help:evaluate -Dexpression=project.version -q -DforceStdout)
IMAGE_FULL_PATH="$GHCR_URL/$GHCR_USERNAME/$REPO_NAME/$IMAGE_NAME:$IMAGE_VERSION"

if [ $STATUS -ne 0 ]; then
  echo "Failed to set new version! Exiting..."
  exit 1
else
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
      git tag $IMAGE_VERSION
      mvn release:update-versions
      git commit -am "Updated version after release"
      git push --tags origin main
  else
      echo "Failed to push the image. Exiting..."
      exit 1
  fi
fi

echo "Released!"
