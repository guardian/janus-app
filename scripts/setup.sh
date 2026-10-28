#!/usr/bin/env bash

set -e

# Create a placeholder directory for janus-app config in /etc/gu/s3-sync
sudo mkdir -p /etc/gu/s3-sync
sudo chown -R $(whoami):staff /etc/gu

aws --profile security --region eu-west-1 sts get-caller-identity >/dev/null
if [[ $? -ne 0 ]]; then
  echo "You need to fetch suitable privileges for running Janus locally"
  exit 1
else
  echo "Privileges for running Janus locally are present"
fi

# Download configuration to run Janus locally from S3 into ~/.gu/janus-app
config_dir="/etc/gu/s3-sync"
config_files=("janus.conf" "janus-service-account-cert.json" "janusData.conf")

for source_file in "${config_files[@]}"; do
  destination="$config_dir/$source_file"

  if [[ ! -f "$destination" ]]; then
    aws --region eu-west-1 s3 cp \
      "s3://security-dist/security/DEV/janus/$source_file" "$destination" \
      --profile security
  else
    echo "${destination##*/} is present"
  fi
done

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

# Create tables - will fail if they exist.
"$(dirname "${BASH_SOURCE[0]}")/setup_dynamodb.sh"

echo "Setup is complete"
