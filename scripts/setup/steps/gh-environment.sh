#!/usr/bin/env bash
# The GitHub environment holding deploy secrets: no reviewer gate, and only
# DEPLOY_BRANCHES and DEPLOY_TAGS may deploy with it.

gh_environment_describe() {
    printf 'GitHub environment %s exists and only %s may deploy with it' \
        "$GITHUB_ENVIRONMENT" "$(gh_environment_desired | tr '\n' ' ' | sed 's/ $//')"
}

gh_environment_requires() {
    printf 'auth'
}

# One "type:name" line per allowed ref, sorted, e.g. "tag:android-v*".
gh_environment_desired() {
    # read -a: no glob expansion of the patterns.
    local branches=() tags=() ref
    read -r -a branches <<< "${DEPLOY_BRANCHES:-}"
    read -r -a tags <<< "${DEPLOY_TAGS:-}"
    {
        for ref in ${branches[@]+"${branches[@]}"}; do printf 'branch:%s\n' "$ref"; done
        for ref in ${tags[@]+"${tags[@]}"}; do printf 'tag:%s\n' "$ref"; done
    } | sort
}

gh_environment_api() {
    printf 'repos/%s/environments/%s' "$(repo_slug)" "$GITHUB_ENVIRONMENT"
}

gh_environment_current_policies() {
    gh api "$(gh_environment_api)/deployment-branch-policies" \
        --jq '.branch_policies[] | "\(.type):\(.name)"' | sort
}

gh_environment_check() {
    local env drift=0
    env="$(gh api "$(gh_environment_api)" 2>/dev/null)" || {
        printf 'Environment %s does not exist\n' "$GITHUB_ENVIRONMENT"
        return 1
    }
    if [[ "$(printf '%s' "$env" | jq '[.protection_rules[]? | select(.type == "required_reviewers")] | length')" != "0" ]]; then
        printf 'A required-reviewer rule pauses every deploy until someone approves it\n'
        drift=1
    fi
    if [[ "$(printf '%s' "$env" | jq -r '.deployment_branch_policy.custom_branch_policies // false')" != "true" ]]; then
        printf 'Any branch can deploy with this environment and read its secrets\n'
        drift=1
    elif [[ "$(gh_environment_current_policies)" != "$(gh_environment_desired)" ]]; then
        printf 'Allowed refs are [%s], expected [%s]\n' \
            "$(gh_environment_current_policies | tr '\n' ' ')" "$(gh_environment_desired | tr '\n' ' ')"
        drift=1
    fi
    return "$drift"
}

gh_environment_apply() {
    local api policy current desired id
    api="$(gh_environment_api)"
    jq -n '{reviewers: [], wait_timer: 0,
            deployment_branch_policy: {protected_branches: false, custom_branch_policies: true}}' \
        | gh api -X PUT "$api" --input - >/dev/null
    log_info "Environment $GITHUB_ENVIRONMENT: no required reviewer, custom deployment refs"

    current="$(gh_environment_current_policies)"
    desired="$(gh_environment_desired)"
    while IFS= read -r policy; do
        [[ -z "$policy" ]] && continue
        grep -qxF "$policy" <<< "$current" && continue
        jq -n --arg type "${policy%%:*}" --arg name "${policy#*:}" '{type: $type, name: $name}' \
            | gh api -X POST "$api/deployment-branch-policies" --input - >/dev/null
        log_info "Allowed $policy"
    done <<< "$desired"

    while IFS=$'\t' read -r id policy; do
        [[ -z "$id" ]] && continue
        grep -qxF "$policy" <<< "$desired" && continue
        gh api -X DELETE "$api/deployment-branch-policies/$id" >/dev/null
        log_info "Removed $policy"
    done < <(gh api "$api/deployment-branch-policies" --jq '.branch_policies[] | "\(.id)\t\(.type):\(.name)"')
}
