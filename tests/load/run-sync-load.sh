#!/bin/bash

#
# Sync Load Test Runner
#
# Requires k6 to be installed: https://k6.io/docs/get-started/installation/
#
# Usage:
#   SYNC_TOKEN="..." ./tests/load/run-sync-load.sh
#   SYNC_TOKEN="..." SYNC_BASE_URL="http://localhost:8080/api/v1" ./tests/load/run-sync-load.sh
#

set -e

if ! command -v k6 >/dev/null 2>&1; then
    echo "k6 is not installed. See https://k6.io/docs/get-started/installation/"
    exit 1
fi

SYNC_BASE_URL=${SYNC_BASE_URL:-"http://localhost:8080/api/v1"}
SYNC_TOKEN=${SYNC_TOKEN:-""}

if [ -z "$SYNC_TOKEN" ]; then
    echo "SYNC_TOKEN is required"
    exit 1
fi

SYNC_BASE_URL="$SYNC_BASE_URL" SYNC_TOKEN="$SYNC_TOKEN" k6 run tests/load/sync-load.k6.js
