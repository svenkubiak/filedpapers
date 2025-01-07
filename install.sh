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
PASSWORD=$(generate_secret)  # Generate a secret password

cat > .env <<EOL
MONGODB_INITDB_ROOT_USERNAME=filedpapers
MONGODB_INITDB_ROOT_PASSWORD=$PASSWORD
MONGODB_INITDB_DATABASE=filedpapers
EOL

# Step 2: Create the config folder
echo "2. Creating config folder..."
mkdir -p config
cd config || { echo "Failed to enter config directory."; exit 1; }

# Step 3: Download the default config.yaml (silent download)
echo "3. Downloading config.yaml..."
curl -s -O "$CONFIG_URL"

# Step 4: Replace the password in the config.yaml file with the generated secret
echo "4. Replacing 'password: filedpapers' with the new secret in config.yaml..."

# Using sed to replace the password in the YAML file (Linux-compatible)
sed -i "s/password: filedpapers/password: $PASSWORD/" config.yaml

# Step 5: Return to the installation directory
cd .. || { echo "Failed to return to installation directory."; exit 1; }

# Step 6: Download the compose.yaml (silent download)
echo "7. Downloading compose.yaml..."
curl -s -O "$COMPOSE_URL"

echo "Installation complete. Please configure your environment and run the Docker containers manually."
echo "----------------------------"
echo "Reminder: Remove this shell script"
echo "!!!IMPORTANT Update all secrets in config/config.yaml with at least 64 characters IMPORTANT!!!!"