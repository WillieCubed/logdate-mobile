#!/usr/bin/env python3
"""Pinned stdin native formatters; downloads occur only in explicit setup commands."""

import argparse
import hashlib
import io
import os
from pathlib import Path
import platform
import subprocess
import sys
import tempfile
import urllib.request
import zipfile

ROOT = Path(__file__).resolve().parents[2]
CACHE = (
    Path(os.environ.get("XDG_CACHE_HOME", str(Path.home() / ".cache")))
    / "hypertext-formatters"
)
SWIFT_VERSION = "0.64.0"
SWIFT_ARCHIVE = "95ea62625d0b898a7af870293311ae6f09e4a3147ecd900593687e4b9ab2fd98"
SWIFT_BINARY = "c2b098cc3ae30ff9fdd1acf7342f9b6efac4c403cf0b4e2d473690c92ac41210"
TF_VERSION = "1.15.5"
TF_HASHES = {
    "darwin_arm64": "01137660510005b918bba82154866fbeac4393163d8277c2abe861dfb5842c3c",
    "darwin_amd64": "3687d07c034b3e7deed5b072cd8ae2b34835bcb139baec3fc4f5fd534dabf5ed",
    "linux_arm64": "06e7b48de826146c6d9331ba35b13da12332d8392be30d1dd6b789ba4713fff0",
    "linux_amd64": "702b2136af6728c8ff037f843dd2dbce2b7ad88786b7381d1d72aefa250f601c",
}
# Archive digests from HashiCorp's versioned SHA256SUMS and PyPI release metadata.
CLANG_VERSION = "23.1.3"
CLANG_WHEELS = {
    "darwin_arm64": (
        "https://files.pythonhosted.org/packages/47/a5/2007b7a7a53dfe417e66d0a6b38b2f87f16391bfe6fe522d436d1e50be1c/clang_format-23.1.3-py2.py3-none-macosx_11_0_arm64.whl",
        "79f53ac55434af6dda88eb90eeb65d903ff920c910f78041de41e3ca666dfba5",
    ),
    "darwin_amd64": (
        "https://files.pythonhosted.org/packages/05/0f/a1813ede1e149e700ee810baf22dd9c863ca0bd719d73f86e8c10b67a6bb/clang_format-23.1.3-py2.py3-none-macosx_10_9_x86_64.whl",
        "0b8e86cc5a870ca42989bd1ccfe5220fbab96e386922d0a8f0ca350db30593df",
    ),
    "linux_arm64": (
        "https://files.pythonhosted.org/packages/1d/00/a0f99e24dc4892fb120e82fa9cf6e8fcb92714181ffd1165ab7eaf5ba1fd/clang_format-23.1.3-py2.py3-none-manylinux_2_26_aarch64.manylinux_2_28_aarch64.whl",
        "072113527b49a9c0ab9058006d9fe20e583823e6482608a34e1883dd2c49ae7d",
    ),
    "linux_amd64": (
        "https://files.pythonhosted.org/packages/11/d1/658d3da4dcbca98620956868c7cf6f4ba19fccecbdfc30cc055e19c9d4a7/clang_format-23.1.3-py2.py3-none-manylinux_2_27_x86_64.manylinux_2_28_x86_64.whl",
        "58bf1804216501669832a39f2415e14284337438a062ff564729792adec664ac",
    ),
}


def checked(data, digest):
    if hashlib.sha256(data).hexdigest() != digest:
        raise RuntimeError(
            "Native formatter checksum differs from its reviewed pin; rerun setup after inspection"
        )
    return data


def atomic_write(path, data, executable=False):
    path.parent.mkdir(parents=True, exist_ok=True)
    descriptor, name = tempfile.mkstemp(prefix=".formatter-", dir=path.parent)
    temporary = Path(name)
    try:
        with os.fdopen(descriptor, "wb") as output:
            output.write(data)
        if executable:
            temporary.chmod(0o755)
        os.replace(temporary, path)
    finally:
        temporary.unlink(missing_ok=True)


def download(url):
    with urllib.request.urlopen(url, timeout=120) as response:
        data = response.read(128 * 1024 * 1024 + 1)
    if len(data) > 128 * 1024 * 1024:
        raise RuntimeError("Native formatter download exceeds 128 MiB")
    return data


