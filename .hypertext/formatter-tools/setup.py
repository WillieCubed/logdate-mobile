#!/usr/bin/env python3
"""Prepare only pinned formatter dependencies; never run on commit."""

import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys

TOOLS = Path(__file__).resolve().parent
ROOT = TOOLS.parents[1]
SPEC = json.loads((TOOLS / "tools.json").read_text())
CACHE = (
    Path(os.environ.get("XDG_CACHE_HOME", str(Path.home() / ".cache")))
    / "hypertext-studio/formatters"
)


def prettier_version():
    source = SPEC["prettier"]
    if "version" in source:
        return source["version"]
    manifest = ROOT / source["manifest"]
    data = json.loads(manifest.read_text())
    value = next(
        (
            data.get(k, {}).get("prettier")
            for k in ("devDependencies", "dependencies")
            if data.get(k, {}).get("prettier")
        ),
        None,
    )
    if value and value.startswith("catalog:"):
        workspace = (ROOT / "pnpm-workspace.yaml").read_text()
        match = re.search(
            r"^\s+prettier:\s*[\'\"]?(\d+\.\d+\.\d+)[\'\"]?\s*$", workspace, re.M
        )
        value = match[1] if match else None
    if not value or not re.fullmatch(r"\d+\.\d+\.\d+", value):
        raise ValueError(
            "Existing Prettier must have an exact repository pin before setup"
        )
    return value


def npm_command():
    npm = shutil.which("npm")
    if not npm:
        raise ValueError("Install Node/npm to prepare pinned Markdown formatting")
    path = Path(npm).resolve()
    script = (
        path
        if path.suffix == ".js"
        else path.parent / "node_modules/npm/bin/npm-cli.js"
    )
    return ["node", str(script)] if script.is_file() else [npm]


def npm_inventory_matches(folder, lock, recipe):
    """Check the installed locked package inventory, including every direct plugin."""
    packages = lock.get("packages", {})
    for name, version in recipe["dependencies"].items():
        if not re.fullmatch(r"\d+\.\d+\.\d+(?:-[\w.-]+)?", version):
            raise ValueError("Formatter dependencies require exact pins: " + name)
        metadata = folder / "node_modules" / name / "package.json"
        try:
            if json.loads(metadata.read_text()).get("version") != version:
                return False
        except (OSError, ValueError):
            return False
    for name, expected in packages.items():
        if not name:
            continue
        relative = Path(name)
        if (
            relative.is_absolute()
            or ".." in relative.parts
            or relative.parts[0] != "node_modules"
        ):
            raise ValueError("Formatter lock contains an unsafe package path")
        metadata = folder / relative / "package.json"
        if expected.get("optional") and not metadata.exists():
            # npm omits platform-incompatible optional packages. Direct dependencies were
            # checked above and never receive this exemption.
            continue
        try:
            if json.loads(metadata.read_text()).get("version") != expected.get(
                "version"
            ):
                return False
        except (OSError, ValueError):
            return False
    return True


def prepare_npm(folder, recipe, lock_bytes):
    """Reuse only a complete install bound to the exact reviewed integrity lock."""
    data = json.loads(lock_bytes)
    if (
        data.get("packages", {}).get("", {}).get("dependencies")
        != recipe["dependencies"]
    ):
        raise ValueError("Formatter lock differs from current repository pins")
    for name, package in data["packages"].items():
        if name and (not package.get("version") or not package.get("integrity")):
            raise ValueError(
                "Formatter lock lacks package version or integrity: " + name
            )
    wanted = {
        "schema_version": 1,
        "lock_sha256": hashlib.sha256(lock_bytes).hexdigest(),
        "recipe_sha256": hashlib.sha256(
            json.dumps(recipe, sort_keys=True).encode()
        ).hexdigest(),
    }
    marker = folder / ".installed.json"
    try:
        complete = json.loads(marker.read_text()) == wanted
    except (OSError, ValueError):
        complete = False
    if complete and npm_inventory_matches(folder, data, recipe):
        return
    marker.unlink(missing_ok=True)
    subprocess.run(
        [
            *npm_command(),
            "ci",
            "--prefix",
            str(folder),
            "--ignore-scripts",
            "--no-audit",
            "--no-fund",
            "--registry=https://registry.npmjs.org",
        ],
        check=True,
    )
    if not npm_inventory_matches(folder, data, recipe):
        raise ValueError(
            "Formatter dependency installation is incomplete; rerun hook setup"
        )
    # A failed or interrupted npm ci never publishes a successful installation marker.
    temporary = marker.with_suffix(".tmp." + str(os.getpid()))
    try:
        temporary.write_text(json.dumps(wanted, indent=2) + "\n")
        os.replace(temporary, marker)
    finally:
        temporary.unlink(missing_ok=True)


def main():
    version = prettier_version()
    recipe = {
        "name": "studio-formatter-runtime",
        "private": True,
        "dependencies": {"prettier": version, **SPEC["npm"]},
    }
    key = hashlib.sha256(json.dumps(recipe, sort_keys=True).encode()).hexdigest()[:20]
    folder = CACHE / "npm" / key
    folder.mkdir(parents=True, exist_ok=True)
    manifest = folder / "package.json"
    manifest.write_text(json.dumps(recipe, indent=2) + "\n")
    lock = TOOLS / "npm-lock.json"
    if not lock.exists():
        subprocess.run(
            [
                *npm_command(),
                "install",
                "--prefix",
                str(folder),
                "--package-lock-only",
                "--ignore-scripts",
                "--no-audit",
                "--no-fund",
                "--registry=https://registry.npmjs.org",
            ],
            check=True,
        )
        lock.write_bytes((folder / "package-lock.json").read_bytes())
    data = json.loads(lock.read_text())
    if data["packages"][""]["dependencies"] != recipe["dependencies"]:
        raise ValueError(
            "Formatter lock differs from current repository pins; regenerate npm-lock.json with the reviewed dependency change"
        )
    (folder / "package-lock.json").write_bytes(lock.read_bytes())
    prepare_npm(folder, recipe, lock.read_bytes())
    ruffdir = CACHE / "ruff" / SPEC["ruff"]
    python = ruffdir / ("Scripts/python.exe" if os.name == "nt" else "bin/python")
    ruff = ruffdir / ("Scripts/ruff.exe" if os.name == "nt" else "bin/ruff")
    if not python.is_file():
        subprocess.run([sys.executable, "-m", "venv", str(ruffdir)], check=True)
    if (
        not ruff.is_file()
        or subprocess.check_output([str(ruff), "--version"]).strip()
        != ("ruff " + SPEC["ruff"]).encode()
    ):
        subprocess.run(
            [
                str(python),
                "-m",
                "pip",
                "install",
                "--disable-pip-version-check",
                "ruff==" + SPEC["ruff"],
            ],
            check=True,
        )
    for command in SPEC.get("native_setup", []):
        subprocess.run(
            [
                v.replace("{repo}", str(ROOT)).replace("{root}", str(ROOT))
                for v in command
            ],
            cwd=ROOT,
            check=True,
        )
    runtime = {
        "npm": str(folder / "node_modules"),
        "prettier": version,
        "ruff": str(ruff),
        "spec": SPEC,
    }
    (TOOLS / "runtime.json").write_text(json.dumps(runtime, indent=2) + "\n")
    print("Pinned commit formatters prepared.")


if __name__ == "__main__":
    main()
