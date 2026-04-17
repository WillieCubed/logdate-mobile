#!/usr/bin/env bash

set -euo pipefail

PACKAGE_NAME="${PACKAGE_NAME:-co.reasonabletech.logdate}"
OUTPUT_DIR="${OUTPUT_DIR:-$(pwd)/tmp}"
TIMESTAMP="$(date +%Y%m%d-%H%M%S)"
OUTPUT_FILE="${OUTPUT_DIR}/logdate-export-debug-${TIMESTAMP}.txt"
FILTERED_FILE="${OUTPUT_DIR}/logdate-export-debug-${TIMESTAMP}-filtered.txt"

mkdir -p "${OUTPUT_DIR}"

require_cmd() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "Missing required command: $1" >&2
    exit 1
  fi
}

run_section() {
  local title="$1"
  local command="$2"

  {
    echo
    echo "===== ${title} ====="
    bash -lc "${command}"
  } >>"${OUTPUT_FILE}" 2>&1
}

require_cmd adb

{
  echo "LogDate export diagnostics"
  echo "Generated at: $(date)"
  echo "Package: ${PACKAGE_NAME}"
  echo "Output file: ${OUTPUT_FILE}"
} >"${OUTPUT_FILE}"

echo "Writing diagnostics to ${OUTPUT_FILE}"

adb get-state >/dev/null

echo "Clearing logcat so the capture only includes the next export attempt..."
adb logcat -c

cat <<'EOF'

Reproduce the export now on the device.
When the export finishes or fails, press Enter here to collect diagnostics.

EOF
read -r _

run_section "adb devices" "adb devices -l"
run_section "filtered export logcat" "adb logcat -d"
run_section \
  "app-private media and database listings" \
  "adb shell \"run-as ${PACKAGE_NAME} sh -c 'echo \\\"== user_media ==\\\"; ls -lah files/user_media 2>/dev/null || echo \\\"missing\\\"; echo; echo \\\"== audio_notes ==\\\"; ls -lah files/audio_notes 2>/dev/null || echo \\\"missing\\\"; echo; echo \\\"== databases ==\\\"; ls -lah databases 2>/dev/null || echo \\\"missing\\\";'\""
run_section \
  "known broken filename probes" \
  "adb shell \"run-as ${PACKAGE_NAME} sh -c 'for pattern in \\\"*1000025239*\\\" \\\"*1000025222*\\\" \\\"*1000025182*\\\" \\\"*1000025157*\\\" \\\"*1000025155*\\\" \\\"*1000025148*\\\" \\\"*1000025141*\\\" \\\"*1000025129*\\\" \\\"*recording_d71308a0-b178-4311-953f-66751c3d3a84*\\\" \\\"*recording_5b10cbdf-a31c-4047-b918-e9c8e13e53c8*\\\" \\\"*recording_4bc9cb1f-61d8-467c-86ae-e343da59fd88*\\\" \\\"*recording_8aa947a1-d9b7-425e-8070-e5683cd94972*\\\" \\\"*recording_c85d1507-c0b0-4013-884a-056e9845bd25*\\\" \\\"*recording_80c4793e-d20e-474b-9016-d0d80e613157*\\\" \\\"*recording_3d6f8366-9028-493e-9308-6758269eea0c*\\\" \\\"*recording_56f7396b*\\\" \\\"*recording_39ee4c12*\\\" \\\"*recording_429774dd*\\\" \\\"*recording_09044d1d*\\\" \\\"*recording_e41b7294*\\\" \\\"*recording_c7ab7a2b*\\\" \\\"*recording_4cc69149*\\\" \\\"*recording_b62d2c3b*\\\" \\\"*recording_fb6185fb*\\\" \\\"*recording_f3b58f7c*\\\"; do echo \\\"== \\\$pattern ==\\\"; ls -lah files/user_media/\\\$pattern files/audio_notes/\\\$pattern 2>/dev/null || echo \\\"missing\\\"; echo; done'\""

grep -E "ExportWorker|No extension found in URI|Media file not found, skipping|Failed to add media file to ZIP" "${OUTPUT_FILE}" >"${FILTERED_FILE}" || true

echo
echo "Done."
echo "Full output: ${OUTPUT_FILE}"
echo "Filtered export lines: ${FILTERED_FILE}"
