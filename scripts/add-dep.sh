#!/usr/bin/env bash
# add-dep.sh - Add a Gradle dependency to the version catalog
#
# Usage:
#   ./scripts/add-dep.sh <alias> <maven-coordinate> <version> [--module <gradle-module> --config <configuration>]
#
# Examples:
#   # Add to version catalog only:
#   ./scripts/add-dep.sh vosk-android com.alphacephei:vosk-android 0.3.47
#
#   # Add to catalog and wire into a module's build.gradle.kts:
#   ./scripts/add-dep.sh vosk-android com.alphacephei:vosk-android 0.3.47 \
#       --module client/media --config androidMain --implementation
#
# What it does:
#   1. Adds a version entry to gradle/libs.versions.toml [versions] section
#   2. Adds a library entry to gradle/libs.versions.toml [libraries] section
#   3. Optionally adds implementation(...) to a module's build.gradle.kts
#
# The alias is used as-is for the version key and library key. Dashes in the
# alias become dots in the Gradle accessor (e.g. vosk-android -> libs.vosk.android).

set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/.."

CATALOG="gradle/libs.versions.toml"

# ── Parse arguments ──────────────────────────────────────────────────────────

usage() {
    echo "Usage: $0 <alias> <group:artifact> <version> [options]"
    echo ""
    echo "Options:"
    echo "  --module <path>     Gradle module to add dependency to (e.g. client/media)"
    echo "  --config <name>     Source set dependencies block (e.g. androidMain, commonMain)"
    echo "  --implementation    Use implementation() (default)"
    echo "  --api               Use api() instead of implementation()"
    echo "  --dry-run           Print what would be changed without modifying files"
    echo ""
    echo "Examples:"
    echo "  $0 vosk-android com.alphacephei:vosk-android 0.3.47"
    echo "  $0 vosk-android com.alphacephei:vosk-android 0.3.47 --module client/media --config androidMain"
    exit 1
}

if [[ $# -lt 3 ]]; then
    usage
fi

ALIAS="$1"
COORDINATE="$2"
VERSION="$3"
shift 3

MODULE=""
CONFIG=""
DEP_TYPE="implementation"
DRY_RUN=false

while [[ $# -gt 0 ]]; do
    case "$1" in
        --module) MODULE="$2"; shift 2 ;;
        --config) CONFIG="$2"; shift 2 ;;
        --implementation) DEP_TYPE="implementation"; shift ;;
        --api) DEP_TYPE="api"; shift ;;
        --dry-run) DRY_RUN=true; shift ;;
        *) echo "Unknown option: $1"; usage ;;
    esac
done

# Validate coordinate format
if [[ ! "$COORDINATE" =~ ^[a-zA-Z0-9._-]+:[a-zA-Z0-9._-]+$ ]]; then
    echo "Error: Maven coordinate must be in group:artifact format (got: $COORDINATE)"
    exit 1
fi

GROUP="${COORDINATE%%:*}"
ARTIFACT="${COORDINATE##*:}"

# Convert alias to version key (strip dashes for version key convention)
# e.g. vosk-android -> voskAndroid (camelCase for version key)
VERSION_KEY=$(echo "$ALIAS" | awk -F'-' '{printf "%s", $1; for(i=2;i<=NF;i++) printf "%s%s", toupper(substr($i,1,1)), substr($i,2); print ""}')

# Gradle accessor: dashes become dots
GRADLE_ACCESSOR="libs.$(echo "$ALIAS" | tr '-' '.')"

# ── Validate catalog exists ──────────────────────────────────────────────────

if [[ ! -f "$CATALOG" ]]; then
    echo "Error: $CATALOG not found"
    exit 1
fi

# ── Check for duplicates ─────────────────────────────────────────────────────

if grep -qE "^${VERSION_KEY}[[:space:]]*=" "$CATALOG"; then
    echo "Warning: Version key '$VERSION_KEY' already exists in [versions]"
    SKIP_VERSION=true
