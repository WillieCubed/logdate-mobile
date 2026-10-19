#!/usr/bin/env bash
# The identity publishing rehearsal must only ever talk to a PLC directory on this machine, and must
# not let the developer's own environment choose a different one.

set -euo pipefail

# shellcheck source=scripts/tests/lib/assertions.sh
source "$(git rev-parse --show-toplevel)/scripts/tests/lib/assertions.sh"
enter_repo_root

SCRIPT="scripts/identity-publishing-rehearsal.sh"

status=0
help_output="$(bash "$SCRIPT" --help)" || status=$?
assert_exit_code 0 "$status"
assert_contains "./run identity:rehearse" "$help_output"

for bad in "--accounts 0" "--accounts many" "--publish-for-real"; do
    status=0
    # shellcheck disable=SC2086
    bash "$SCRIPT" $bad >/dev/null 2>&1 || status=$?
    [[ "$status" != "0" ]] || fail "accepted $bad"
    pass
done

# shellcheck source=scripts/identity-publishing-rehearsal.sh
source "$SCRIPT"

for url in "http://127.0.0.1:2582" "http://localhost:40123"; do
    (require_loopback_url "$url") >/dev/null 2>&1 || fail "refused loopback $url"
    pass
done
for url in "https://plc.directory" "http://plc.directory:80" "http://127.0.0.1.example.com:80" "http://10.0.0.4:2582"; do
    if (require_loopback_url "$url") >/dev/null 2>&1; then
        fail "accepted non-loopback $url"
    fi
    pass
done

# The server never inherits the caller's PLC settings: SERVER_ENV names the local directory only.
export ATPROTO_PLC_DIRECTORY_URL="https://plc.directory" ATPROTO_PLC_PUBLISH_ENABLED=true
PLC_URL="http://127.0.0.1:2582" SERVER_PORT=1 PG_PORT=2 JWT=j KEK=k SESSION_SECRET=s
server_environment false "https://pds.$HANDLE_DOMAIN"
environment="$(printf '%s\n' "${SERVER_ENV[@]}")"
assert_contains "ATPROTO_PLC_DIRECTORY_URL=http://127.0.0.1:2582" "$environment"
assert_contains "ATPROTO_PLC_PUBLISH_ENABLED=false" "$environment"
assert_not_contains "plc.directory" "$environment"

if (PLC_URL="https://plc.directory" server_environment true "https://pds.$HANDLE_DOMAIN") >/dev/null 2>&1; then
    fail "server_environment accepted the public directory"
fi
pass
if (server_environment true "https://cloud.logdate.app") >/dev/null 2>&1; then
    fail "server_environment accepted a real PDS address"
fi
pass

print_pass_summary "identity publishing rehearsal"
