#!/bin/bash

# Define paths
JWT_FILE="blob.jwt"
OUTPUT_FILE="decoded_jwt.json"

# Check if the JWT file exists
if [ ! -f "$JWT_FILE" ]; then
    echo "Error: JWT file not found at $JWT_FILE"
    exit 1
fi

# Read the JWT token from file
JWT=$(cat "$JWT_FILE")

if [ -z "$JWT" ]; then
    echo "Error: JWT file is empty"
    exit 1
fi

echo "Processing MDS3 format JWT token..."

# Split the JWT into parts
IFS='.' read -ra JWT_PARTS <<< "$JWT"

if [ ${#JWT_PARTS[@]} -ne 3 ]; then
    echo "Error: Invalid JWT format. Expected 3 parts (header.payload.signature)"
    exit 1
fi

# Function to decode base64url to base64 and then decode
decode_base64() {
    local input=$1
    # Add padding if needed
    local mod4=$((${#input} % 4))
    if [ $mod4 -eq 2 ]; then input="${input}=="; 
    elif [ $mod4 -eq 3 ]; then input="${input}="; fi
    
    # Replace URL-safe characters
    input=$(echo "$input" | tr -d '\n' | tr '_-' '/+')
    
    # Decode
    echo "$input" | base64 --decode
}

# Decode payload (MDS3 format is in the payload)
PAYLOAD=$(decode_base64 "${JWT_PARTS[1]}")

# Format and save as JSON
echo "$PAYLOAD" | jq '.' > "$OUTPUT_FILE" 2>/dev/null

# If jq is not available or fails, try a basic approach
if [ $? -ne 0 ]; then
    echo "Warning: jq not available or failed to format. Saving raw decoded payload."
    echo "$PAYLOAD" > "$OUTPUT_FILE"
fi

echo "Decoded JWT saved to $OUTPUT_FILE"

# Display a preview of the decoded content
echo "------------------------"
echo "Preview of decoded content:"
head -n 10 "$OUTPUT_FILE"
echo "------------------------"
echo "Decoding complete."
