#!/bin/bash

# This script is used by the passkeys_make_aaguid_datafile.sh script.
#
# Script to merge combined_aaguid.json and decoded_jwt.json into a single output file
# with aaguid to description and icon mappings

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
