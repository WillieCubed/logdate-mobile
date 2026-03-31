#!/usr/bin/env python3

import json
import re
import shlex
import sys
from pathlib import Path


ANDROID_TOOL_BASENAMES = {
    "adb",
    "fastboot",
}

GRADLE_BASENAMES = {
    "gradle",
    "gradlew",
}

CONNECTED_TEST_TASK = re.compile(r"(^|:)(connected[\w-]*AndroidTest)$")
INSTALL_TASK = re.compile(r"(^|:)((install|uninstall)[A-Z][\w-]*)$")
SCRIPT_BANNED_PATTERN = re.compile(
    r"\badb\b|(^|:)(connected[\w-]*AndroidTest)\b|(^|:)((install|uninstall)[A-Z][\w-]*)\b",
    re.MULTILINE,
)


def emit_deny(reason: str) -> int:
    print(
        json.dumps(
            {
                "hookSpecificOutput": {
                    "hookEventName": "PreToolUse",
                    "permissionDecision": "deny",
                    "permissionDecisionReason": reason,
                },
                "systemMessage": reason,
            }
        )
    )
    return 0


def tokenize(command: str) -> list[str]:
    try:
        return shlex.split(command, posix=True)
    except ValueError:
        return command.split()


def basename(token: str) -> str:
    return Path(token).name


def maybe_read_text_file(token: str, cwd: str) -> str | None:
    candidate = Path(token)
    if not candidate.is_absolute():
        candidate = Path(cwd) / candidate

    if not candidate.exists() or not candidate.is_file():
        return None

    try:
        data = candidate.read_text(encoding="utf-8", errors="ignore")
    except OSError:
        return None

    return data[:200_000]


def main() -> int:
    payload = json.load(sys.stdin)
    command = payload.get("tool_input", {}).get("command", "")
    cwd = payload.get("cwd", str(Path.home()))
    if not command:
        return 0

    tokens = tokenize(command)
    lowered = [token.lower() for token in tokens]

    for token in tokens:
        if basename(token) in ANDROID_TOOL_BASENAMES:
            return emit_deny(
                "Android device-capable commands are blocked in Codex for LogDate. "
                "Do not use adb or fastboot here; use an emulator or Gradle Managed Device flow instead."
            )

    if any(basename(token) in GRADLE_BASENAMES for token in tokens):
        for token in tokens[1:]:
            stripped = token.lstrip(":")
            if CONNECTED_TEST_TASK.search(stripped):
                return emit_deny(
                    "Gradle connected Android test tasks are blocked in Codex for LogDate because they can target physical devices. "
                    "Use a Gradle Managed Device task or an emulator-only flow instead."
                )
            if INSTALL_TASK.search(stripped):
                return emit_deny(
                    "Gradle install/uninstall device tasks are blocked in Codex for LogDate. "
                    "Use a Gradle Managed Device or emulator-specific workflow instead."
                )

    joined = " ".join(lowered)
    if " connected" in joined and "androidtest" in joined:
        return emit_deny(
            "Connected Android test commands are blocked in Codex for LogDate because they can target physical devices. "
            "Use a Gradle Managed Device task or an emulator-only flow instead."
        )

    for token in tokens[1:]:
        contents = maybe_read_text_file(token, cwd)
        if contents and SCRIPT_BANNED_PATTERN.search(contents):
            return emit_deny(
                "Local scripts that invoke adb or connected-device Android Gradle tasks are blocked in Codex for LogDate. "
                "Use an emulator or Gradle Managed Device workflow instead."
            )

    return 0


if __name__ == "__main__":
    sys.exit(main())
