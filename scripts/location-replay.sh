#!/usr/bin/env bash
# Replays exported location history through the day reconstruction, old and current, and prints a
# per-day comparison: how many visits, journeys and gaps each version shows, next to the quality of
# the samples behind them (capture source, accuracy, cadence).
#
# Usage:
#   ./run location-replay <export.zip | location_history.json | location-history.jsonl> [time-zone]
#
# The export holds private location history. Everything is copied under tmp/location-replay/,
# which git ignores; never commit the input or the report.
set -euo pipefail

cd "$(dirname "${BASH_SOURCE[0]}")/.."

readonly INPUT="${1:?usage: location-replay <export.zip | location_history.json | location-history.jsonl> [time-zone]}"
readonly ZONE="${2:-}"
readonly NAME="$(basename "${INPUT%.*}")"
readonly WORK_DIR="tmp/location-replay/${NAME}"

mkdir -p "$WORK_DIR"

# v1 archives hold location_history.json; v2 archives hold data/location-history.jsonl.
case "$INPUT" in
    *.json | *.jsonl)
        samples="$WORK_DIR/$(basename "$INPUT")"
        cp "$INPUT" "$samples"
        ;;
    *)
        entry="$(unzip -Z1 "$INPUT" | grep -m1 -E '(location_history\.json|location-history\.jsonl)$' || true)"
        if [[ -z "$entry" ]]; then
            echo "No location history inside $INPUT" >&2
            exit 1
        fi
        samples="$WORK_DIR/$(basename "$entry")"
        unzip -p "$INPUT" "$entry" >"$samples"
        ;;
esac

LOCATION_REPLAY_JSON="$PWD/$samples" \
    LOCATION_REPLAY_OUT="$PWD/$WORK_DIR/replay" \
    LOCATION_REPLAY_ZONE="$ZONE" \
    ./gradlew :client:domain:jvmTest \
    --tests 'app.logdate.client.domain.location.history.replay.LocationReplayTool' \
    --rerun --quiet

cat "$WORK_DIR/replay/report.txt"
echo
echo "Per-day GeoJSON: $WORK_DIR/replay/"
