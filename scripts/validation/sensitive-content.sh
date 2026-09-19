#!/usr/bin/env bash
# Rules the pre-commit hook uses to keep secrets out of commits. Source it.
#
#   is_sensitive_path PATH       0 when the file itself is likely a secret
#   skips_content_scan PATH      0 for files whose literals are test fixtures
#   text_has_secret TEXT         0 when TEXT contains a known credential format
#
# The content scan matches only formats that are credentials by construction
# (key prefixes, key blocks, URL credentials). It does not guess from variable
# names like `password =`, which flagged ordinary code far more often than
# real secrets.

# Files that are secrets by type, wherever they live.
SENSITIVE_PATH_PATTERNS=(
    '\.env$'
    '\.env\.(production|secrets)$'
    'local\.properties$'
    '\.keystore$'
    '\.jks$'
    'google-services\.json$'
    'credentials\.json$'
    '\.pem$'
    '\.p12$'
    '\.tfplan$'
)

# Paths mentioning secrets (in any directory or file name) are flagged unless
# the file is code or docs: `sync-secrets.sh` is a script, `secrets/prod.json`
# and `secrets.yaml` hold data.
SENSITIVE_NAME_PATTERN='(secret|apikey)'
CODE_OR_DOC_EXTENSION='\.(sh|bash|py|kt|kts|java|swift|md)$'

SECRET_CONTENT_PATTERNS=(
    'AKIA[0-9A-Z]{16}'
    '-----BEGIN (RSA|EC|DSA|OPENSSH|PGP) PRIVATE KEY-----'
    # URL-embedded credentials (`scheme://user:pass@host`); user may be empty.
    '://[^/:@[:space:]"'"'"']*:[^/@[:space:]"'"'"']{6,}@'
    # Query-string credentials such as JDBC `?password=...`.
    '[?&]password=[A-Za-z0-9+/=_.-]{6,}'
    'sk_live_[0-9A-Za-z]{16,}'
    'rk_live_[0-9A-Za-z]{16,}'
    'whsec_[0-9A-Za-z]{16,}'
    'ghp_[0-9A-Za-z]{30,}'
    'gho_[0-9A-Za-z]{30,}'
    'AIza[0-9A-Za-z_-]{30,}'
    # Split so this file does not match its own pattern.
    '"type": "service''_account"'
)

is_sensitive_path() {
    local path="$1" pattern
    for pattern in "${SENSITIVE_PATH_PATTERNS[@]}"; do
        grep -qiE -e "$pattern" <<< "$path" && return 0
    done
    grep -qiE -e "$SENSITIVE_NAME_PATTERN" <<< "$path" \
        && ! grep -qiE -e "$CODE_OR_DOC_EXTENSION" <<< "$path"
}

skips_content_scan() {
    local path="$1"
    grep -qE '(^|/)(package-lock\.json|yarn\.lock|pnpm-lock\.yaml|Cargo\.lock)$' <<< "$path" && return 0
    grep -qE '/src/(test|androidTest|commonTest|iosTest|desktopTest|wasmJsTest|androidDeviceTest|screenshotTest)/' <<< "$path" && return 0
    grep -qE '^scripts/tests/' <<< "$path"
}

text_has_secret() {
    local text="$1" pattern
    for pattern in "${SECRET_CONTENT_PATTERNS[@]}"; do
        grep -qiE -e "$pattern" <<< "$text" && return 0
    done
    return 1
}
