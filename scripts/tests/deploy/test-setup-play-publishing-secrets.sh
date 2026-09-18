#!/usr/bin/env bash
# Runs setup-play-publishing-secrets.sh against a fake gh under shell tracing and
# proves each secret reaches `gh secret set --env production` on stdin while no
# secret value appears in any gh argv or in the traced output.
set -euo pipefail

source "$(git rev-parse --show-toplevel)/scripts/tests/lib/assertions.sh"
enter_repo_root

TMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TMP_DIR"' EXIT

FAKE_BIN="$TMP_DIR/bin"
SIGNING_DIR="$TMP_DIR/signing"
GH_LOG="$TMP_DIR/gh-calls.log"
mkdir -p "$FAKE_BIN" "$SIGNING_DIR"

STORE_PASSWORD="sentinel-store-password-6e1f"
KEY_ALIAS="sentinel-alias-93ac"
KEY_PASSWORD="sentinel-key-password-b72d"
SERVICE_ACCOUNT_SECRET="sentinel-private-key-40c8"

printf 'sentinel-keystore-bytes-d15a' >"$SIGNING_DIR/production-upload.jks"
KEYSTORE_BASE64="$(base64 <"$SIGNING_DIR/production-upload.jks" | tr -d '\n')"
cat >"$SIGNING_DIR/production-upload.env" <<EOF
# comment lines are ignored
LOGDATE_RELEASE_STORE_FILE=$SIGNING_DIR/production-upload.jks
LOGDATE_RELEASE_STORE_PASSWORD="$STORE_PASSWORD"
LOGDATE_RELEASE_KEY_ALIAS = $KEY_ALIAS
LOGDATE_RELEASE_KEY_PASSWORD='$KEY_PASSWORD'
EOF
cat >"$SIGNING_DIR/play-publisher.json" <<EOF
{"type": "service_account", "private_key": "$SERVICE_ACCOUNT_SECRET"}
EOF

# Records argv on one line and, for `secret set`, the stdin it received.
cat >"$FAKE_BIN/gh" <<'EOF'
#!/usr/bin/env bash
printf 'ARGV %s\n' "$*" >>"$GH_LOG"
if [[ "$1 $2" == "secret set" ]]; then
    printf 'STDIN %s %s\n' "$3" "$(cat)" >>"$GH_LOG"
fi
exit 0
EOF
chmod +x "$FAKE_BIN/gh"

run_setup() {
    GH_LOG="$GH_LOG" LOGDATE_SIGNING_DIR="$SIGNING_DIR" PATH="$FAKE_BIN:$PATH" \
        bash -x ./scripts/setup-play-publishing-secrets.sh --non-interactive "$@" 2>&1
}

output="$(run_setup)"
exit_code=$?
assert_exit_code 0 "$exit_code"

gh_log="$(cat "$GH_LOG")"
argv_lines="$(grep '^ARGV ' "$GH_LOG")"

assert_contains "STDIN ANDROID_PUBLISHER_CREDENTIALS {\"type\": \"service_account\", \"private_key\": \"$SERVICE_ACCOUNT_SECRET\"}" "$gh_log"
assert_contains "STDIN LOGDATE_RELEASE_STORE_BASE64 $KEYSTORE_BASE64" "$gh_log"
assert_contains "STDIN LOGDATE_RELEASE_STORE_PASSWORD $STORE_PASSWORD" "$gh_log"
assert_contains "STDIN LOGDATE_RELEASE_KEY_ALIAS $KEY_ALIAS" "$gh_log"
assert_contains "STDIN LOGDATE_RELEASE_KEY_PASSWORD $KEY_PASSWORD" "$gh_log"
assert_contains "ARGV secret set LOGDATE_RELEASE_KEY_PASSWORD --env production" "$argv_lines"
assert_not_contains "--body" "$(grep '^ARGV secret' "$GH_LOG")"

for sentinel in "$STORE_PASSWORD" "$KEY_ALIAS" "$KEY_PASSWORD" "$SERVICE_ACCOUNT_SECRET" "$KEYSTORE_BASE64"; do
    assert_not_contains "$sentinel" "$argv_lines"
done
# The alias is not a secret the script needs to hide from its own trace, but the
# passwords and key material are.
for sentinel in "$STORE_PASSWORD" "$KEY_PASSWORD" "$SERVICE_ACCOUNT_SECRET" "$KEYSTORE_BASE64"; do
    assert_not_contains "$sentinel" "$output"
done

# Publish variables stay untouched unless requested.
assert_not_contains "ARGV variable set" "$argv_lines"

: >"$GH_LOG"
run_setup --enable-internal true >/dev/null
assert_contains "ARGV variable set LOGDATE_PLAY_INTERNAL_PUBLISH_ENABLED --body true" "$(cat "$GH_LOG")"
assert_not_contains "LOGDATE_PLAY_PRODUCTION_PUBLISH_ENABLED" "$(cat "$GH_LOG")"

# A missing input fails in non-interactive mode rather than prompting.
rm "$SIGNING_DIR/play-publisher.json"
set +e
missing_output="$(run_setup)"
missing_exit=$?
set -e
assert_exit_code 1 "$missing_exit"
assert_contains "required and --non-interactive was set" "$missing_output"

print_pass_summary "Play publishing secrets setup"
