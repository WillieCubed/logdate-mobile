#!/usr/bin/env bash
#
# Verifies that every included Gradle module exposes a ktlintCheck task.

set -euo pipefail

REPO_ROOT="$(git rev-parse --show-toplevel)"
cd "$REPO_ROOT"

modules=()
while IFS= read -r mod; do
    modules+=("$mod")
done < <(rg --no-filename --only-matching 'include\(":[^"]+"\)' settings.gradle.kts | sed -E 's/include\("([^"]+)"\)/\1/')

if [[ ${#modules[@]} -eq 0 ]]; then
    echo "No included modules were found in settings.gradle.kts."
    exit 1
fi

covered=()
missing=()

for mod in "${modules[@]}"; do
    if ./gradlew "${mod}:tasks" --all --quiet 2>/dev/null | grep -q "ktlintCheck"; then
        covered+=("$mod")
    else
        missing+=("$mod")
    fi
done

echo "ktlint task coverage: ${#covered[@]}/${#modules[@]} modules"

if [[ ${#missing[@]} -gt 0 ]]; then
    echo "Modules missing ktlintCheck:"
    for mod in "${missing[@]}"; do
        echo "  - $mod"
    done
    exit 1
fi

echo "All included modules expose ktlintCheck."
