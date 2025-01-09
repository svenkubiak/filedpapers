#!/bin/bash
set -e

IMAGE_NAME="filedpapers"
GHCR_USERNAME="svenkubiak"
REPO_NAME="filedpapers"
GHCR_URL="ghcr.io"

mvn release:clean
mvn clean verify

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
IMAGE_LATEST_PATH="$GHCR_URL/$GHCR_USERNAME/$REPO_NAME/$IMAGE_NAME:latest"

is_stable_release() {
    if [[ "$IMAGE_VERSION" =~ (?i)alpha|beta|RC ]]; then
        return 0
    else
        return 1
    fi
}

if [ $STATUS -ne 0 ]; then
  echo "Failed to set new version! Exiting..."
  exit 1
else
  echo "Building Version Docker image..."
  docker build --no-cache -t "$IMAGE_NAME:$IMAGE_VERSION" .

  if is_stable_release; then
    echo "Building Latest Docker image..."
    docker build --no-cache -t "$IMAGE_NAME:latest" .
  else
    echo "Skipping build of Latest Docker image as this is pre-release"
  fi

  echo "Tagging version as $IMAGE_FULL_PATH..."
  docker tag "$IMAGE_NAME:$IMAGE_VERSION" "$IMAGE_FULL_PATH"

  if is_stable_release; then
    echo "Tagging latest as $IMAGE_LATEST_PATH..."
    docker tag "$IMAGE_NAME:latest" "$IMAGE_LATEST_PATH"
  else
    echo "Skipping tag latest as this is a pre-release"
  fi

  echo "Pushing version to GitHub Container Registry..."
  docker push "$IMAGE_FULL_PATH"

  if is_stable_release; then
    echo "Pushing latest to GitHub Container Registry..."
    docker push "$IMAGE_LATEST_PATH"
  else
    echo "Skipping push latest as this is a pre-release"
  fi

  if [ $? -eq 0 ]; then
      echo "Image successfully pushed to $IMAGE_FULL_PATH"
      git tag $IMAGE_VERSION
      mvn release:update-versions
      git commit -am "Updated version after release"
      git push --tags origin main
      echo "Released!"
  else
      echo "Failed to push the image. Exiting..."
      exit 1
  fi
fi

rm pom.xml.versionsBackup