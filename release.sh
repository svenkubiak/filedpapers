#!/bin/bash
set -e

IMAGE_NAME="filedpapers"
IMAGE_NAME_METASCRAPER="filedpapers-metascraper"
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
mvn clean verify -DskipTests=true
IMAGE_VERSION=$(mvn help:evaluate -Dexpression=project.version -q -DforceStdout)
IMAGE_FULL_PATH="$GHCR_URL/$GHCR_USERNAME/$REPO_NAME/$IMAGE_NAME:$IMAGE_VERSION"
IMAGE_METASCRAPER_FULL_PATH="$GHCR_URL/$GHCR_USERNAME/$REPO_NAME/$IMAGE_NAME_METASCRAPER:$IMAGE_VERSION"
IMAGE_LATEST_PATH="$GHCR_URL/$GHCR_USERNAME/$REPO_NAME/$IMAGE_NAME:latest"
IMAGE_LATEST_METASCRAPER_PATH="$GHCR_URL/$GHCR_USERNAME/$REPO_NAME/$IMAGE_NAME_METASCRAPER:latest"

# Function to check if the release is stable
is_stable_release() {
    # Check if IMAGE_VERSION contains alpha, beta, or RC (case-insensitive)
    if [[ "$IMAGE_VERSION" =~ [aA]lpha|[bB]eta|[rR][cC] ]]; then
        return 1 # Not stable
    else
        return 0 # Stable
    fi
}

# Check the status before proceeding
if [ $STATUS -ne 0 ]; then
    echo "Failed to set new version! Exiting..."
    exit 1
else
    ### Filedpapers image ###
    echo "[Filedpapers] Building Version Docker image..."
    docker build --no-cache -t "$IMAGE_NAME:$IMAGE_VERSION" .

    # Check if this is a stable release
    if is_stable_release; then
        echo "[Filedpapers] Building Latest Docker image..."
        docker tag "$IMAGE_NAME:$IMAGE_VERSION" "$IMAGE_NAME:latest"
    else
        echo "[Filedpapers] Skipping Latest Docker image as this is a pre-release"
    fi

    echo "[Filedpapers] Tagging version as $IMAGE_FULL_PATH..."
    docker tag "$IMAGE_NAME:$IMAGE_VERSION" "$IMAGE_FULL_PATH"

    if is_stable_release; then
        echo "[Filedpapers] Tagging latest as $IMAGE_LATEST_PATH..."
        docker tag "$IMAGE_NAME:latest" "$IMAGE_LATEST_PATH"
    else
        echo "[Filedpapers] Skipping tagging latest as this is a pre-release"
    fi

    echo "[Filedpapers] Pushing version to GitHub Container Registry..."
    docker push "$IMAGE_FULL_PATH"

    if is_stable_release; then
        echo "[Filedpapers] Pushing latest to GitHub Container Registry..."
        docker push "$IMAGE_LATEST_PATH"
    else
        echo "[Filedpapers] Skipping push of latest as this is a pre-release"
    fi

    ### Filedpapers Metascraper ###

    cd metascraper

    echo "[Filedpapers-Metascraper] Building Version Docker image..."
    docker build --no-cache -t "$IMAGE_NAME_METASCRAPER:$IMAGE_VERSION" .

    # Check if this is a stable release
    if is_stable_release; then
        echo "[Filedpapers-Metascraper] Building Latest Docker image..."
        docker tag "$IMAGE_NAME_METASCRAPER:$IMAGE_VERSION" "$IMAGE_NAME_METASCRAPER:latest"
    else
        echo "[Filedpapers-Metascraper] Skipping build of Latest Docker image as this is a pre-release"
    fi

    echo "[Filedpapers-Metascraper] Tagging version as $IMAGE_FULL_PATH..."
    docker tag "$IMAGE_NAME_METASCRAPER:$IMAGE_VERSION" "$IMAGE_METASCRAPER_FULL_PATH"

    if is_stable_release; then
        echo "[Filedpapers-Metascraper] Tagging latest as $IMAGE_LATEST_METASCRAPER_PATH..."
        docker tag "$IMAGE_NAME_METASCRAPER:latest" "$IMAGE_LATEST_METASCRAPER_PATH"
    else
        echo "[Filedpapers-Metascraper] Skipping tag of latest as this is a pre-release"
    fi

    echo "[Filedpapers-Metascraper] Pushing version to GitHub Container Registry..."
    docker push "$IMAGE_METASCRAPER_FULL_PATH"

    if is_stable_release; then
        echo "[Filedpapers-Metascraper] Pushing latest to GitHub Container Registry..."
        docker push "$IMAGE_LATEST_METASCRAPER_PATH"
    else
        echo "[Filedpapers-Metascraper] Skipping push of latest as this is a pre-release"
    fi

    cd ..

    # Push tags and update versions if the push succeeds
    if [ $? -eq 0 ]; then
        echo "Tagging repo and pushing..."
        git tag $IMAGE_VERSION
        mvn release:update-versions
        git commit -am "Updated version after release"
        git push --tags origin main
        echo "Released!!!"
    else
        echo "Failed to push the image. Exiting..."
    exit 1
  fi
fi

rm pom.xml.versionsBackup
