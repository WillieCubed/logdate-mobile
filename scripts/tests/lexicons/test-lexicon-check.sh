#!/usr/bin/env bash
# Validates LogDate's lexicon schemas and example records with the AT Protocol reference
# implementation, so CI catches a schema the reference implementation would reject.
# `./run setup --only test-deps` installs its dependencies.
set -euo pipefail

root="$(git rev-parse --show-toplevel)"
node "$root/tools/lexicon-check/check.mjs"
node --test "$root"/tools/lexicon-check/*.test.mjs
