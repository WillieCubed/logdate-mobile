"""Retain the primary acceptance failure and its notes on supported Python hosts."""

import sys


def add_failure_note(error, message):
    add_note = getattr(error, "add_note", None)
    if add_note is None:
        notes = getattr(error, "__notes__", None)
        if notes is None:
            error.__notes__ = [message]
        else:
            notes.append(message)
        try:
            print(f"{type(error).__name__}: {message}", file=sys.stderr, flush=True)
        except (OSError, ValueError):
            pass
    else:
        add_note(message)
