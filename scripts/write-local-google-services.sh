#!/usr/bin/env bash
# Writes the placeholder google-services.json that lets the Android app build
# locally without a real Firebase project.
#
# The Google Services Gradle plugin fails the build unless the file declares a
# client whose package_name equals the applicationId, so this stub is derived
# from build.gradle.kts rather than hand-maintained -- an applicationId change
# would otherwise break every local build until someone edited the file by hand.
#
# The real configuration is delivered from repository secrets by
# scripts/sync-firebase-configs.sh; this file is gitignored and never shipped.
set -euo pipefail

readonly REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
readonly BUILD_FILE="$REPO_ROOT/app/android-main/build.gradle.kts"
readonly OUTPUT_FILE="$REPO_ROOT/app/android-main/google-services.json"

die() {
    echo "ERROR: $*" >&2
    exit 1
}

[[ -f "$BUILD_FILE" ]] || die "cannot find $BUILD_FILE"

# Mirrors the -Plogdate.applicationId override the app module honours, so a legacy-identity
# build gets a stub the Google Services plugin will accept.
package_name="${LOGDATE_APPLICATION_ID:-}"
if [[ -z "$package_name" ]]; then
    package_name="$(
        sed -n 's/.*\.orElse("\([a-z0-9_.]*\)").*/\1/p' "$BUILD_FILE" | head -1
    )"
fi
[[ -n "$package_name" ]] || die "could not determine the applicationId from $BUILD_FILE"

# The plugin matches each variant's full applicationId, so the suffixed debug build needs a client.
extra_package_name="${LOGDATE_APPLICATION_ID_EXTRA:-}"
if [[ -z "$extra_package_name" ]]; then
    suffix="$(
        sed -n 's/.*debugApplicationIdSuffix = "\([a-z0-9_.]*\)".*/\1/p' "$BUILD_FILE" | head -1
    )"
    [[ -n "$suffix" ]] && extra_package_name="${package_name}${suffix}"
fi

extra_client=""
stub_packages="$package_name"
if [[ -n "$extra_package_name" && "$extra_package_name" != "$package_name" ]]; then
    stub_packages="$package_name and $extra_package_name"
    extra_client=",
    {
      \"client_info\": {
        \"mobilesdk_app_id\": \"1:000000000000:android:0000000000000000000001\",
        \"android_client_info\": {
          \"package_name\": \"$extra_package_name\"
        }
      },
      \"oauth_client\": [],
      \"api_key\": [
        { \"current_key\": \"AIzaSyLocalStubKeyNotUsedForAnything00\" }
      ],
      \"services\": {
        \"appinvite_service\": { \"other_platform_oauth_client\": [] }
      }
    }"
fi

if [[ -f "$OUTPUT_FILE" ]] && ! grep -q '"project_id": "logdate-local-stub"' "$OUTPUT_FILE"; then
    die "$OUTPUT_FILE is a real Firebase configuration; refusing to overwrite it.
Delete it first if you meant to fall back to the local stub."
fi

cat > "$OUTPUT_FILE" <<JSON
{
  "project_info": {
    "project_number": "000000000000",
    "project_id": "logdate-local-stub",
    "storage_bucket": "logdate-local-stub.appspot.com"
  },
  "client": [
    {
      "client_info": {
        "mobilesdk_app_id": "1:000000000000:android:0000000000000000000000",
        "android_client_info": {
          "package_name": "$package_name"
        }
      },
      "oauth_client": [],
      "api_key": [
        { "current_key": "AIzaSyLocalStubKeyNotUsedForAnything00" }
      ],
      "services": {
        "appinvite_service": { "other_platform_oauth_client": [] }
      }
    }${extra_client}
  ],
  "configuration_version": "1"
}
JSON

echo "Wrote local Firebase stub for ${stub_packages} to ${OUTPUT_FILE#"$REPO_ROOT"/}"
