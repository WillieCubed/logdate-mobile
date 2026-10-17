#!/usr/bin/env bash
# The passkey verifier venv and the lexicon checker's Node packages, needed by
# scripts/tests/run-all.sh.

TEST_DEPS_VENV="$LOGDATE_REPO_ROOT/scripts/passkey-verify/.venv"
TEST_DEPS_REQUIREMENTS="$LOGDATE_REPO_ROOT/scripts/passkey-verify/requirements.txt"
TEST_DEPS_LEXICON_CHECK="$LOGDATE_REPO_ROOT/tools/lexicon-check"

test_deps_describe() {
    printf 'Script tests can run: the passkey verifier venv and lexicon checker packages are installed'
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
    if [[ ! -d "$TEST_DEPS_LEXICON_CHECK/node_modules/@atproto/lexicon" ]]; then
        printf 'The lexicon checker packages in tools/lexicon-check are not installed\n'
        drift=1
    fi
    return "$drift"
}

test_deps_apply() {
    command -v python3 >/dev/null 2>&1 || { printf 'Install Python 3, then re-run.\n'; return 2; }
    [[ -x "$TEST_DEPS_VENV/bin/python" ]] || python3 -m venv "$TEST_DEPS_VENV"
    "$TEST_DEPS_VENV/bin/pip" install --quiet -r "$TEST_DEPS_REQUIREMENTS"
    test_deps_install_lexicon_check
}

# Uses pnpm when it's installed, and otherwise the version tools/lexicon-check/package.json pins,
# run through npx. (corepack can't be used: it may fetch a different pnpm than the pin, which
# pnpm then refuses to run under.)
test_deps_install_lexicon_check() {
    command -v node >/dev/null 2>&1 || { printf 'Install Node.js, then re-run.\n'; return 2; }
    local pnpm=(pnpm)
    if ! command -v pnpm >/dev/null 2>&1; then
        local pinned
        pinned="$(node -p 'require(process.argv[1]).packageManager' "$TEST_DEPS_LEXICON_CHECK/package.json")"
        pnpm=(npx --yes "$pinned")
    fi
    "${pnpm[@]}" --dir "$TEST_DEPS_LEXICON_CHECK" install --frozen-lockfile --silent
}