def platform_key():
    arch = {
        "arm64": "arm64",
        "aarch64": "arm64",
        "x86_64": "amd64",
        "AMD64": "amd64",
    }.get(platform.machine())
    key = platform.system().lower() + "_" + str(arch)
    if key not in TF_HASHES:
        raise RuntimeError(
            "Pinned formatter setup supports macOS/Linux on arm64 or x86_64"
        )
    return key


def zip_tool(tool, setup=False):
    key = platform_key()
    if tool == "terraform":
        filename = f"terraform_{TF_VERSION}_{key}.zip"
        url = f"https://releases.hashicorp.com/terraform/{TF_VERSION}/{filename}"
        digest, member = TF_HASHES[key], "terraform"
        directory = CACHE / "terraform" / TF_VERSION / key
    else:
        url, digest = CLANG_WHEELS[key]
        filename = url.rsplit("/", 1)[1]
        member = "clang_format/data/bin/clang-format"
        directory = CACHE / "clang-format" / CLANG_VERSION / key
    archive, binary = directory / filename, directory / Path(member).name
    if not archive.is_file():
        if not setup:
            raise RuntimeError(
                f"Native formatter is not prepared; run native-addons.py setup-{tool}"
            )
        atomic_write(archive, checked(download(url), digest))
    with zipfile.ZipFile(io.BytesIO(checked(archive.read_bytes(), digest))) as bundle:
        executable = bundle.read(member)
    if not binary.is_file():
        if not setup:
            raise RuntimeError(
                f"Native formatter executable is missing; run native-addons.py setup-{tool}"
            )
        atomic_write(binary, executable, executable=True)
    checked(binary.read_bytes(), hashlib.sha256(executable).hexdigest())
    return binary


def swift_tool(setup=False):
    if platform.system() != "Darwin":
        raise RuntimeError("Pinned SwiftFormat setup requires macOS")
    path = CACHE / "swiftformat" / SWIFT_VERSION / "swiftformat"
    if not path.is_file():
        if not setup:
            raise RuntimeError(
                "SwiftFormat is not prepared; run native-addons.py setup-swift"
            )
        url = f"https://github.com/nicklockwood/SwiftFormat/releases/download/{SWIFT_VERSION}/swiftformat.zip"
        with zipfile.ZipFile(
            io.BytesIO(checked(download(url), SWIFT_ARCHIVE))
        ) as bundle:
            atomic_write(
                path, checked(bundle.read("swiftformat"), SWIFT_BINARY), executable=True
            )
    checked(path.read_bytes(), SWIFT_BINARY)
    return path


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "tool",
        choices=[
            "swift",
            "terraform",
            "metal",
            "c",
            "setup-swift",
            "setup-terraform",
            "setup-metal",
            "setup-c",
        ],
    )
    parser.add_argument("--path", default="input.swift")
    args = parser.parse_args()
    setup = args.tool.startswith("setup-")
    tool = args.tool.removeprefix("setup-")
    binary = swift_tool(setup) if tool == "swift" else zip_tool(tool, setup)
    if setup:
        print(f"Pinned {tool} formatter ready.")
        return 0
    if tool == "swift":
        command = [
            str(binary),
            "stdin",
            "--stdin-path",
            args.path,
            "--quiet",
            "--cache",
            "ignore",
        ]
        if (ROOT / ".swiftformat").is_file():
            command += ["--config", str(ROOT / ".swiftformat")]
    elif tool == "terraform":
        command = [str(binary), "fmt", "-no-color", "-"]
    else:
        # Preserve C virtual paths; Metal shader syntax uses the C++ parser.
        # Both retain the source directory for native style configuration lookup.
        command = [
            str(binary),
            "--assume-filename",
            args.path if tool == "c" else str(Path(args.path).with_suffix(".cpp")),
            "--style=file",
            "--fallback-style=LLVM",
        ]
    environment = {
        k: v for k, v in os.environ.items() if not k.startswith("TF_CLI_ARGS")
    }
    environment.update(CHECKPOINT_DISABLE="1", TF_IN_AUTOMATION="1")
    return subprocess.call(command, cwd=ROOT, env=environment)


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (OSError, RuntimeError, ValueError, KeyError, zipfile.BadZipFile) as error:
        print("Native formatter blocked: " + str(error), file=sys.stderr)
        raise SystemExit(1)
