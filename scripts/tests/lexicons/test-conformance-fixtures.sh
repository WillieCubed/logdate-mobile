#!/usr/bin/env bash
# Keeps the shared client conformance fixtures in conformance/ in step with the lexicons and the
# sealed-record format: the committed fixtures must be exactly what the generator produces from the
# lexicons, the generator's own tests must pass, and the Python reference implementation of
# sealing must reproduce every sealed fixture. `./run setup --only test-deps` installs what it needs.
set -euo pipefail

root="$(git rev-parse --show-toplevel)"
python="$root/scripts/passkey-verify/.venv/bin/python"

node "$root/tools/lexicon-check/conformance.mjs"
node --test "$root/tools/lexicon-check/conformance.test.mjs"
"$python" -I "$root/conformance/sealed/reference/verify.py"
