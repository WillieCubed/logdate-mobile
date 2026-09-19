#!/usr/bin/env bash
# Command-line tools the selected target needs.

tools_describe() {
    printf 'Required command-line tools are installed'
}

tools_needed() {
    printf '%s\n' git java jq
    [[ "$SETUP_TARGET" == "local" ]] && return 0
    printf '%s\n' gh gcloud curl keytool shasum base64
}

tools_check() {
    local tool missing=()
    while IFS= read -r tool; do
        command -v "$tool" >/dev/null 2>&1 || { missing+=("$tool"); continue; }
        # macOS ships /usr/bin/java as a stub that exists without any JDK.
        [[ "$tool" == "java" ]] && ! java -version >/dev/null 2>&1 && missing+=("$tool")
    done < <(tools_needed)
    [[ ${#missing[@]} -eq 0 ]] && return 0

    printf 'Missing: %s\n' "${missing[*]}"
    printf 'Install with Homebrew: brew install %s\n' "$(tools_brew_names "${missing[@]}")"
    return 2
}

tools_brew_names() {
    local tool names=()
    for tool in "$@"; do
        case "$tool" in
            gcloud) names+=(--cask google-cloud-sdk) ;;
            java|keytool) names+=(openjdk@17) ;;
            *) names+=("$tool") ;;
        esac
    done
    printf '%s' "${names[*]}"
}
