#!/bin/bash

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

CONFIGPATH=$HOME/.gu/janus-app
CONFIG=$CONFIGPATH/janus.local.conf
CONFIGDATA=$CONFIGPATH/janusData.conf

if [[ ! -w $CONFIGPATH ]]; then
  echo "Configpath $CONFIGPATH is not writable - docker db will not start"
  exit 1
fi

if [[ ! -f $CONFIG ]]; then
  echo "No config found at $CONFIG"
  exit 1
fi

if [[ ! -f $CONFIGDATA ]]; then
  echo "No config data found at $CONFIGDATA"
  exit 1
fi

cd "$PROJECT_ROOT" && sbt -Dconfig.file="$CONFIG" run
