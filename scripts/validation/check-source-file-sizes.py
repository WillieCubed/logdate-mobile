#!/usr/bin/env python3
"""Reject source files above 500 lines; directories include nested source files."""

import argparse
import subprocess
import sys
from pathlib import Path

SOURCE_SUFFIXES = {".swift", ".kt", ".kts", ".py"}
MAX_LINES = 500


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("paths", nargs="*")
    parser.add_argument("--stdin", action="store_true", help="Read one path per line")
    parser.add_argument("--base", help="Check source files changed from this Git revision to HEAD")
    args = parser.parse_args()
    paths = list(args.paths)
    if args.stdin:
        paths.extend(line.rstrip("\n") for line in sys.stdin if line.strip())
    if args.base:
        result = subprocess.run(
            ["git", "diff", "--name-only", "--diff-filter=ACMR", "-z", args.base, "HEAD"],
            stdout=subprocess.PIPE, check=True,
        )
        paths.extend(path for path in result.stdout.decode().split("\0") if path)
    if not paths and not (args.stdin or args.base):
        parser.error("Provide source paths, --stdin, or --base")
    sources = set()
    for name in paths:
        path = Path(name)
        if not path.exists() and args.stdin:
            continue  # A file changed by an earlier commit may have been deleted later.
        if not path.exists():
            parser.error(f"Requested path does not exist: {name}")
        candidates = path.rglob("*") if path.is_dir() else [path]
        sources.update(file for file in candidates if file.is_file() and file.suffix in SOURCE_SUFFIXES)
    violations = []
    for path in sorted(sources):
        count = len(path.read_text().splitlines())
        if count > MAX_LINES:
            violations.append(f"{path}: {count} lines (maximum {MAX_LINES})")
    if violations:
        print("\n".join(violations))
        return 1
    print(f"Source file size check passed ({len(sources)} files, maximum {MAX_LINES} lines).")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
