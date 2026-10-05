#!/usr/bin/env python3

"""Allow scoped diagnostics; physical devices still require explicit user consent."""

import json
import re
import shlex
import sys
from pathlib import Path


PACKAGES = {"studio.hypertext.logdate", "co.reasonabletech.logdate"}
GRADLE_BASENAMES = {"gradle", "gradlew"}
SHELL_BASENAMES = {"sh", "bash", "zsh"}
CONNECTED_TEST_TASK = re.compile(r"(^|:)connected[\w-]*AndroidTest$")
INSTALL_TASK = re.compile(r"(^|:)(install|uninstall)[A-Z][\w-]*$")
SEPARATORS = {"\n", ";", "&&", "||", "|", "&", "(", ")"}
DEVICE_REASON = (
    "Only device inventory and explicitly selected, read-only LogDate diagnostics are allowed. "
    "Physical-device diagnostics require the user's explicit consent. "
    "Installs, resets, instrumentation, and arbitrary device shell commands are blocked."
)
HEREDOC = re.compile(
    r"(?m)^(?P<header>[^\n]*?)<<(?P<tabs>-?)[ \t]*"
    r"(?P<delimiter>'[^'\n]+'|\"[^\"\n]+\"|[A-Za-z_][\w]*)[^\n]*\n"
)


def emit_deny(reason: str) -> int:
    print(json.dumps({
        "hookSpecificOutput": {
            "hookEventName": "PreToolUse",
            "permissionDecision": "deny",
            "permissionDecisionReason": reason,
        },
        "systemMessage": reason,
    }))
    return 0


def tokenize(command: str) -> list[str]:
    lexer = shlex.shlex(command, posix=True, punctuation_chars=";&|()<>\n")
    lexer.whitespace = " \t\r"
    lexer.whitespace_split = True
    lexer.commenters = "#"
    tokens: list[str] = []
    for token in lexer:
        if "\n" in token and set(token) <= set("\n;&|()"):
            tokens.extend(part for part in re.split(r"(\n)", token) if part)
        else:
            tokens.append(token)
    return tokens


def read_only_adb(args: list[str]) -> bool:
    if args in (["devices"], ["devices", "-l"]):
        return True
    if len(args) < 4 or args[0] != "-s" or not re.fullmatch(r"[\w.:-]+", args[1]):
        return False
    args = args[2:]
    if args[0] == "shell":
        # adb concatenates shell arguments remotely; reject all shell syntax.
        if any(not re.fullmatch(r"[\w.-]+", arg) for arg in args[1:]):
            return False
        if len(args) == 3 and args[1] == "pidof":
            return args[2] in PACKAGES
        return len(args) == 4 and args[1] == "dumpsys" and args[2] in {
            "package", "jobscheduler"
        } and args[3] in PACKAGES
    if args[0] != "logcat" or "-d" not in args:
        return False
    options = args[1:]
    has_pid = False
    while options:
        option, *options = options
        if option == "-d":
            continue
        if re.fullmatch(r"--pid=[1-9][0-9]*", option):
            has_pid = True
            continue
        if option in {"--pid", "-t", "-v"} and options:
            value, *options = options
            if option == "-v" and value in {"brief", "threadtime", "time", "raw"}:
                continue
            if option in {"--pid", "-t"} and re.fullmatch(r"[1-9][0-9]*", value):
                has_pid = has_pid or option == "--pid"
                continue
        return False
    return has_pid


def command_reason(tokens: list[str], cwd: Path, depth: int) -> str | None:
    if not tokens:
        return None
    while tokens and (tokens[0] in {"command", "exec", "env"} or re.fullmatch(
        r"[A-Za-z_][A-Za-z_0-9]*=[^;]*", tokens[0]
    )):
        tokens = tokens[1:]
    if not tokens:
        return None
    executable = Path(tokens[0]).name
    args = tokens[1:]
    if executable == "adb":
        return None if read_only_adb(args) else DEVICE_REASON
    if executable == "fastboot":
        return "Fastboot commands are blocked for LogDate."
    if executable in GRADLE_BASENAMES and any(
        CONNECTED_TEST_TASK.search(arg) or INSTALL_TASK.search(arg) for arg in args
    ):
        return "Connected Android tests and Gradle install/uninstall tasks are blocked; use a Gradle Managed Device."
    if executable in SHELL_BASENAMES:
        if args and "c" in args[0].lstrip("-") and args[0].startswith("-") and len(args) > 1:
            return check_command(args[1], cwd, depth + 1)
        if args and not args[0].startswith("-"):
            return script_reason(args[0], cwd, depth)
    if executable.endswith(".sh"):
        return script_reason(tokens[0], cwd, depth)
    return None


def script_reason(name: str, cwd: Path, depth: int) -> str | None:
    path = Path(name)
    if not path.is_absolute():
        path = cwd / path
    try:
        content = path.read_text(encoding="utf-8")
    except (OSError, UnicodeError):
        return None
    return check_command(content, cwd, depth + 1)


def prepare_heredocs(command: str) -> tuple[str, list[str]]:
    shell_inputs: list[str] = []
    position = 0
    while match := HEREDOC.search(command, position):
        try:
            header = tokenize(match["header"])
        except ValueError:
            position = match.end()
            continue
        delimiter = match["delimiter"].strip("'\"")
        indentation = r"\t*" if match["tabs"] else ""
        closing = re.search(rf"(?m)^{indentation}{re.escape(delimiter)}\r?$", command[match.end():])
        if closing is None:
            raise ValueError("Unterminated heredoc")
        body_end = match.end() + closing.start()
        body = command[match.end():body_end]
        executes_input = any(Path(token).name in SHELL_BASENAMES for token in header)
        expands_input = match["delimiter"] == delimiter and re.search(r"\$\(|`", body)
        if executes_input or expands_input:
            shell_inputs.append(body)
        command = command[:match.end()] + command[match.end() + closing.end():]
        position = match.end()
    return command, shell_inputs


def check_command(command: str, cwd: Path, depth: int = 0) -> str | None:
    if depth > 8:
        return "Nested shell commands exceeded the Android policy inspection limit."
    try:
        command, shell_inputs = prepare_heredocs(command)
        for body in shell_inputs:
            reason = check_command(body, cwd, depth + 1)
            if reason:
                return reason
        tokens = tokenize(command)
    except ValueError:
        return "Cannot safely inspect malformed shell command."
    segment: list[str] = []
    redirect_target = False
    for token in tokens + [";"]:
        if redirect_target:
            redirect_target = False
            continue
        if token in SEPARATORS:
            reason = command_reason(segment, cwd, depth)
            if reason:
                return reason
            segment = []
        elif token in {">", ">>", "<", "<<"}:
            # Skip only the destination; arguments after it still reach adb.
            redirect_target = True
        else:
            segment.append(token)
    return None


def main() -> int:
    payload = json.load(sys.stdin)
    tool_input = payload.get("tool_input", {})
    command = tool_input.get("command") or tool_input.get("cmd", "")
    reason = check_command(command, Path(payload.get("cwd", str(Path.home()))))
    return emit_deny(reason) if reason else 0


if __name__ == "__main__":
    sys.exit(main())
