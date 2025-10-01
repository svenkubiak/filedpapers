#!/bin/bash

# Set variables
CONFIG_URL="https://raw.githubusercontent.com/svenkubiak/filedpapers/refs/heads/main/config.yaml"
COMPOSE_URL="https://raw.githubusercontent.com/svenkubiak/filedpapers/refs/heads/main/compose.yaml"

# Fix locale to avoid "Illegal byte sequence" error
export LC_CTYPE=C.UTF-8

# Generate a random 64-character secret (retained for future use)
generate_secret() {
  tr -dc 'A-Za-z0-9' </dev/urandom | head -c 64
}

# Create an .env file and add configuration variables
echo "1/6 Creating .env file..."

MONGODB_USERNAME=filedpapers
MONGODB_PASSWORD=$(generate_secret)

cat > .env <<EOL
# Custom configuration
VERSION=latest

APPLICATION_URL=http://localhost
APPLICATION_REGISTRATION=true
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
MONGODB_INITDB_ROOT_USERNAME=${MONGODB_USERNAME}
MONGODB_INITDB_ROOT_PASSWORD=${MONGODB_PASSWORD}
PERSISTENCE_MONGO_USERNAME=${MONGODB_USERNAME}
PERSISTENCE_MONGO_PASSWORD=${MONGODB_PASSWORD}
APPLICATION_SECRET=$(generate_secret)
API_ACCESSTOKEN_SECRET=$(generate_secret)
API_ACCESSTOKEN_KEY=$(generate_secret)
API_REFRESHTOKEN_SECRET=$(generate_secret)
API_REFRESHTOKEN_KEY=$(generate_secret)
API_CHALLENGETOKEN_SECRET=$(generate_secret)
API_CHALLENGETOKEN_KEY=$(generate_secret)
SESSION_COOKIE_SECRET=$(generate_secret)
SESSION_COOKIE_KEY=$(generate_secret)
AUTHENTICATION_COOKIE_SECRET=$(generate_secret)
AUTHENTICATION_COOKIE_KEY=$(generate_secret)
FLASH_COOKIE_SECRET=$(generate_secret)
FLASH_COOKIE_KEY=$(generate_secret)
EOL

if [ ! -d "logs" ]; then
  echo "2/6 Creating logs folder..."
  mkdir "logs"
else
  echo "[Skipping] Logs folder already exists."
fi

# Create config folder if it does not exist
if [ ! -d "config" ]; then
  echo "3/6 Creating config folder..."
  mkdir "config"
else
  echo "[Skipping] Config folder already exists."
fi
cd config || { echo "Failed to enter config directory."; exit 1; }

# Download the default config.yaml (silent download)
echo "4/6 Downloading config.yaml..."
curl -s -O "$CONFIG_URL"

# Return to the installation directory
cd .. || { echo "Failed to return to installation directory."; exit 1; }

# Step 5: Download the compose.yaml (silent download)
echo "5/6 Downloading compose.yaml..."
curl -s -O "$COMPOSE_URL"

# Step 6: Installation complete
echo "6/6 Installation complete!"
echo ""
echo "Please configure your specific environment in your compose.yaml and remove this script."
echo "Enjoy Filed Papers!"