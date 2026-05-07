#!/usr/bin/env bash
# submit-ios-for-review.sh
#
# After xcrun altool --upload-app has put a build into App Store
# Connect, this script:
#   1. Polls the build until Apple's processingState transitions to
#      VALID (typically 5-10 minutes).
#   2. Finds (or creates) an appStoreVersion for MARKETING_VERSION on
#      the IOS platform.
#   3. Attaches our build to that appStoreVersion.
#   4. POSTs to /v1/appStoreVersionSubmissions to put the build into
#      Apple's review queue.
#
# Required env:
#   APP_STORE_CONNECT_API_KEY_ID      e.g. ABC1234567
#   APP_STORE_CONNECT_API_ISSUER_ID   UUID
#   APP_STORE_CONNECT_API_KEY_P8      raw .p8 file content
#   IOS_BUNDLE_ID                     e.g. studio.hypertext.LogDate
#   MARKETING_VERSION                 e.g. 1.2.3
#   CURRENT_PROJECT_VERSION           e.g. 5023482  (the buildNumber)
#
# Optional env:
#   SUBMIT_TIMEOUT_SECONDS            default 1200 (20 min)
#   SUBMIT_POLL_INTERVAL_SECONDS      default 30

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Make sure the Python deps the inline script needs are installed.
# pyjwt[crypto] for ES256 JWT signing; requests for HTTP. Both
# universally available via pip.
python3 -m pip install --quiet --upgrade --user "pyjwt[crypto]>=2.8" requests

exec python3 "$SCRIPT_DIR/submit-ios-for-review.py" "$@"
