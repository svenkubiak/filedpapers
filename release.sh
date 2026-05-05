#!/bin/bash
set -e

BOLD="\033[1m"
DIM="\033[2m"
RESET="\033[0m"
GREEN="\033[32m"
YELLOW="\033[33m"
RED="\033[31m"
CYAN="\033[36m"
BLUE="\033[34m"

TOTAL_STEPS=5
CURRENT_STEP=0

divider() {
  echo -e "${DIM}────────────────────────────────────────────────────────────────────────────────${RESET}"
}

step() {
  local msg="$1"
  CURRENT_STEP=$((CURRENT_STEP + 1))
  echo
  divider
  echo -e "  ${BOLD}${CYAN}[${CURRENT_STEP}/${TOTAL_STEPS}]${RESET}  ${BOLD}${msg}${RESET}"
  divider
  echo
}

info() {
  echo -e "  ${BLUE}→${RESET}  $1"
}

success() {
  echo -e "  ${GREEN}✔${RESET}  $1"
}

error() {
  echo -e "  ${RED}✖${RESET}  ${RED}$1${RESET}"
}

warn() {
  echo -e "  ${YELLOW}⚠${RESET}  ${YELLOW}$1${RESET}"
}

banner() {
  echo
  echo -e "${BOLD}${CYAN}╔══════════════════════════════════════════════════════════════════════════════╗${RESET}"
  echo -e "${BOLD}${CYAN}║                          🚀  Release Script                                  ║${RESET}"
  echo -e "${BOLD}${CYAN}╚══════════════════════════════════════════════════════════════════════════════╝${RESET}"
  echo
}

run_maven() {
  local description="$1"
  shift
  local args=("$@")
  local tmp_log
  tmp_log=$(mktemp)

  info "${description} ..."

  if ! mvn "${args[@]}" > "$tmp_log" 2>&1; then
    echo
    error "Maven command failed: mvn ${args[*]}"
    echo
    echo -e "${DIM}────────────────────── Maven Error Output ──────────────────────${RESET}"
    grep -E "\[ERROR\]|\[FATAL\]" "$tmp_log" | while IFS= read -r line; do
      echo -e "  ${RED}${line}${RESET}"
    done
    echo -e "${DIM}────────────────────────────────────────────────────────────────${RESET}"
    echo
    rm -f "$tmp_log"
    exit 1
  fi

  rm -f "$tmp_log"
}

run_silent() {
  local description="$1"
  shift
  local tmp_log
  tmp_log=$(mktemp)

  info "${description} ..."

  if ! "$@" > "$tmp_log" 2>&1; then
    echo
    error "Command failed: $*"
    echo
    echo -e "${DIM}────────────────────── Error Output ────────────────────────────${RESET}"
    while IFS= read -r line; do
      echo -e "  ${RED}${line}${RESET}"
    done < "$tmp_log"
    echo -e "${DIM}────────────────────────────────────────────────────────────────${RESET}"
    echo
    rm -f "$tmp_log"
    exit 1
  fi

  rm -f "$tmp_log"
}

bump_patch() {
  local ver="$1"
  IFS=. read -r major minor patch <<<"$ver"
  patch=$((patch + 1))
  echo "${major}.${minor}.${patch}"
}

is_stable_release() {
  if [[ "$IMAGE_VERSION" =~ [aA]lpha|[bB]eta|[rR][cC] ]]; then
    return 1
  else
    return 0
  fi
}

MODE="$1"
IMAGE_NAME="filedpapers"
IMAGE_NAME_METASCRAPER="filedpapers-metascraper"
GHCR_USERNAME="svenkubiak"
REPO_NAME="filedpapers"
GHCR_URL="ghcr.io"

SEMVER_REGEX='^(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)(-((0|[1-9][0-9]*|[0-9]*[A-Za-z-][0-9A-Za-z-]*)(\.(0|[1-9][0-9]*|[0-9]*[A-Za-z-][0-9A-Za-z-]*))*))?(\+[0-9A-Za-z-]+(\.[0-9A-Za-z-]+)*)?$'

banner

step "Checking Git state"

git status --short
if ! git diff-index --quiet HEAD --; then
  warn "There are uncommitted changes in the repository. They will be included in the release commit at the end."
else
  success "Git working directory is clean."
fi

step "Running Maven clean + verify"

