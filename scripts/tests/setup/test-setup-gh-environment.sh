#!/usr/bin/env bash
# The gh-environment step against a fake gh holding one environment in state
# files: check makes no writes, apply removes the reviewer gate and converges
# the allowed refs by adding missing ones and deleting extras.
set -euo pipefail

source "$(git rev-parse --show-toplevel)/scripts/tests/lib/assertions.sh"
enter_repo_root

TMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TMP_DIR"' EXIT
FAKE_BIN="$TMP_DIR/bin"
STATE="$TMP_DIR/state"
mkdir -p "$FAKE_BIN" "$STATE"

# Starting state: a reviewer gate, and one stale policy.
printf '%s' '{"protection_rules":[{"type":"required_reviewers"}],"deployment_branch_policy":{"protected_branches":false,"custom_branch_policies":true}}' \
    >"$STATE/env.json"
printf '%s' '{"branch_policies":[{"id":7,"type":"branch","name":"release"},{"id":8,"type":"branch","name":"main"}]}' \
    >"$STATE/policies.json"

cat >"$FAKE_BIN/gh" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
printf '%s\n' "$*" >>"$STATE/calls.log"
[[ "$1" == "api" ]] || exit 0
shift
method=GET jq_filter="" path=""
while [[ $# -gt 0 ]]; do
    case "$1" in
        -X) method="$2"; shift 2 ;;
        --jq) jq_filter="$2"; shift 2 ;;
        --input) shift 2 ;;
        *) path="$1"; shift ;;
    esac
done
emit() { if [[ -n "$jq_filter" ]]; then jq -r "$jq_filter" "$1"; else cat "$1"; fi; }
case "$method $path" in
    "GET "*/deployment-branch-policies) emit "$STATE/policies.json" ;;
    "GET "*) emit "$STATE/env.json" ;;
    "PUT "*)
        body="$(cat)"
        jq -n --argjson body "$body" '{protection_rules: [], deployment_branch_policy: $body.deployment_branch_policy}' \
            >"$STATE/env.json" ;;
    "POST "*/deployment-branch-policies)
        body="$(cat)"
        jq --argjson body "$body" '.branch_policies += [$body + {id: (100 + (.branch_policies | length))}]' \
            "$STATE/policies.json" >"$STATE/p.tmp" && mv "$STATE/p.tmp" "$STATE/policies.json" ;;
    "DELETE "*/deployment-branch-policies/*)
        id="${path##*/}"
        jq --argjson id "$id" '.branch_policies |= map(select(.id != $id))' \
            "$STATE/policies.json" >"$STATE/p.tmp" && mv "$STATE/p.tmp" "$STATE/policies.json" ;;
esac
EOF
chmod +x "$FAKE_BIN/gh"

run_step_under_test() {
    local check_only="$1"
    STATE="$STATE" PATH="$FAKE_BIN:$PATH" CHECK_ONLY="$check_only" NON_INTERACTIVE=true \
        GITHUB_ENVIRONMENT=production DEPLOY_BRANCHES="main" DEPLOY_TAGS="android-v* server-v*" \
        bash -c '
            set -euo pipefail
            source scripts/lib/common.sh
            source scripts/setup/runner.sh
            run_step gh-environment >/dev/null 2>&1
            printf "%s\n" "${RESULT_STATUS[0]}"
        '
}

status="$(run_step_under_test true)"
assert_contains "drift" "$status"
assert_not_contains "-X" "$(cat "$STATE/calls.log")"

status="$(run_step_under_test false)"
assert_contains "applied" "$status"
assert_contains "-X PUT" "$(cat "$STATE/calls.log")"
assert_contains "-X DELETE repos/" "$(cat "$STATE/calls.log")"
assert_contains "deployment-branch-policies/7" "$(grep DELETE "$STATE/calls.log")"
assert_not_contains "deployment-branch-policies/8" "$(grep DELETE "$STATE/calls.log")"

policies="$(jq -r '.branch_policies[] | "\(.type):\(.name)"' "$STATE/policies.json" | sort | tr '\n' ' ')"
[[ "$policies" == "branch:main tag:android-v* tag:server-v* " ]] || fail "unexpected policies: $policies"
pass
[[ "$(jq '.protection_rules | length' "$STATE/env.json")" == "0" ]] || fail "reviewer gate not removed"
pass

: >"$STATE/calls.log"
status="$(run_step_under_test false)"
assert_contains "ok" "$status"
assert_not_contains "-X" "$(cat "$STATE/calls.log")"

print_pass_summary "setup gh-environment step"
