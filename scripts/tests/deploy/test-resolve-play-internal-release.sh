#!/usr/bin/env bash
# resolve-play-internal-release.sh against a fake Play API: it finds the
# internal release by commit SHA, and fails rather than guessing.
set -euo pipefail

source "$(git rev-parse --show-toplevel)/scripts/tests/lib/assertions.sh"
enter_repo_root

TMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TMP_DIR"' EXIT
mkdir -p "$TMP_DIR/bin"

# Answers edits.insert, tracks.get and edits.delete with canned JSON plus the
# HTTP status line curl -w appends.
cat >"$TMP_DIR/bin/curl" <<'EOF'
#!/usr/bin/env bash
cat >/dev/null
method=GET url=""
while [[ $# -gt 0 ]]; do
    case "$1" in
        -X) method="$2"; shift 2 ;;
        -H|-w|--data|--config) shift 2 ;;
        -*) shift ;;
        *) url="$1"; shift ;;
    esac
done
case "$method $url" in
    "POST "*/edits) printf '{"id":"e1"}\n200' ;;
    "GET "*/tracks/internal) printf '%s\n200' "$(cat "$TRACK_FILE")" ;;
    "DELETE "*) printf '\n204' ;;
    *) printf '{}\n404' ;;
esac
EOF
chmod +x "$TMP_DIR/bin/curl"

cat >"$TMP_DIR/track.json" <<'EOF'
{"track":"internal","releases":[
  {"name":"internal 0.1.0 3072ae3","versionCodes":["1921442"],"status":"completed"},
  {"name":"internal 0.1.0 abc1234","versionCodes":["1921443"],"status":"completed"}
]}
EOF

resolve() {
    TRACK_FILE="$TMP_DIR/track.json" PLAY_ACCESS_TOKEN=fake PATH="$TMP_DIR/bin:$PATH" \
        ./scripts/resolve-play-internal-release.sh studio.hypertext.logdate "$1" 2>&1
}

assert_contains "version_code=1921443" "$(resolve abc1234)"
assert_contains "version_code=1921442" "$(resolve 3072ae3)"

set +e
missing="$(resolve deadbee)"
missing_exit=$?
set -e
assert_exit_code 1 "$missing_exit"
assert_contains "No internal release" "$missing"

print_pass_summary "Play internal release lookup"