else
    SKIP_VERSION=false
fi

if grep -qE "^${ALIAS}[[:space:]]*=" "$CATALOG"; then
    echo "Error: Library alias '$ALIAS' already exists in [libraries]"
    exit 1
fi

# ── Apply changes ────────────────────────────────────────────────────────────

echo "Adding dependency: $ALIAS"
echo "  Maven:    $GROUP:$ARTIFACT:$VERSION"
echo "  Version:  [$VERSION_KEY] = \"$VERSION\""
echo "  Library:  $ALIAS = { module = \"$COORDINATE\", version.ref = \"$VERSION_KEY\" }"
echo "  Accessor: $GRADLE_ACCESSOR"
if [[ -n "$MODULE" ]]; then
    echo "  Module:   $MODULE (${CONFIG:-commonMain}.dependencies)"
fi
echo ""

if $DRY_RUN; then
    echo "[dry-run] No files modified."
    exit 0
fi

# 1. Add version entry (before [libraries] section, at end of versions block)
if ! $SKIP_VERSION; then
    # Find the last line of the [versions] section (line before [libraries])
    LIBRARIES_LINE=$(grep -n '^\[libraries\]' "$CATALOG" | head -1 | cut -d: -f1)
    if [[ -z "$LIBRARIES_LINE" ]]; then
        echo "Error: Could not find [libraries] section in $CATALOG"
        exit 1
    fi

    # Insert version entry before the [libraries] line (with a blank line)
    sed -i '' "${LIBRARIES_LINE}i\\
${VERSION_KEY} = \"${VERSION}\"\\
" "$CATALOG"

    echo "  + Added version: $VERSION_KEY = \"$VERSION\""
else
    echo "  ~ Skipped version (already exists)"
fi

# Re-read the file since line numbers may have shifted
# 2. Add library entry at the end of the [libraries] section (before [plugins])
PLUGINS_LINE=$(grep -n '^\[plugins\]' "$CATALOG" | head -1 | cut -d: -f1)
if [[ -z "$PLUGINS_LINE" ]]; then
    echo "Error: Could not find [plugins] section in $CATALOG"
    exit 1
fi

LIBRARY_ENTRY="${ALIAS} = { module = \"${COORDINATE}\", version.ref = \"${VERSION_KEY}\" }"
sed -i '' "${PLUGINS_LINE}i\\
${LIBRARY_ENTRY}\\
" "$CATALOG"

echo "  + Added library: $ALIAS"

# 3. Optionally add to module's build.gradle.kts
if [[ -n "$MODULE" ]]; then
    BUILD_FILE="$MODULE/build.gradle.kts"
    if [[ ! -f "$BUILD_FILE" ]]; then
        echo "Error: $BUILD_FILE not found"
        exit 1
    fi

    TARGET_CONFIG="${CONFIG:-commonMain}"
    DEP_LINE="            ${DEP_TYPE}(${GRADLE_ACCESSOR})"

    # Find the target dependencies block: <config>.dependencies {
    # and add the dependency inside it
    if grep -q "${TARGET_CONFIG}.dependencies" "$BUILD_FILE"; then
        # Find the line with the dependencies block and add after the opening brace
        DEPS_LINE=$(grep -n "${TARGET_CONFIG}.dependencies" "$BUILD_FILE" | head -1 | cut -d: -f1)
        sed -i '' "${DEPS_LINE}a\\
${DEP_LINE}
" "$BUILD_FILE"
        echo "  + Added ${DEP_TYPE}(${GRADLE_ACCESSOR}) to ${BUILD_FILE} (${TARGET_CONFIG})"
    else
        echo "  Warning: Could not find '${TARGET_CONFIG}.dependencies' block in $BUILD_FILE"
        echo "  Please add manually: ${DEP_TYPE}(${GRADLE_ACCESSOR})"
    fi
fi

echo ""
echo "Done. Run './gradlew --refresh-dependencies' or sync Gradle to pick up the new dependency."
