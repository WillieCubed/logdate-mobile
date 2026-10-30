"""Cold boot Gradle-owned clones without inheriting shared guest network state."""

import json
import os
from pathlib import Path
import re
import shlex
import subprocess
import sys
import time


def managed_launch_arguments(arguments, expected_avd, nonce):
    if not re.fullmatch(r"[a-f0-9-]{36}", nonce):
        raise ValueError("A private managed generation nonce is required")
    if "-read-only" not in arguments or "-check-snapshot-loadable" in arguments:
        return list(arguments)
    names = [value[1:] for value in arguments if value.startswith("@")]
    if arguments.count("-avd") == 1:
        avd_index = arguments.index("-avd") + 1
        if avd_index >= len(arguments):
            raise ValueError("Managed snapshot arguments are incomplete")
        names.append(arguments[avd_index])
    elif "-avd" in arguments:
        raise ValueError("A snapshot may identify only one AVD")
    if len(names) != 1 or arguments.count("-id") != 1:
        raise ValueError(
            "Only an explicit Gradle managed snapshot may receive a station identity"
        )
    id_index = arguments.index("-id") + 1
    if id_index >= len(arguments):
        raise ValueError("Managed snapshot arguments are incomplete")
    managed_id = arguments[id_index]
    if (
        names[0] != expected_avd
        or not re.fullmatch(
            r"[a-f0-9]{8}(?:-[a-f0-9]{4}){3}-[a-f0-9]{12}_GradleManagedDevice",
            managed_id,
        )
        or arguments.count("-read-only") != 1
        or arguments.count("-no-snapshot-save") != 1
        or any(value.startswith("-wifi-mac-address") for value in arguments)
    ):
        raise ValueError(
            "The snapshot launch does not match its owned managed generation"
        )
    selected = [
        value
        for value in arguments
        if value not in {"-force-snapshot-load", "-no-snapshot-load"}
    ]
    return [*selected, "-no-snapshot-load"]


def create_managed_sdk(sdk, destination, expected_avd, nonce, *, purpose="drivers"):
    if purpose not in {"preparation", "drivers"}:
        raise ValueError("An immutable managed invocation purpose is required")
    sdk = sdk.resolve(strict=True)
    destination.mkdir(mode=0o700)
    emulator = destination / "emulator"
    emulator.mkdir(mode=0o700)
    for item in sdk.iterdir():
        if item.name != "emulator":
            (destination / item.name).symlink_to(
                item, target_is_directory=item.is_dir()
            )
    for item in (sdk / "emulator").iterdir():
        if item.name != "emulator":
            (emulator / item.name).symlink_to(item, target_is_directory=item.is_dir())
    android_tmp = destination.with_name(destination.name + "-android-tmp")
    android_tmp.mkdir(mode=0o700)
    config = destination / "managed-launch.json"
    with config.open("x") as output:
        json.dump(
            {
                "original": str(sdk / "emulator" / "emulator"),
                "avd": expected_avd,
                "nonce": nonce,
                "purpose": purpose,
                "receipts": str(destination.parent / "managed-emulator-launches"),
                "androidTmp": str(android_tmp.resolve(strict=True)),
            },
            output,
        )
    config.chmod(0o600)
    shim = emulator / "emulator"
    shim.write_text(
        "#!/bin/sh\nexec "
        + shlex.join([sys.executable, str(Path(__file__).resolve()), str(config), "--"])
        + ' "$@"\n'
    )
    shim.chmod(0o700)
    return destination


def owned_managed_candidates(directory, candidates, nonce):
    selected = {}
    for pid, candidate in candidates.items():
        path = directory / f"{candidate['managedId']}.json"
        if path.is_symlink():
            continue
        try:
            receipt = json.loads(path.read_text())
        except (FileNotFoundError, ValueError, UnicodeError):
            # A launcher may still be publishing its receipt. No device commands
            # are allowed until its complete ownership record can be verified.
            continue
        if (
            isinstance(receipt, dict)
            and receipt.get("nonce") == nonce
            and receipt.get("managedId") == candidate["managedId"]
            and receipt.get("avd") == candidate["avd"]
            and (
                receipt.get("coldBoot") is True
                or receipt.get("purpose") == "preparation"
            )
            and type(receipt.get("launcherPid")) is int
            and receipt["launcherPid"] == pid
        ):
            selected[pid] = candidate
    return selected