run_maven "Running mvn clean verify" clean verify
echo
success "Maven build succeeded."

# === DEV MODE ===
if [[ "$MODE" == "dev" ]]; then
  TOTAL_STEPS=3
  step "Building and pushing dev images"

  IMAGE_DEV_PATH="$GHCR_URL/$GHCR_USERNAME/$REPO_NAME/$IMAGE_NAME:dev"
  IMAGE_METASCRAPER_DEV_PATH="$GHCR_URL/$GHCR_USERNAME/$REPO_NAME/$IMAGE_NAME_METASCRAPER:dev"

  warn "Dev mode — skipping Maven release/version updates."
  echo

  run_silent "Building image ${BOLD}${IMAGE_DEV_PATH}${RESET}" \
    docker build --no-cache -t "$IMAGE_NAME:dev" .
  run_silent "Tagging image" \
    docker tag "$IMAGE_NAME:dev" "$IMAGE_DEV_PATH"
  run_silent "Pushing image ${BOLD}${IMAGE_DEV_PATH}${RESET}" \
    docker push "$IMAGE_DEV_PATH"
  echo
  success "Filedpapers dev image pushed successfully."
  echo

  cd metascraper
  run_silent "Building image ${BOLD}${IMAGE_METASCRAPER_DEV_PATH}${RESET}" \
    docker build --no-cache -t "$IMAGE_NAME_METASCRAPER:dev" .
  run_silent "Tagging image" \
    docker tag "$IMAGE_NAME_METASCRAPER:dev" "$IMAGE_METASCRAPER_DEV_PATH"
  run_silent "Pushing image ${BOLD}${IMAGE_METASCRAPER_DEV_PATH}${RESET}" \
    docker push "$IMAGE_METASCRAPER_DEV_PATH"
  cd ..
  echo
  success "Filedpapers-Metascraper dev image pushed successfully."

  echo
  divider
  echo -e "  ${BOLD}${GREEN}🏁  All done!${RESET}  Dev images for ${BOLD}${IMAGE_NAME}${RESET} and ${BOLD}${IMAGE_NAME_METASCRAPER}${RESET} are live."
  divider
  echo
  exit 0
fi

# === REGULAR RELEASE MODE ===

step "Determining release version"

CURRENT_VERSION=$(mvn help:evaluate -Dexpression=project.version -q -DforceStdout)
DEFAULT_RELEASE_VERSION="${CURRENT_VERSION%-SNAPSHOT}"

info "Current version  :  ${BOLD}${CURRENT_VERSION}${RESET}"
echo
read -rp "  ✏️   Enter new release version [${DEFAULT_RELEASE_VERSION}]: " NEW_VERSION
NEW_VERSION="${NEW_VERSION:-$DEFAULT_RELEASE_VERSION}"
echo

if [[ ! "$NEW_VERSION" =~ $SEMVER_REGEX ]]; then
  error "Invalid Semantic Version (must follow SemVer 2.0.0): $NEW_VERSION"
  exit 1
fi

NEXT_DEV_BASE="$(bump_patch "$NEW_VERSION")"
NEXT_SNAPSHOT_VERSION="${NEXT_DEV_BASE}-SNAPSHOT"

run_maven "Setting version to ${NEW_VERSION}" versions:set -DnewVersion="$NEW_VERSION"
echo
success "Release version  :  ${BOLD}${NEW_VERSION}${RESET}"
info    "Next snapshot    :  ${BOLD}${NEXT_SNAPSHOT_VERSION}${RESET}"

step "Rebuilding with new version"

run_maven "Running mvn clean package" clean package -DskipTests
echo
success "Maven rebuild succeeded."

IMAGE_VERSION=$(mvn help:evaluate -Dexpression=project.version -q -DforceStdout)
IMAGE_FULL_PATH="$GHCR_URL/$GHCR_USERNAME/$REPO_NAME/$IMAGE_NAME:$IMAGE_VERSION"
IMAGE_METASCRAPER_FULL_PATH="$GHCR_URL/$GHCR_USERNAME/$REPO_NAME/$IMAGE_NAME_METASCRAPER:$IMAGE_VERSION"
IMAGE_LATEST_PATH="$GHCR_URL/$GHCR_USERNAME/$REPO_NAME/$IMAGE_NAME:latest"
IMAGE_LATEST_METASCRAPER_PATH="$GHCR_URL/$GHCR_USERNAME/$REPO_NAME/$IMAGE_NAME_METASCRAPER:latest"

