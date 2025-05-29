#!/bin/bash

# Script to download JWT and JSON files from URLs and process them using existing scripts

set -e

JSON_URL="https://raw.githubusercontent.com/passkeydeveloper/passkey-authenticator-aaguids/refs/heads/main/combined_aaguid.json"
JWT_URL="https://mds3.fidoalliance.org/"

# Define temporary and output files
TEMP_DIR=$(mktemp -d)
JWT_FILE="$TEMP_DIR/blob.jwt"
JSON_FILE="$TEMP_DIR/combined_aaguid.json"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CONF_DIR="$SCRIPT_DIR/../conf"

echo "Downloading files..."

# Download JWT file
echo "Downloading JWT from: $JWT_URL"
if ! curl -s -L -o "$JWT_FILE" "$JWT_URL"; then
    echo "Error: Failed to download JWT file from $JWT_URL"
    rm -rf "$TEMP_DIR"
    exit 1
fi

# Download JSON file
echo "Downloading JSON from: $JSON_URL"
if ! curl -s -L -o "$JSON_FILE" "$JSON_URL"; then
    echo "Error: Failed to download JSON file from $JSON_URL"
    rm -rf "$TEMP_DIR"
    exit 1
fi

echo "Processing files..."

# Change to temp directory for processing
cd "$TEMP_DIR"

# Copy decode_jwt.sh to temp directory and run it
cp "$SCRIPT_DIR/../scripts/decode_jwt.sh" .
chmod +x decode_jwt.sh
./decode_jwt.sh

# Copy aaguid_map_data.sh and its dependencies to temp directory
cp "$SCRIPT_DIR/aaguid_map_data.sh" .
cp "$SCRIPT_DIR/aaguid_map_data.jq" .
chmod +x aaguid_map_data.sh

# Run the mapping generation
./aaguid_map_data.sh

# Copy output back to conf directory
cp aaguid_descriptions.json "$CONF_DIR/"

echo "Process completed successfully!"
echo "Output file: $CONF_DIR/aaguid_descriptions.json"

# Clean up
cd "$SCRIPT_DIR"
rm -rf "$TEMP_DIR"
