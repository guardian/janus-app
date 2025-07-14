#!/bin/bash
set -euo pipefail

#
# This script deletes all passkeys belonging to a given user
# from the production Janus Passkeys table.
# It's designed to be run locally when there's a problem with someone's passkeys.
# Removing all their passkeys so that they can start again is the easiest solution to most problems.
#
# Requirements:
# - AWS CLI installed
# - jQ installed
# - AWS CLI configured with 'security' profile
# - Permission to write to DynamoDB service
#
# Arguments:
# - username whose passkeys to delete
# - --dry-run (optional): show what would be deleted without actually deleting
#

check_dependencies() {
    local missing_deps=()

    if ! command -v aws &> /dev/null; then
        missing_deps+=("aws")
    fi

    if ! command -v jq &> /dev/null; then
        missing_deps+=("jq")
    fi

    if [ ${#missing_deps[@]} -gt 0 ]; then
        echo "Error: Missing required dependencies: ${missing_deps[*]}"
        echo "Please install the missing tools and try again."
        exit 1
    fi
}

# Check dependencies first
check_dependencies

# Parse arguments
DRY_RUN=false
USERNAME=""

while [[ $# -gt 0 ]]; do
    case $1 in
        --dry-run)
            DRY_RUN=true
            shift
            ;;
        *)
            USERNAME="$1"
            shift
            ;;
    esac
done

# Check if username argument is provided
if [ -z "$USERNAME" ]; then
    echo "Usage: $0 <username> [--dry-run]"
    exit 1
fi

PROFILE="security"
REGION="eu-west-1"

if [ "$DRY_RUN" = true ]; then
    echo "[DRY RUN] Would delete all passkeys for username: $USERNAME"
else
    echo "Deleting all passkeys for username: $USERNAME"
fi

# First, scan the table to get all items with the given username
ITEMS=$(aws dynamodb scan \
    --table-name Passkeys \
    --filter-expression "username = :username" \
    --expression-attribute-values '{":username": {"S": "'$USERNAME'"}}' \
    --region $REGION \
    --profile $PROFILE \
    --output json)

# Extract credentialId values and delete each item
echo "$ITEMS" | jq -r '.Items[] | .credentialId.S' | while read -r CREDENTIAL_ID; do
    if [ "$DRY_RUN" = true ]; then
        echo "[DRY RUN] Would delete item with credentialId: $CREDENTIAL_ID"
    else
        echo "Deleting item with credentialId: $CREDENTIAL_ID"
        aws dynamodb delete-item \
            --table-name Passkeys \
            --key '{"credentialId": {"S": "'$CREDENTIAL_ID'"}, "username": {"S": "'$USERNAME'"}}' \
            --region $REGION \
            --profile $PROFILE
    fi
done

if [ "$DRY_RUN" = true ]; then
    echo "[DRY RUN] Would have completed deletion for username: $USERNAME"
else
    echo "Deletion complete for username: $USERNAME"
fi