step "Building and pushing Docker images"

info "Image version  :  ${BOLD}${IMAGE_VERSION}${RESET}"
if is_stable_release; then
  info "Release type   :  ${BOLD}stable${RESET} (latest tag will be pushed)"
else
  warn "Release type   :  pre-release (latest tag will be skipped)"
fi
echo

run_silent "Building image ${BOLD}${IMAGE_FULL_PATH}${RESET}" \
  docker build --no-cache -t "$IMAGE_NAME:$IMAGE_VERSION" .

if is_stable_release; then
  run_silent "Tagging as latest" docker tag "$IMAGE_NAME:$IMAGE_VERSION" "$IMAGE_NAME:latest"
fi

run_silent "Tagging ${BOLD}${IMAGE_FULL_PATH}${RESET}" \
  docker tag "$IMAGE_NAME:$IMAGE_VERSION" "$IMAGE_FULL_PATH"

if is_stable_release; then
  run_silent "Tagging ${BOLD}${IMAGE_LATEST_PATH}${RESET}" \
    docker tag "$IMAGE_NAME:latest" "$IMAGE_LATEST_PATH"
fi

run_silent "Pushing image ${BOLD}${IMAGE_FULL_PATH}${RESET}" docker push "$IMAGE_FULL_PATH"

if is_stable_release; then
  run_silent "Pushing image ${BOLD}${IMAGE_LATEST_PATH}${RESET}" docker push "$IMAGE_LATEST_PATH"
fi

echo
success "Filedpapers image pushed successfully."
echo

cd metascraper
run_silent "Building image ${BOLD}${IMAGE_METASCRAPER_FULL_PATH}${RESET}" \
  docker build --no-cache -t "$IMAGE_NAME_METASCRAPER:$IMAGE_VERSION" .

if is_stable_release; then
  run_silent "Tagging as latest" docker tag "$IMAGE_NAME_METASCRAPER:$IMAGE_VERSION" "$IMAGE_NAME_METASCRAPER:latest"
fi

run_silent "Tagging ${BOLD}${IMAGE_METASCRAPER_FULL_PATH}${RESET}" \
  docker tag "$IMAGE_NAME_METASCRAPER:$IMAGE_VERSION" "$IMAGE_METASCRAPER_FULL_PATH"

if is_stable_release; then
  run_silent "Tagging ${BOLD}${IMAGE_LATEST_METASCRAPER_PATH}${RESET}" \
    docker tag "$IMAGE_NAME_METASCRAPER:latest" "$IMAGE_LATEST_METASCRAPER_PATH"
fi

run_silent "Pushing image ${BOLD}${IMAGE_METASCRAPER_FULL_PATH}${RESET}" docker push "$IMAGE_METASCRAPER_FULL_PATH"

if is_stable_release; then
  run_silent "Pushing image ${BOLD}${IMAGE_LATEST_METASCRAPER_PATH}${RESET}" docker push "$IMAGE_LATEST_METASCRAPER_PATH"
fi
cd ..

echo
success "Filedpapers-Metascraper image pushed successfully."

step "Tagging Git and updating versions"

run_silent "Creating Git tag ${IMAGE_VERSION}" git tag "$IMAGE_VERSION"
run_maven "Setting next snapshot version ${NEXT_SNAPSHOT_VERSION}" versions:set -DnewVersion="${NEXT_SNAPSHOT_VERSION}"
rm -f pom.xml.versionsBackup
run_silent "Committing release ${IMAGE_VERSION}" git commit -am "Release ${IMAGE_VERSION}, next dev version ${NEXT_SNAPSHOT_VERSION}"
run_silent "Pushing to origin main" git push --tags origin main
echo
success "Git tag ${BOLD}${IMAGE_VERSION}${RESET} pushed and next dev version set  :  ${BOLD}${NEXT_SNAPSHOT_VERSION}${RESET}"

rm -f pom.xml.versionsBackup || true

echo
divider
echo -e "  ${BOLD}${GREEN}🏁  All done!${RESET}  ${BOLD}${IMAGE_NAME}${RESET} and ${BOLD}${IMAGE_NAME_METASCRAPER}${RESET} version ${BOLD}${GREEN}${IMAGE_VERSION}${RESET} released."
divider
echo