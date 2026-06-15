#!/usr/bin/env bash

set -e

# Create a placeholder directory for janus-app config in ~/.gu
mkdir -p ~/.gu/janus-app/data
chown -R $(whoami):$(whoami) ~/.gu/janus-app/data

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

if [[ ! -f ~/.gu/janus-app/janusData.conf ]]; then
   echo "You need to copy in the janusData file: ~/.gu/janus-app/janusData.conf"
   exit 1
else
  echo "janusData.conf is present"
fi

docker compose -f local-dev/docker-compose.yml up -d
aws dynamodb list-tables --profile security --region eu-west-1 --endpoint http://localhost:8000
cat <<EOF
If the list of table above is not populated, change each of the tests called "create table" from "ignore"
to "in" and run the following:

sbt "testOnly *DBTest*"

EOF
