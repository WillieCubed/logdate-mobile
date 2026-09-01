#!/usr/bin/env bash
# Adds the client for the applicationId being built to a real google-services.json
# that predates an applicationId change.
#
# The Google Services Gradle plugin fails the build outright when the configuration
# declares no client whose package_name equals the applicationId. That coupling makes
# renaming an application a two-sided change: the rename lands in the repository, but
# the configuration arrives from elsewhere -- repository secrets in CI, a developer's
# download locally -- and until every one of those sources is regenerated, no Android
# build runs at all.
#
# This reconciles the gap from the repository side. When a configuration is missing the
# client for the applicationId being built, the client is added from the Firebase app ID
# recorded in infra/firebase/android-app-ids.json, keeping the configuration's own
# project_info and API key. Those app IDs are public -- they ship inside every APK.
#
# This is deliberately narrow:
#   - a configuration that already declares the client is left untouched;
#   - a missing configuration is left to scripts/write-local-google-services.sh;
#   - an unknown applicationId fails loudly rather than inventing an app ID, because a
#     fabricated one would build and then silently misreport to Firebase at runtime.
#
# It runs immediately before scripts/validate-firebase-config.sh, which is the thing that
# actually decides whether a configuration is acceptable. This only closes the one gap the
# rename opened; everything else the validator rejects still fails the build.
#
# It is migration scaffolding, and it is the reason Android CI passes while the repository
# secrets still describe the old application. Once
# LOGDATE_ANDROID_GOOGLE_SERVICES_JSON_DEBUG_BASE64 and _RELEASE_BASE64 carry configurations
# that already declare the client, this becomes a no-op: delete it, the mapping file, and the
# reconcile step in .github/actions/setup-firebase-configs.
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
readonly REPO_ROOT
readonly APP_ID_MAP="$REPO_ROOT/infra/firebase/android-app-ids.json"

die() {
    echo "ERROR: $*" >&2
    exit 1
}

CONFIG_FILE=""
APPLICATION_ID="${LOGDATE_APPLICATION_ID:-}"

while [[ $# -gt 0 ]]; do
    case "$1" in
        --file) CONFIG_FILE="${2:-}"; shift 2 ;;
        --application-id) APPLICATION_ID="${2:-}"; shift 2 ;;
        *) die "unknown argument: $1" ;;
    esac
done

[[ -n "$CONFIG_FILE" ]] || die "--file is required."
[[ -n "$APPLICATION_ID" ]] || die "--application-id (or LOGDATE_APPLICATION_ID) is required."

# A configuration that is not there yet is not this script's problem.
[[ -f "$CONFIG_FILE" ]] || exit 0

[[ -f "$APP_ID_MAP" ]] || die "missing $APP_ID_MAP"

CONFIG_FILE="$CONFIG_FILE" APPLICATION_ID="$APPLICATION_ID" APP_ID_MAP_PATH="$APP_ID_MAP" python3 - <<'PY'
import copy
import json
import os
import sys

config_path = os.environ["CONFIG_FILE"]
application_id = os.environ["APPLICATION_ID"]
map_path = os.environ["APP_ID_MAP_PATH"]

with open(config_path) as handle:
    config = json.load(handle)

clients = config.get("client") or []


def package_of(client):
    return (
        client.get("client_info", {})
        .get("android_client_info", {})
        .get("package_name")
    )


if any(package_of(client) == application_id for client in clients):
    sys.exit(0)

if not clients:
    print(
        f"ERROR: {config_path} declares no clients at all; cannot derive one for "
        f"{application_id}.",
        file=sys.stderr,
    )
    sys.exit(1)

project_id = config.get("project_info", {}).get("project_id")

with open(map_path) as handle:
    app_ids = json.load(handle)

mobilesdk_app_id = (app_ids.get(project_id) or {}).get(application_id)
if not mobilesdk_app_id:
    print(
        f"ERROR: {config_path} (project {project_id}) has no client for "
        f"{application_id}, and no Firebase app ID is recorded for that pair in "
        f"{os.path.relpath(map_path)}.\n"
        f"Register the app in the {project_id} Firebase project, then either refresh "
        f"this configuration or record its app ID in that file.",
        file=sys.stderr,
    )
    sys.exit(1)

# Model the new client on an existing one so the API key, OAuth clients, and service
# blocks stay exactly as Firebase issued them for this project.
template = copy.deepcopy(clients[0])
template["client_info"]["mobilesdk_app_id"] = mobilesdk_app_id
template["client_info"]["android_client_info"]["package_name"] = application_id

for oauth_client in template.get("oauth_client") or []:
    android_info = oauth_client.get("android_info")
    if isinstance(android_info, dict) and "package_name" in android_info:
        android_info["package_name"] = application_id

config["client"] = clients + [template]

with open(config_path, "w") as handle:
    json.dump(config, handle, indent=2)
    handle.write("\n")

print(
    f"Added the {application_id} client to {os.path.basename(config_path)} "
    f"(project {project_id}, app {mobilesdk_app_id})."
)
PY
