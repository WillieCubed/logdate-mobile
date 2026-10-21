#!/usr/bin/env bash
# Real-behavior tests for scripts/check-handle-resolution.sh against a local HTTP stub that plays
# both the LogDate server and the website that answers https://<handle>/.well-known/atproto-did.

set -euo pipefail

OPERATOR_ROOT="$(git rev-parse --show-toplevel)"
# shellcheck source=scripts/tests/lib/assertions.sh
source "$OPERATOR_ROOT/scripts/tests/lib/assertions.sh"

SCRIPT="$OPERATOR_ROOT/scripts/check-handle-resolution.sh"
TMP_DIR="$(mktemp -d)"
chmod 700 "$TMP_DIR"
FAKE_BIN="$TMP_DIR/bin"
STUB_CONFIG="$TMP_DIR/stub.json"
STUB_PORT_FILE="$TMP_DIR/port"
DIG_OUTPUT="$TMP_DIR/dig-output"
mkdir -p "$FAKE_BIN"
: >"$DIG_OUTPUT"

HANDLE="alice.logdate.app"
DID="did:plc:abcdefghijklmnopqrstuvwx"
OTHER_DID="did:plc:zzzzzzzzzzzzzzzzzzzzzzzz"

STUB_PID=""
cleanup() {
    if [[ -n "$STUB_PID" ]]; then
        kill "$STUB_PID" 2>/dev/null || true
        wait "$STUB_PID" 2>/dev/null || true
    fi
    rm -r "$TMP_DIR"
}
trap cleanup EXIT

cat >"$FAKE_BIN/dig" <<EOF
#!/usr/bin/env bash
cat "$DIG_OUTPUT"
EOF
chmod +x "$FAKE_BIN/dig"

cat >"$TMP_DIR/stub.py" <<'PY'
import http.server
import json
import sys
import urllib.parse

config_path, port_path = sys.argv[1], sys.argv[2]


class Handler(http.server.BaseHTTPRequestHandler):
    def log_message(self, *args):
        pass

    def send(self, status, body, content_type):
        payload = body.encode()
        self.send_response(status)
        self.send_header("Content-Type", content_type)
        self.send_header("Content-Length", str(len(payload)))
        self.end_headers()
        self.wfile.write(payload)

    def do_GET(self):
        with open(config_path) as handle:
            config = json.load(handle)
        url = urllib.parse.urlparse(self.path)
        if url.path == "/xrpc/com.atproto.identity.resolveHandle":
            handle_value = urllib.parse.parse_qs(url.query).get("handle", [""])[0]
            did = config["server"].get(handle_value)
            if did:
                return self.send(200, json.dumps({"did": did}), "application/json")
            return self.send(400, json.dumps({"error": "HandleNotFound", "message": "Handle not found"}), "application/json")
        if url.path == "/.well-known/atproto-did":
            host = self.headers.get("Host", "").split(":")[0]
            if config.get("redirect"):
                self.send_response(302)
                self.send_header("Location", "https://sign-in.example/")
                self.send_header("Content-Length", "0")
                self.end_headers()
                return
            did = config["wellKnown"].get(host) or config.get("wildcard")
            if did:
                return self.send(200, did + "\n", "text/plain; charset=utf-8")
            return self.send(404, "Handle not found", "text/plain; charset=utf-8")
        if url.path.startswith("/did:"):
            document = config["didDocs"].get(url.path[1:])
            if document:
                return self.send(200, json.dumps(document), "application/json")
            return self.send(404, json.dumps({"message": "DID not registered"}), "application/json")
        return self.send(404, "", "text/plain")


server = http.server.ThreadingHTTPServer(("127.0.0.1", 0), Handler)
with open(port_path, "w") as handle:
    handle.write(str(server.server_address[1]))
server.serve_forever()
PY
python3 "$TMP_DIR/stub.py" "$STUB_CONFIG" "$STUB_PORT_FILE" &
STUB_PID=$!

for _ in $(seq 1 300); do
    [[ -s "$STUB_PORT_FILE" ]] && break
    kill -0 "$STUB_PID" 2>/dev/null || fail "stub server exited before binding"
    sleep 0.1
done
[[ -s "$STUB_PORT_FILE" ]] || fail "stub server did not start"
ORIGIN="http://127.0.0.1:$(cat "$STUB_PORT_FILE")"

