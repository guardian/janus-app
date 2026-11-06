#!/bin/bash
#
# AAGUID Data Merger for Passkeys
#
# This script merges AAGUID (Authenticator Attestation GUID) data from two sources:
# 1. combined_aaguid.json - Community-driven passkey authenticator database
# 2. decoded_jwt.json - Official FIDO Alliance Metadata Service (MDS) data
#
# The script uses an external jq script (passkeys_aaguid_map_data.jq) to process
# and merge the data, creating a unified mapping of AAGUIDs to their descriptions
# and icons for identifying types of passkey.
#
# Input files:
#   - combined_aaguid.json: Community AAGUID data
#   - decoded_jwt.json: Decoded FIDO MDS JWT payload
#   - passkeys_aaguid_map_data.jq: jq processing script
#
# Output:
#   - passkeys_aaguid_descriptions.json: Merged AAGUID mappings
#
# Usage:
#   ./passkeys_aaguid_map_data.sh
#
# Called by: passkeys_make_aaguid_datafile.sh

set -e

INPUT_FILE_1="combined_aaguid.json"
INPUT_FILE_2="decoded_jwt.json"
OUTPUT_FILE="passkeys_aaguid_descriptions.json"
JQ_SCRIPT="passkeys_aaguid_map_data.jq"

# Check if input files exist
if [[ ! -f "$INPUT_FILE_1" ]]; then
    echo "Error: $INPUT_FILE_1 not found"
    exit 1
fi

if [[ ! -f "$INPUT_FILE_2" ]]; then
    echo "Error: $INPUT_FILE_2 not found"
    exit 1
fi

if [[ ! -f "$JQ_SCRIPT" ]]; then
    echo "Error: $JQ_SCRIPT not found"
    exit 1
fi

# Use jq with external script to merge the files
jq -s -f "$JQ_SCRIPT" "$INPUT_FILE_1" "$INPUT_FILE_2" > "$OUTPUT_FILE"

echo "Successfully merged $INPUT_FILE_1 and $INPUT_FILE_2 into $OUTPUT_FILE"
echo "Total mappings created: $(jq 'length' "$OUTPUT_FILE")"
echo "Each mapping contains 'description' and 'icon' fields where available"
