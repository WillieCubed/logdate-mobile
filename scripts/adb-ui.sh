#!/usr/bin/env bash
# Small helper for driving a LogDate build over adb during manual verification.
#
# uiautomator's XML dump is the only reliable way to find a Compose element's
# bounds, and every caller needs the same dump -> match -> tap-the-centre
# sequence. Keeping it here means the verification steps read as intent rather
# than as three lines of sed per tap.
#
# Usage:
#   adb-ui.sh <serial> text                       # list visible text
#   adb-ui.sh <serial> tap <needle>               # tap the first node matching
#   adb-ui.sh <serial> find <needle>              # exit 0 if present
#   adb-ui.sh <serial> scroll-to <needle> [tries] # swipe up until present
#   adb-ui.sh <serial> type <string>              # type into the focused field
set -euo pipefail

readonly SERIAL="${1:?serial required}"
readonly COMMAND="${2:?command required}"
shift 2

# Anything that types or taps goes to whatever happens to be in front. A phone that rings, an
# install that restarts the app, or the owner picking the device up is enough to put another app
# there between one step and the next -- which is how a username once ended up typed into somebody's
# text conversation. Set LOGDATE_UI_REQUIRE_PACKAGE and every input refuses to fire unless that
# package is still the focused one.
readonly REQUIRED_PACKAGE="${LOGDATE_UI_REQUIRE_PACKAGE:-}"

focused_package() {
    adb -s "$SERIAL" shell "dumpsys window" 2>/dev/null |
        grep -m1 "mCurrentFocus" |
        sed -nE 's|.* ([A-Za-z0-9_.]+)/[A-Za-z0-9_.$]+.*|\1|p'
}

require_foreground() {
    [[ -z "$REQUIRED_PACKAGE" ]] && return 0
    local focused
    focused="$(focused_package)"
    if [[ "$focused" != "$REQUIRED_PACKAGE" ]]; then
        echo "refusing to send input: $REQUIRED_PACKAGE is not in the foreground (found: ${focused:-none})" >&2
        return 1
    fi
    return 0
}

dump() {
    adb -s "$SERIAL" shell "uiautomator dump /sdcard/logdate-ui.xml >/dev/null 2>&1; cat /sdcard/logdate-ui.xml" 2>/dev/null
}

# Prints "<x> <y>" for the centre of the first node whose attributes contain the
# needle, or nothing when there is no match.
centre_of() {
    dump | tr '<' '\n' | grep -F -- "$1" |
        grep -oE 'bounds="\[[0-9]+,[0-9]+\]\[[0-9]+,[0-9]+\]"' | head -1 |
        sed -E 's/.*\[([0-9]+),([0-9]+)\]\[([0-9]+),([0-9]+)\].*/\1 \2 \3 \4/' |
        awk 'NF==4 {print int(($1+$3)/2), int(($2+$4)/2)}'
}

case "$COMMAND" in
    text)
        dump | tr '<' '\n' | grep -oE '(text|content-desc)="[^"]{1,80}"' | sort -u
        ;;
    find)
        [[ -n "$(centre_of "${1:?needle required}")" ]]
        ;;
    tap)
        needle="${1:?needle required}"
        require_foreground || exit 1
        read -r x y <<<"$(centre_of "$needle")"
        [[ -n "${x:-}" ]] || { echo "no match for: $needle" >&2; exit 1; }
        adb -s "$SERIAL" shell "input tap $x $y" >/dev/null
        sleep "${2:-4}"
        echo "tapped '$needle' at $x,$y"
        ;;
    scroll-to)
        needle="${1:?needle required}"
        require_foreground || exit 1
        for _ in $(seq 1 "${2:-6}"); do
            [[ -n "$(centre_of "$needle")" ]] && { echo "found: $needle"; exit 0; }
            adb -s "$SERIAL" shell "input swipe 540 1700 540 900 300" >/dev/null
            sleep 2
        done
        echo "never appeared: $needle" >&2
        exit 1
        ;;
    type)
        require_foreground || exit 1
        adb -s "$SERIAL" shell "input text '${1//\'/}'" >/dev/null
        sleep 2
        ;;
    foreground)
        focused_package
        ;;
    *)
        echo "unknown command: $COMMAND" >&2
        exit 1
        ;;
esac
