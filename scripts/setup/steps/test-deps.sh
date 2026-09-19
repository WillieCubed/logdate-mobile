#!/usr/bin/env bash
# The passkey verifier venv, needed by scripts/tests/run-all.sh.

TEST_DEPS_VENV="$LOGDATE_REPO_ROOT/scripts/passkey-verify/.venv"
TEST_DEPS_REQUIREMENTS="$LOGDATE_REPO_ROOT/scripts/passkey-verify/requirements.txt"

test_deps_describe() {
    printf 'Script tests can run: the passkey verifier venv is installed'
}

test_deps_check() {
    local drift=0
    if [[ ! -x "$TEST_DEPS_VENV/bin/python" ]]; then
        printf 'No passkey verifier venv at %s\n' "${TEST_DEPS_VENV#"$LOGDATE_REPO_ROOT"/}"
        drift=1
    elif ! "$TEST_DEPS_VENV/bin/python" -c 'import cryptography, fido2, requests' 2>/dev/null; then
        printf 'The passkey verifier venv is missing packages from requirements.txt\n'
        drift=1
    fi
    return "$drift"
}

test_deps_apply() {
    command -v python3 >/dev/null 2>&1 || { printf 'Install Python 3, then re-run.\n'; return 2; }
    [[ -x "$TEST_DEPS_VENV/bin/python" ]] || python3 -m venv "$TEST_DEPS_VENV"
    "$TEST_DEPS_VENV/bin/pip" install --quiet -r "$TEST_DEPS_REQUIREMENTS"
}
