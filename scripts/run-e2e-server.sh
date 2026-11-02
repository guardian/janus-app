#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."
project="janus-e2e-$$-$RANDOM"
compose=(docker compose --project-name "$project" --file test/docker-compose.e2e.yml)
server_pid=""
started=false

cleanup() {
  local status=$?
  trap - EXIT INT TERM
  if [[ -n "$server_pid" ]] && kill -0 "$server_pid" 2>/dev/null; then
    if ! kill -TERM "$server_pid"; then
      echo "Could not signal E2E server process $server_pid" >&2
      status=1
    fi
    if wait "$server_pid"; then
      :
    else
      echo "E2E server stopped" >&2
    fi
  fi
  if [[ "$started" == true ]]; then
    if [[ "$status" -ne 0 && "$status" -ne 130 && "$status" -ne 143 ]]; then
      if ! "${compose[@]}" logs --no-color >&2; then
        echo "Could not collect E2E container logs for $project" >&2
      fi
    fi
    if ! "${compose[@]}" down --timeout 5; then
      echo "Could not clean up E2E Compose project $project" >&2
      status=1
    fi
  fi
  exit "$status"
}

trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

docker compose version
docker info --format '{{.ServerVersion}}'
echo "Starting isolated E2E database (Compose project: $project)"
started=true
"${compose[@]}" up --detach dynamodb
"${compose[@]}" run --rm --no-deps dynamodb-seed

export GOOGLE_AUTH_DISABLED=true
sbt "Test / runMain E2ETestServer" &
server_pid=$!
wait "$server_pid"
