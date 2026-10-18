#!/usr/bin/env bash
# Proves that the lexicon check refuses breaking changes to a published schema, accepts compatible
# ones, and refuses unpublishing. It builds a base commit in which the journal entry is published,
# without touching the working tree, and runs the checker against an exported copy of the schemas.
set -euo pipefail

root="$(git rev-parse --show-toplevel)"
work="$(mktemp -d)"
trap 'rm -r "$work"' EXIT

# A copy of the working tree's schemas and checker, sharing the installed packages.
mkdir -p "$work/tools/lexicon-check"
cp -R "$root/lexicons" "$work/lexicons"
cp -R "$root/tools/lexicon-check/check.mjs" "$root/tools/lexicon-check/compat.mjs" "$root/tools/lexicon-check/examples" \
    "$work/tools/lexicon-check/"
ln -s "$root/tools/lexicon-check/node_modules" "$work/tools/lexicon-check/node_modules"

# The base: HEAD with the journal entry listed as published. Written as a loose commit object, so
# no branch, index or hook is involved.
published='{"published": ["app.logdate.journal.entry"]}'
blob="$(printf '%s\n' "$published" | git -C "$root" hash-object -w --stdin)"
export GIT_INDEX_FILE="$work/index"
git -C "$root" read-tree HEAD
git -C "$root" update-index --add --cacheinfo "100644,$blob,lexicons/published.json"
tree="$(git -C "$root" write-tree)"
unset GIT_INDEX_FILE
base="$(git -C "$root" commit-tree "$tree" -p HEAD -m "lexicon breaking-change test base")"

export GIT_DIR
GIT_DIR="$(git -C "$root" rev-parse --absolute-git-dir)"
check() { LEXICON_BASE_REF="$base" node "$work/tools/lexicon-check/check.mjs" 2>&1; }
entry="$work/lexicons/app/logdate/journal/entry.json"
edit_entry() { node -e "const fs=require('fs');const d=JSON.parse(fs.readFileSync('$entry'));$1;fs.writeFileSync('$entry',JSON.stringify(d,null,2))"; }
fail() { echo "FAIL: $1" >&2; echo "$2" >&2; exit 1; }

printf '%s\n' "$published" > "$work/lexicons/published.json"
output="$(check)" || fail "an unchanged published schema should pass" "$output"

edit_entry "d.defs.main.record.properties.mood={type:'string'}"
output="$(check)" || fail "adding an optional field should pass" "$output"

edit_entry "delete d.defs.main.record.properties.updatedAt"
if output="$(check)"; then fail "removing a field from a published schema should fail" "$output"; fi
[[ "$output" == *"#main.record.updatedAt: the field was removed"* ]] || fail "the failure should name the removed field" "$output"

cp "$root/lexicons/app/logdate/journal/entry.json" "$entry"
printf '%s\n' '{"published": []}' > "$work/lexicons/published.json"
if output="$(check)"; then fail "unpublishing a schema should fail" "$output"; fi
[[ "$output" == *"app.logdate.journal.entry was published, and can't be unpublished"* ]] || fail "the failure should name the schema" "$output"

echo "Published-schema compatibility checks behave as expected."
