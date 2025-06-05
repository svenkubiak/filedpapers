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

# Create an .env file and add configuration variables
echo "Creating .env file..."

cat > .env <<EOL
# Custom configuration
VERSION=latest

APPLICATION_URL=http://localhost
ALLOW_REGISTRATION=true
SMTP_HOST=localhost
SMTP_PORT=25
SMTP_AUTHENTICATION=true
SMTP_USERNAME=username
SMTP_PASSWORD=password
SMTP_FROM=email@localhost
SMTP_PROTOCOL=smtptls
SMTP_DEBUG=false

# Auto generated - Change at your own risk
MONGODB_INITDB_DATABASE=filedpapers
MONGODB_INITDB_ROOT_USERNAME=filedpapers
MONGODB_INITDB_ROOT_PASSWORD=$(generate_secret)
APPLICATION_SECRET=$(generate_secret)
ACCESS_TOKEN_SECRET=$(generate_secret)
REFRESH_TOKEN_SECRET=$(generate_secret)
CHALLENGE_TOKEN_SECRET=$(generate_secret)
SESSION_SECRET=$(generate_secret)
AUTHENTICATION_SECRET=$(generate_secret)
FLASH_SECRET=$(generate_secret)
EOL

# Create logs folder
echo "Creating logs folder..."
mkdir -p logs

# Create the config folder
echo "Creating config folder..."
mkdir -p config
cd config || { echo "Failed to enter config directory."; exit 1; }

# Download the default config.yaml (silent download)
echo "Downloading config.yaml..."
curl -s -O "$CONFIG_URL"

# Return to the installation directory
cd .. || { echo "Failed to return to installation directory."; exit 1; }

# Step 5: Download the compose.yaml (silent download)
echo "Downloading compose.yaml..."
curl -s -O "$COMPOSE_URL"

# Step 6: Installation complete
echo "Installation complete!"
curl -s -O "$COMPOSE_URL"

echo ""
echo "Please configure your specific environment in your compose.yaml and remove this shell script."
echo "Enjoy Filed Papers!"