write_stub() {
    local server_did="$1" well_known_did="$2" wildcard="${3:-null}" listed_handle="${4:-}"
    jq -n --arg handle "$HANDLE" --arg server "$server_did" --arg known "$well_known_did" \
        --argjson wildcard "$wildcard" --arg listed "$listed_handle" '{
        server: (if $server == "" then {} else {($handle): $server} end),
        wellKnown: (if $known == "" then {} else {($handle): $known} end),
        wildcard: $wildcard,
        didDocs: (if $listed == "" then {} else {($server): {id: $server, alsoKnownAs: ["at://\($listed)"]}} end)
    }' >"$STUB_CONFIG"
}

run_check() {
    local status=0
    OUTPUT="$(PATH="$FAKE_BIN:$PATH" "$SCRIPT" --handle "$HANDLE" --service-url "$ORIGIN" --well-known-origin "$ORIGIN" "$@" 2>&1)" || status=$?
    STATUS="$status"
}

# A handle that resolves the same way through the server and through its own host passes.
write_stub "$DID" "$DID"
run_check
assert_exit_code 0 "$STATUS"
assert_contains "[PASS] $HANDLE resolves to $DID through https://$HANDLE/.well-known/atproto-did" "$OUTPUT"
assert_contains "[PASS] an unknown handle under logdate.app returns 404" "$OUTPUT"

# The expected DID pins the result to one account.
run_check --expect-did "$OTHER_DID"
assert_exit_code 1 "$STATUS"
assert_contains "[FAIL] the server resolves $HANDLE to $DID, expected $OTHER_DID" "$OUTPUT"

# The handle host answering with a different account is a failure.
write_stub "$DID" "$OTHER_DID"
run_check
assert_exit_code 1 "$STATUS"
assert_contains "[FAIL] https://$HANDLE/.well-known/atproto-did returned $OTHER_DID, but the server resolves $HANDLE to $DID" "$OUTPUT"

# A handle the server does not know is reported with the server's own error.
write_stub "" ""
run_check
assert_exit_code 1 "$STATUS"
assert_contains "[FAIL] the server could not resolve $HANDLE (HTTP 400: HandleNotFound)" "$OUTPUT"
assert_contains "[FAIL] https://$HANDLE/.well-known/atproto-did returned HTTP 404" "$OUTPUT"

# A wildcard answer for every host would let anyone's label claim a DID.
write_stub "$DID" "$DID" "\"$DID\""
run_check
assert_exit_code 1 "$STATUS"
assert_contains "returned 200 instead of 404" "$OUTPUT"

# A redirect (such as a sign-in wall in front of the handle host) is reported as LogDate's own rule.
write_stub "$DID" "$DID"
jq '.redirect = true' "$STUB_CONFIG" >"$STUB_CONFIG.next"
mv "$STUB_CONFIG.next" "$STUB_CONFIG"
run_check
assert_exit_code 1 "$STATUS"
assert_contains "[FAIL] https://$HANDLE/.well-known/atproto-did redirected (HTTP 302)" "$OUTPUT"

# A DNS TXT record that disagrees with the HTTPS answer makes resolvers reject the handle.
write_stub "$DID" "$DID"
printf '"did=%s"\n' "$OTHER_DID" >"$DIG_OUTPUT"
run_check
assert_exit_code 1 "$STATUS"
assert_contains "[FAIL] _atproto.$HANDLE TXT says did=$OTHER_DID, which conflicts with $DID" "$OUTPUT"

# A matching DNS TXT record is accepted.
printf '"did=%s"\n' "$DID" >"$DIG_OUTPUT"
run_check
assert_exit_code 0 "$STATUS"
assert_contains "[PASS] _atproto.$HANDLE TXT matches $DID" "$OUTPUT"
: >"$DIG_OUTPUT"

# Apps confirm the handle in the DID document; an unpublished did:plc fails that check.
run_check --verify-did-document --plc-directory-url "$ORIGIN"
assert_exit_code 1 "$STATUS"
assert_contains "[FAIL] the DID document for $DID is unavailable (HTTP 404)" "$OUTPUT"

write_stub "$DID" "$DID" null "$HANDLE"
run_check --verify-did-document --plc-directory-url "$ORIGIN"
assert_exit_code 0 "$STATUS"
assert_contains "[PASS] the DID document for $DID lists at://$HANDLE" "$OUTPUT"

write_stub "$DID" "$DID" null "someone-else.logdate.app"
run_check --verify-did-document --plc-directory-url "$ORIGIN"
assert_exit_code 1 "$STATUS"
assert_contains "[FAIL] the DID document for $DID does not list at://$HANDLE" "$OUTPUT"

# Missing required arguments are a usage error, not a silent pass.
status=0
OUTPUT="$("$SCRIPT" --service-url "$ORIGIN" 2>&1)" || status=$?
assert_exit_code 2 "$status"
assert_contains "--handle is required" "$OUTPUT"

echo "PASS: $PASS_COUNT assertions"
