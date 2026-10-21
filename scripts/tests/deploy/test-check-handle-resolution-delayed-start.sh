#!/usr/bin/env bash
# The HTTP fixture must tolerate a cold Python startup on a slow test host.
set -euo pipefail

REPO_ROOT="$(git rev-parse --show-toplevel)"
TASK_TEMP="$(mktemp -d)"
trap 'rm -rf "$TASK_TEMP"' EXIT
export LOGDATE_TEST_REAL_PYTHON
LOGDATE_TEST_REAL_PYTHON="$(command -v python3)"
mkdir "$TASK_TEMP/bin"
cat > "$TASK_TEMP/bin/python3" <<'WRAPPER'
#!/usr/bin/env bash
set -euo pipefail
if [[ "${1:-}" == */stub.py ]]; then
    if [[ "${LOGDATE_TEST_STUB_MODE:-delay}" == exit ]]; then
        exit 7
    fi
    sleep 6
fi
exec "$LOGDATE_TEST_REAL_PYTHON" "$@"
WRAPPER
chmod +x "$TASK_TEMP/bin/python3"
LOGDATE_TEST_STUB_MODE=delay PATH="$TASK_TEMP/bin:$PATH" bash "$REPO_ROOT/scripts/tests/deploy/test-check-handle-resolution.sh"
status=0
OUTPUT="$(LOGDATE_TEST_STUB_MODE=exit PATH="$TASK_TEMP/bin:$PATH" bash "$REPO_ROOT/scripts/tests/deploy/test-check-handle-resolution.sh" 2>&1)" || status=$?
[[ "$status" -ne 0 && "$OUTPUT" == *"stub server exited before binding"* ]] || {
    echo "FAIL: an exited stub must fail before the startup timeout"
    exit 1
}
echo "PASS: delayed startup succeeds and an exited fixture fails promptly"
