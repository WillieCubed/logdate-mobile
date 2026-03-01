#!/usr/bin/env bash
# validate-commit-scope.sh - Validates a commit scope against allowed-scopes.txt
#
# Usage: validate-commit-scope.sh <scope>
# Exit 0: valid scope (or empty scope, which is allowed for cross-cutting changes)
# Exit 1: invalid scope (prints suggestions)

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
SCOPES_FILE="$REPO_ROOT/allowed-scopes.txt"

scope="${1:-}"

# Empty scope is valid (for cross-cutting changes like "docs: update README")
if [[ -z "$scope" ]]; then
    exit 0
fi

# Load allowed scopes (strip comments and blank lines)
if [[ ! -f "$SCOPES_FILE" ]]; then
    echo "ERROR: allowed-scopes.txt not found at $SCOPES_FILE"
    echo "Run from the repository root or check your setup."
    exit 1
fi

allowed_scopes=()
while IFS= read -r line; do
    # Skip comments and blank lines
    line="$(echo "$line" | sed 's/^[[:space:]]*//' | sed 's/[[:space:]]*$//')"
    [[ -z "$line" || "$line" == \#* ]] && continue
    allowed_scopes+=("$line")
done < "$SCOPES_FILE"

# Exact match check
for s in "${allowed_scopes[@]}"; do
    if [[ "$scope" == "$s" ]]; then
        exit 0
    fi
done

# No match - find suggestions
echo ""
echo "ERROR: Invalid commit scope: '$scope'"
echo ""

suggestions=()

# Substring matches (scope contains or is contained by an allowed scope)
for s in "${allowed_scopes[@]}"; do
    if [[ "$s" == *"$scope"* ]] || [[ "$scope" == *"$s"* ]]; then
        suggestions+=("$s")
    fi
done

# Prefix matches (first 3+ chars match)
if [[ ${#suggestions[@]} -eq 0 ]] && [[ ${#scope} -ge 3 ]]; then
    prefix="${scope:0:3}"
    for s in "${allowed_scopes[@]}"; do
        if [[ "$s" == "$prefix"* ]]; then
            suggestions+=("$s")
        fi
    done
fi

# Character similarity heuristic for short scopes
if [[ ${#suggestions[@]} -eq 0 ]]; then
    scope_len=${#scope}
    for s in "${allowed_scopes[@]}"; do
        s_len=${#s}
        # Only compare scopes of similar length
        diff=$((scope_len - s_len))
        abs_diff=${diff#-}
        if [[ $abs_diff -le 2 ]]; then
            # Check if they share most characters positionally
            common=0
            i=0
            while [[ $i -lt $scope_len && $i -lt $s_len ]]; do
                if [[ "${scope:$i:1}" == "${s:$i:1}" ]]; then
                    common=$((common + 1))
                fi
                i=$((i + 1))
            done
            min_len=$((scope_len < s_len ? scope_len : s_len))
            threshold=$((min_len * 2 / 3))
            if [[ $min_len -gt 0 && $common -ge $threshold ]]; then
                suggestions+=("$s")
            fi
        fi
    done
fi

if [[ ${#suggestions[@]} -gt 0 ]]; then
    echo "Did you mean one of these?"
    for s in "${suggestions[@]}"; do
        echo "  - $s"
    done
else
    echo "No similar scopes found."
fi

echo ""
echo "Valid scopes are listed in: allowed-scopes.txt"
echo "Full documentation: docs/reference/standards/commit-scopes.md"
echo ""
exit 1
