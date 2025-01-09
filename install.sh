#!/bin/bash

# Set variables
CONFIG_URL="https://raw.githubusercontent.com/svenkubiak/filedpapers/refs/heads/main/config.yaml"
COMPOSE_URL="https://raw.githubusercontent.com/svenkubiak/filedpapers/refs/heads/main/compose.yml"

# Fix locale to avoid "Illegal byte sequence" error
export LC_CTYPE=C.UTF-8

# Generate a random 64-character secret (retained for future use)
generate_secret() {
  tr -dc 'A-Za-z0-9' </dev/urandom | head -c 64
}

# Step 1: Create an .env file and add configuration variables
echo "1. Creating .env file..."

cat > .env <<EOL
MONGODB_INITDB_DATABASE=filedpapers
MONGODB_INITDB_ROOT_USERNAME=filedpapers
MONGODB_INITDB_ROOT_PASSWORD=$(generate_secret)
ALLOW_REGISTRATION=true
APPLICATION_SECRET=$(generate_secret)
ACCESS_TOKEN_SECRET=$(generate_secret)
REFRESH_TOKEN_SECRET=$(generate_secret)
SESSION_SECRET=$(generate_secret)
AUTHENTICATION_SECRET=$(generate_secret)
FLASH_SECRET=$(generate_secret)
EOL

# Step 2: Create the config folder
echo "2. Creating config folder..."
mkdir -p config
cd config || { echo "Failed to enter config directory."; exit 1; }

# Step 3: Download the default config.yaml (silent download)
echo "3. Downloading config.yaml..."
curl -s -O "$CONFIG_URL"

# Step 5: Return to the installation directory
cd .. || { echo "Failed to return to installation directory."; exit 1; }

# Step 6: Download the compose.yaml (silent download)
echo "7. Downloading compose.yaml..."
curl -s -O "$COMPOSE_URL"

echo "Installation complete. Please configure your environment in your compose.yaml if required."
echo "----------------------------"
echo "Reminder: Remove this shell script"