#!/usr/bin/env python3
"""
Identify (and optionally apply) safe updates for `gradle/libs.versions.toml`.

Defaults (intentionally conservative, since we are not running Gradle builds here):
- Only consider version keys that look "stable" (no alpha/beta/rc/snapshot/milestone qualifiers)
- Only update within the same major version (or same major+minor when major == 0)

Usage:
  python3 scripts/version-catalog-updates.py
  python3 scripts/version-catalog-updates.py --include-prerelease
  python3 scripts/version-catalog-updates.py --apply
"""

from __future__ import annotations

import re
import subprocess
import sys
import tomllib
import xml.etree.ElementTree as ET
from dataclasses import dataclass
from pathlib import Path
from typing import Iterable


ROOT = Path(__file__).resolve().parents[1]
CATALOG = ROOT / "gradle" / "libs.versions.toml"


QUALIFIER_RE = re.compile(r"(?i)(alpha|beta|rc|snapshot|m\d+)")
LINE_RE = re.compile(r'^([a-zA-Z0-9_.-]+)\s*=\s*"([^"]*)"\s*$')


@dataclass(frozen=True)
class Coordinate:
    group: str
    name: str

    @property
    def maven_metadata_url(self) -> str:
        group_path = self.group.replace(".", "/")
        return f"https://repo1.maven.org/maven2/{group_path}/{self.name}/maven-metadata.xml"

    def __str__(self) -> str:
        return f"{self.group}:{self.name}"


def _parse_semverish(version: str) -> tuple[int, int, int, str]:
    """
    Best-effort parsing:
    - Pull leading X.Y.Z or X.Y, treat missing pieces as 0
    - Keep remaining suffix for tiebreaking
    """
    m = re.match(r"^(\d+)(?:\.(\d+))?(?:\.(\d+))?(.*)$", version)
    if not m:
        return (0, 0, 0, version)
    major = int(m.group(1) or 0)
    minor = int(m.group(2) or 0)
    patch = int(m.group(3) or 0)
    suffix = m.group(4) or ""
    return (major, minor, patch, suffix)


def _is_snapshot(version: str) -> bool:
    return "SNAPSHOT" in version.upper()


def _is_stable(version: str) -> bool:
    if _is_snapshot(version):
        return False
    if QUALIFIER_RE.search(version):
        return False
    return True


def _same_update_lane(current: str, candidate: str) -> bool:
    cmaj, cmin, _, _ = _parse_semverish(current)
    nmaj, nmin, _, _ = _parse_semverish(candidate)
    if cmaj == 0:
        return (nmaj, nmin) == (cmaj, cmin)
    return nmaj == cmaj


def _fetch_versions(coord: Coordinate) -> list[str]:
    try:
        # Use curl so we don't depend on Python's cert store configuration.
        # Maven Central is HTTPS; curl uses the system trust store.
        proc = subprocess.run(
            ["curl", "-fsSL", coord.maven_metadata_url],
            check=False,
            capture_output=True,
            text=True,
            timeout=20,
        )
    except (OSError, subprocess.TimeoutExpired):
        return []

    if proc.returncode != 0:
        return []

    try:
        root = ET.fromstring(proc.stdout)
    except ET.ParseError:
        return []

    versions_node = root.find("./versioning/versions")
    if versions_node is None:
        return []

    versions: list[str] = []
    for child in versions_node:
        if child.tag != "version":
            continue
        if child.text:
            versions.append(child.text.strip())
    return versions


def _version_sort_key(version: str) -> tuple[int, int, int, int, int, str]:
    major, minor, patch, suffix = _parse_semverish(version)
    stable_flag = 1 if _is_stable(version) else 0
    # Prefer "bare" releases (no suffix) over compatibility/build suffixes.
    release_flag = 1 if suffix == "" else 0
    return (major, minor, patch, release_flag, stable_flag, suffix)


def _pick_latest(current: str, versions: Iterable[str], *, include_prerelease: bool) -> str | None:
    if include_prerelease:
        candidates = [v for v in versions if not _is_snapshot(v) and _same_update_lane(current, v)]
    else:
        candidates = [v for v in versions if _is_stable(v) and _same_update_lane(current, v)]
    if not candidates:
        return None
    candidates.sort(key=_version_sort_key)
    latest = candidates[-1]
    if latest == current:
        return None
    return latest


def _choose_sample_coords(doc: dict) -> dict[str, Coordinate]:
    """
    Pick a representative Maven coordinate for each version key by scanning
    `libraries` entries that reference it.
    """
    result: dict[str, Coordinate] = {}
    libs = doc.get("libraries", {})
    for lib in libs.values():
        if not isinstance(lib, dict):
            continue
        ver_ref = lib.get("version", {}).get("ref") if isinstance(lib.get("version"), dict) else lib.get("version.ref")
        # tomllib keeps dotted keys literal, so support both shapes
        if ver_ref is None and isinstance(lib.get("version.ref"), str):
            ver_ref = lib.get("version.ref")
        if not isinstance(ver_ref, str):
            continue
        if ver_ref in result:
            continue

        module = lib.get("module")
        if isinstance(module, str) and ":" in module:
            group, name = module.split(":", 1)
            result[ver_ref] = Coordinate(group=group, name=name)
            continue

        group = lib.get("group")
        name = lib.get("name")
        if isinstance(group, str) and isinstance(name, str):
            result[ver_ref] = Coordinate(group=group, name=name)
    return result


def _apply_updates_to_file(path: Path, updates: dict[str, str]) -> None:
    lines = path.read_text(encoding="utf-8").splitlines(keepends=False)
    out: list[str] = []

    for line in lines:
        m = LINE_RE.match(line)
        if not m:
            out.append(line)
            continue
        key = m.group(1)
        if key not in updates:
            out.append(line)
            continue
        new_version = updates[key]
        out.append(f'{key} = "{new_version}"')

    path.write_text("\n".join(out) + "\n", encoding="utf-8")


def main(argv: list[str]) -> int:
    apply = "--apply" in argv
    include_prerelease = "--include-prerelease" in argv
    if not CATALOG.exists():
        print(f"Missing version catalog: {CATALOG}", file=sys.stderr)
        return 2

    raw = CATALOG.read_bytes()
    doc = tomllib.loads(raw.decode("utf-8"))

    versions: dict[str, str] = doc.get("versions", {})
    sample_coords = _choose_sample_coords(doc)

    candidates: list[tuple[str, str, str, str]] = []
    updates: dict[str, str] = {}

    for key, current in versions.items():
        if not isinstance(key, str) or not isinstance(current, str):
            continue
        if not include_prerelease and not _is_stable(current):
            continue
        coord = sample_coords.get(key)
        if coord is None:
            continue
        available = _fetch_versions(coord)
        latest = _pick_latest(current, available, include_prerelease=include_prerelease)
        if latest is None:
            continue
        candidates.append((key, current, latest, str(coord)))
        updates[key] = latest

    if not candidates:
        print("No safe stable-lane updates found.")
        return 0

    candidates.sort(key=lambda row: row[0])
    heading = (
        "Safe updates (same major; including pre-releases):"
        if include_prerelease
        else "Safe stable-lane updates (same major; no pre-releases):"
    )
    print(heading)
    for key, cur, lat, coord in candidates:
        print(f"- {key}: {cur} -> {lat}  ({coord})")

    if apply:
        _apply_updates_to_file(CATALOG, updates)
        print(f"\nApplied {len(updates)} updates to {CATALOG}")

    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
