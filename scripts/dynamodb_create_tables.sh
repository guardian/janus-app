#!/usr/bin/env bash

# Create DynamoDB tables if they do not exist in the local DynamoDB instance
# This script is not updated automatically and must be kept up to date with
# the PROD schema.

set -euo pipefail

export AWS_ENDPOINT_URL="${AWS_ENDPOINT_URL:-http://localhost:8000}"
export AWS_DEFAULT_REGION=eu-west-1
export AWS_ACCESS_KEY_ID=fakeMyKeyId
export AWS_SECRET_ACCESS_KEY=fakeSecretAccessKey
export AWS_PAGER=""

create_table_if_missing() {
  local table_name="$1"
  shift
  local error

  if error=$(aws dynamodb describe-table --table-name "$table_name" 2>&1); then
    echo "$table_name already exists"
  elif [[ "$error" == *"(ResourceNotFoundException)"* ]]; then
    echo "$table_name does not exist, creating..."
    aws dynamodb create-table --table-name "$table_name" "$@"
  else
    printf '%s\n' "$error" >&2
    return 1
  fi
}

echo "Creating DynamoDB tables if they do not exist..."

create_table_if_missing AuditTrail \
  --key-schema \
    AttributeName=j_account,KeyType=HASH \
    AttributeName=j_timestamp,KeyType=RANGE \
  --attribute-definitions \
    AttributeName=j_account,AttributeType=S \
    AttributeName=j_timestamp,AttributeType=N \
    AttributeName=j_username,AttributeType=S \
  --global-secondary-indexes '[{
    "IndexName": "AuditTrailByUser",
    "KeySchema": [
      {"AttributeName": "j_username", "KeyType": "HASH"},
      {"AttributeName": "j_timestamp", "KeyType": "RANGE"}
    ],
    "Projection": {"ProjectionType": "ALL"},
    "ProvisionedThroughput": {"ReadCapacityUnits": 15, "WriteCapacityUnits": 15}
  }]' \
  --provisioned-throughput ReadCapacityUnits=15,WriteCapacityUnits=15

create_table_if_missing PasskeyChallenges \
  --key-schema \
    AttributeName=username,KeyType=HASH \
    AttributeName=flow,KeyType=RANGE \
  --attribute-definitions \
    AttributeName=username,AttributeType=S \
    AttributeName=flow,AttributeType=S \
  --provisioned-throughput ReadCapacityUnits=15,WriteCapacityUnits=15

create_table_if_missing Passkeys \
  --key-schema \
    AttributeName=username,KeyType=HASH \
    AttributeName=credentialId,KeyType=RANGE \
  --attribute-definitions \
    AttributeName=username,AttributeType=S \
    AttributeName=credentialId,AttributeType=S \
  --provisioned-throughput ReadCapacityUnits=15,WriteCapacityUnits=15
