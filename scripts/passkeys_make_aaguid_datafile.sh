#!/bin/bash
#
# Passkey AAGUID Data File Generator
#
# This script creates a comprehensive JSON mapping file that maps passkey AAGUIDs
# (Authenticator Attestation GUIDs) to authenticator device information including
# descriptions and icons. This data is used to provide user-friendly names for
# passkey authenticators in the application.
#
# Data sources:
#   1. Community Database: GitHub repository with community-maintained AAGUID data
#      URL: https://github.com/passkeydeveloper/passkey-authenticator-aaguids
#   2. Official FIDO MDS: FIDO Alliance Metadata Service (JWT format)
#      URL: https://mds3.fidoalliance.org/
#
# Process:
#   1. Downloads both data sources
#   2. Decodes the FIDO MDS JWT using decode_jwt.sh
#   3. Merges both datasets using passkeys_aaguid_map_data.sh and .jq
#   4. Outputs final mapping to conf/passkeys_aaguid_descriptions.json
#
# Dependencies:
#   - curl (for downloading files)
#   - jq (for JSON processing)
#   - decode_jwt.sh (for JWT decoding)
#   - passkeys_aaguid_map_data.sh (for data merging)
#   - passkeys_aaguid_map_data.jq (jq script for data processing)
#
# Output:
#   - conf/passkeys_aaguid_descriptions.json
#
# Usage:
#   ./passkeys_make_aaguid_datafile.sh
#
# Note: The official FIDO MDS is updated roughly monthly and contains metadata
# about when to check for updates next.

set -e

JSON_URL="https://raw.githubusercontent.com/passkeydeveloper/passkey-authenticator-aaguids/refs/heads/main/combined_aaguid.json"
JWT_URL="https://mds3.fidoalliance.org/"

# Define temporary and output files
TEMP_DIR=$(mktemp -d)
trap 'rm -rf "$TEMP_DIR"' EXIT
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
cp "$SCRIPT_DIR/decode_jwt.sh" .
chmod +x decode_jwt.sh
./decode_jwt.sh

# Copy passkeys_aaguid_map_data.sh and its dependencies to temp directory
cp "$SCRIPT_DIR/passkeys_aaguid_map_data.sh" .
cp "$SCRIPT_DIR/passkeys_aaguid_map_data.jq" .
chmod +x passkeys_aaguid_map_data.sh

# Run the mapping generation
./passkeys_aaguid_map_data.sh

# Copy output back to conf directory
cp passkeys_aaguid_descriptions.json "$CONF_DIR/"

echo "Process completed successfully!"
echo "Output file: $CONF_DIR/passkeys_aaguid_descriptions.json"

# Clean up
cd "$SCRIPT_DIR"
rm -rf "$TEMP_DIR"
