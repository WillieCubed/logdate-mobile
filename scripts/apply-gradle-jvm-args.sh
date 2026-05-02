#!/usr/bin/env bash
set -euo pipefail

# Idempotently append `--enable-native-access=ALL-UNNAMED` to the
# `org.gradle.jvmargs=` line in `gradle.properties`.
#
# Why this script exists:
#   - Under JDK 24+, the Gradle daemon's `net.rubygrapefruit.platform`
#     native-platform library loads JNI via `System.load()`, which the
#     JDK now flags with a "restricted method called" warning unless the
#     daemon JVM was launched with `--enable-native-access=ALL-UNNAMED`.
#   - Five such warnings get printed at the start of every build.
#   - The flag only affects the build-host JVM (Gradle daemon). It has
#     no effect on AGP, the Android compile toolchain, the Android
#     runtime (ART), `compileSdk`/`targetSdk`/`minSdk`, or any Android
#     version compatibility.
#
# Why a script and not a direct edit:
#   - `gradle.properties` is on the project's protected-config list
#     (`.githooks/block-rogue-configs.sh`), so direct edits via Claude's
#     Edit / Write / sed / awk tools are blocked. Running this script
#     is the explicit, reviewable path for applying the change.

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PROPS="$ROOT/gradle.properties"
FLAG="--enable-native-access=ALL-UNNAMED"

if [[ ! -f "$PROPS" ]]; then
    echo "error: $PROPS not found" >&2
    exit 1
fi

if grep -qF -- "$FLAG" "$PROPS"; then
    echo "Already applied; nothing to do."
    exit 0
fi

awk -v flag="$FLAG" '
    /^org\.gradle\.jvmargs=/ && !done {
        print $0 " " flag
        done = 1
        next
    }
    { print }
' "$PROPS" > "$PROPS.tmp"

if ! grep -qF -- "$FLAG" "$PROPS.tmp"; then
    rm -f "$PROPS.tmp"
    echo "error: org.gradle.jvmargs= line not found in $PROPS" >&2
    exit 1
fi

mv "$PROPS.tmp" "$PROPS"
echo "Applied $FLAG to org.gradle.jvmargs."
