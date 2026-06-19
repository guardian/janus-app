#!/usr/bin/env bash

set -e

# Create a placeholder directory for janus-app config in ~/.gu
mkdir -p ~/.gu/janus-app/data
chown -R $(whoami):staff ~/.gu/janus-app/data

aws --profile security --region eu-west-1 sts get-caller-identity >/dev/null
if [[ $? -ne 0 ]]; then
  echo "You need to fetch suitable privileges for running Janus locally"
  exit 1
else
  echo "Privileges for running Janus locally are present"
fi

# Download configuration to run Janus locally from S3 into ~/.gu/janus-app
if [[ ! -f ~/.gu/janus-app/janus.local.conf ]]; then
  aws --region eu-west-1 s3 cp \
    s3://security-dist/security/DEV/janus/janus.conf ~/.gu/janus-app/janus.local.conf \
    --profile security
else
  echo "janus.local.conf is present"
fi

if [[ ! -f ~/.gu/janus-app/janus-service-account-cert.json ]]; then
  aws --region eu-west-1 s3 cp s3://security-dist/security/DEV/janus/janus-service-account-cert.json \
    ~/.gu/janus-app \
    --profile security
else
  echo "janus-service-account-cert.json is present"
fi

docker compose -f local-dev/docker-compose.yml up -d
sleep 2

# Create tables - will fail if they exist, but can be run manually with "destroy" or "recreate"
sbt "setup / run create"

if [[ ! -f ~/.gu/janus-app/janusData.conf ]]; then
   echo "!!! You need to copy in a janusData.conf file at ~/.gu/janus-app/janusData.conf"
   exit 1
else
  echo "janusData.conf is present"
fi

# Need AWS profile 'janus' to test access to dev-playground account.
# See Readme.md 'Janus AWS Profile' section.
aws --profile security --region eu-west-1 ssm get-parameter \
  --name "/DEV/security/janus/janus.aws.profile" \
  --with-decryption \
  --query "Parameter.Value" \
  --output text \
  | aws configure import --csv file:///dev/stdin

docker compose -f local-dev/docker-compose.yml up -d
sleep 2
TABLE_COUNT=$(aws dynamodb list-tables --profile security --region eu-west-1 --endpoint http://localhost:8000 | jq '.TableNames|length')
if [[ $TABLE_COUNT -eq 0 ]]; then
   echo 'Turning on tests that create tables by searching for <"create table" ignore>'
   sed -i 's/"create table" ignore/"create table" in/' $(grep -rl '"create table" ignore' test/)
   echo "Running tests to create tables"
   sbt "testOnly *DBTest*"
   echo "Turning off tests that create tables"
   sed -i 's/"create table" in/"create table" ignore/' $(grep -rl '"create table" in' test/)
   git status
   aws dynamodb list-tables --profile security --region eu-west-1 --endpoint http://localhost:8000
else
  echo "$TABLE_COUNT tables found; not running table create tests"
fi

echo "Setup is complete"
