#!/bin/bash

# Script to merge combined_aaguid.json and decoded_jwt.json into a single output file
# with aaguid to description mappings

set -e

INPUT_FILE_1="combined_aaguid.json"
INPUT_FILE_2="decoded_jwt.json"
OUTPUT_FILE="aaguid_descriptions.json"

# Check if input files exist
if [[ ! -f "$INPUT_FILE_1" ]]; then
    echo "Error: $INPUT_FILE_1 not found"
    exit 1
fi

if [[ ! -f "$INPUT_FILE_2" ]]; then
    echo "Error: $INPUT_FILE_2 not found"
    exit 1
fi

# Use jq to merge the files and create aaguid to description mappings
jq -s '
  # Combine both input arrays/objects
  .[0] as $file1 | .[1] as $file2 |
  
  # Initialize result object
  {} |
  
  # Process first file (combined_aaguid.json) - should be an array
  . + (
    if ($file1 | type) == "array" then
      $file1 | map(
        if (.aaguid // empty) and (.description // empty) then
          {(.aaguid): .description}
        else empty end
      ) | add // {}
    else
      {}
    end
  ) |
  
  # Process second file (decoded_jwt.json) - extract from entries array
  . + (
    if ($file2 | has("entries")) then
      $file2.entries | map(
        if (.aaguid // empty) and (.metadataStatement | type) == "object" and (.metadataStatement.description // empty) then
          {(.aaguid): .metadataStatement.description}
        else empty end
      ) | add // {}
    else
      {}
    end
  )
' "$INPUT_FILE_1" "$INPUT_FILE_2" > "$OUTPUT_FILE"

echo "Successfully merged $INPUT_FILE_1 and $INPUT_FILE_2 into $OUTPUT_FILE"
echo "Total mappings created: $(jq 'length' "$OUTPUT_FILE")"