def require_managed_station_launches(directory, targets, nonce, stations):
    for target in targets.values():
        receipt = json.loads((directory / f"{target['managedId']}.json").read_text())
        if (
            receipt.get("nonce") != nonce
            or receipt.get("managedId") != target["managedId"]
            or receipt.get("avd") != target["avd"]
            or receipt.get("coldBoot") is not True
        ):
            raise RuntimeError(
                "The live managed instance has no matching cold-boot launch receipt"
            )

    def valid(address):
        return (
            isinstance(address, str)
            and re.fullmatch(r"(?:[0-9a-f]{2}:){5}[0-9a-f]{2}", address)
            and not int(address[:2], 16) & 1
            and address != "00:00:00:00:00:00"
        )

    if (
        len(targets) != 2
        or set(targets) != set(stations)
        or not all(valid(address) for address in stations.values())
        or len(set(stations.values())) != 2
    ):
        raise RuntimeError(
            "Managed cold boots must have two distinct real Wi-Fi stations"
        )
    return {
        "stations": stations,
        "managedColdBoot": True,
        "distinctManagedWifiStations": True,
    }


def managed_processes_stopped(gradle, invocations, observed_pids, process_lines, sdk):
    if gradle.poll() is None or process_lines is None:
        return False
    pids = set(observed_pids) | {
        invocation["launcherPid"] for invocation in invocations
    }
    managed_ids = set()
    for invocation in invocations:
        arguments = invocation["arguments"]
        if arguments.count("-id") == 1 and arguments.index("-id") + 1 < len(arguments):
            managed_ids.add(arguments[arguments.index("-id") + 1])
    for line in process_lines:
        fields = line.strip().split(None, 1)
        if not fields or not fields[0].isdigit():
            continue
        if (
            int(fields[0]) in pids
            or str(sdk) in line
            or any(identifier in line for identifier in managed_ids)
        ):
            return False
    return True


def main(config_path, arguments):
    config = json.loads(Path(config_path).read_text())
    purpose = config.get("purpose")
    if purpose not in {"preparation", "drivers"}:
        raise ValueError("The managed invocation has no valid immutable purpose")
    android_tmp = Path(config["androidTmp"])
    if (
        not android_tmp.is_absolute()
        or android_tmp.is_symlink()
        or not android_tmp.is_dir()
    ):
        raise ValueError(
            "The managed emulator requires its private temporary directory"
        )
    android_tmp = android_tmp.resolve(strict=True)
    environment = os.environ.copy()
    environment["ANDROID_TMP"] = str(android_tmp)
    invocations = Path(config["receipts"]).parent / "managed-emulator-invocations"
    invocations.mkdir(mode=0o700, exist_ok=True)
    birth = " ".join(
        subprocess.check_output(
            ["ps", "-p", str(os.getpid()), "-o", "lstart="], text=True, timeout=5
        ).split()
    )
    if not birth:
        raise ValueError("The emulator launch requires a process birth identity")
    with open(
        invocations / f"{os.getpid()}-{time.monotonic_ns()}.json",
        "x",
        opener=lambda name, flags: os.open(name, flags, 0o600),
    ) as receipt:
        json.dump(
            {
                "nonce": config["nonce"],
                "arguments": arguments,
                "launcherPid": os.getpid(),
                "androidTmp": str(android_tmp),
                "purpose": purpose,
                "birth": birth,
            },
            receipt,
        )
    validated = managed_launch_arguments(arguments, config["avd"], config["nonce"])
    selected = validated if purpose == "drivers" else arguments
    if (
        "-read-only" in arguments
        and "-id" in arguments
        and "-check-snapshot-loadable" not in arguments
    ):
        managed_id = arguments[arguments.index("-id") + 1]
        directory = Path(config["receipts"])
        directory.mkdir(mode=0o700, exist_ok=True)
        with open(
            directory / f"{managed_id}.json",
            "x",
            opener=lambda name, flags: os.open(name, flags, 0o600),
        ) as receipt:
            json.dump(
                {
                    "nonce": config["nonce"],
                    "managedId": managed_id,
                    "avd": config["avd"],
                    "launcherPid": os.getpid(),
                    "coldBoot": purpose == "drivers",
                    "purpose": purpose,
                    "birth": birth,
                    "androidTmp": str(android_tmp),
                },
                receipt,
            )
    os.execve(config["original"], [config["original"], *selected], environment)


if __name__ == "__main__":
    if len(sys.argv) < 3 or sys.argv[2] != "--":
        raise ValueError("The Gradle SDK facade must provide its configuration")
    main(sys.argv[1], sys.argv[3:])
