#!/bin/bash
#
# Passkey Community AAGUID Data Refresher
#
# Refreshes the committed community AAGUID database used as a fallback for
# passkey authenticator descriptions and icons.
#
# FIDO-certified authenticators are resolved at runtime from the FIDO Metadata
# Service (MDS) by the webauthn4j-metadata library (see PasskeyMetadataService),
# so only the community database needs to be committed. The community database
# covers common platform authenticators (e.g. iCloud Keychain, Google Password
# Manager) that are not published to the FIDO MDS.
#
# Data source:
#   Community database (passkeydeveloper/passkey-authenticator-aaguids)
#   https://github.com/passkeydeveloper/passkey-authenticator-aaguids
#
# Output:
#   conf/passkeys_aaguid_community.json
#
# Usage:
#   ./passkeys_refresh_community_data.sh

set -e

JSON_URL="https://raw.githubusercontent.com/passkeydeveloper/passkey-authenticator-aaguids/refs/heads/main/combined_aaguid.json"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
OUTPUT_FILE="$SCRIPT_DIR/../conf/passkeys_aaguid_community.json"

echo "Downloading community AAGUID data from: $JSON_URL"
if ! curl -s -L -f -o "$OUTPUT_FILE" "$JSON_URL"; then
    echo "Error: Failed to download community AAGUID data from $JSON_URL"
    exit 1
fi

echo "Process completed successfully!"
echo "Output file: $OUTPUT_FILE"
