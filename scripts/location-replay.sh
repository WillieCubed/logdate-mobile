#!/usr/bin/env bash
# Replays exported location history through the day reconstruction, old and current, and prints a
# per-day comparison: how many visits, journeys and gaps each version shows, next to the quality of
# the samples behind them (capture source, accuracy, cadence).
#
# Usage:
#   ./run location-replay <export.zip | location_history.json> [time-zone]
#
# The export holds private location history. Everything is copied under tmp/location-replay/,
# which git ignores; never commit the input or the report.
set -euo pipefail

cd "$(dirname "${BASH_SOURCE[0]}")/.."

readonly INPUT="${1:?usage: location-replay <export.zip | location_history.json> [time-zone]}"
readonly ZONE="${2:-}"
readonly NAME="$(basename "${INPUT%.*}")"
readonly WORK_DIR="tmp/location-replay/${NAME}"

mkdir -p "$WORK_DIR"

case "$INPUT" in
    *.json)
        cp "$INPUT" "$WORK_DIR/location_history.json"
        ;;
    *)
        entry="$(unzip -Z1 "$INPUT" | grep -m1 'location_history\.json$' || true)"
        if [[ -z "$entry" ]]; then
            echo "No location_history.json inside $INPUT" >&2
            exit 1
        fi
        unzip -p "$INPUT" "$entry" >"$WORK_DIR/location_history.json"
        ;;
esac

LOCATION_REPLAY_JSON="$PWD/$WORK_DIR/location_history.json" \
    LOCATION_REPLAY_OUT="$PWD/$WORK_DIR/replay" \
    LOCATION_REPLAY_ZONE="$ZONE" \
    ./gradlew :client:domain:jvmTest \
    --tests 'app.logdate.client.domain.location.history.replay.LocationReplayTool' \
    --rerun --quiet

cat "$WORK_DIR/replay/report.txt"
echo
echo "Per-day GeoJSON: $WORK_DIR/replay/"
