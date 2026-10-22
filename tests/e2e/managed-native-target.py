"""Bind native restore commands to the current Gradle Managed Device lifecycle."""

from pathlib import PurePosixPath
from pathlib import Path
import json
import os
import re
import shlex
import subprocess
import sys
import time

DRIVER = "app.logdate.tools.restoredriver"


def managed_adb_command(serial, arguments):
    if not re.fullmatch(r"emulator-[0-9]+", serial):
        raise ValueError("An explicit emulator-NNNN serial is required")
    if not os.environ.get("LOGDATE_MANAGED_CUSTODY"):
        raise RuntimeError("Run native scenarios through managed-native-restore-acceptance.py to establish device ownership")
    return [sys.executable, str(Path(__file__).resolve()), "-s", serial, *map(str, arguments)]


def managed_device_capacity(process_lines):
    existing = set()
    for line in process_lines:
        fields = line.strip().split(None, 1)
        if len(fields) != 2 or not fields[0].isdigit():
            continue
        try:
            arguments = shlex.split(fields[1])
        except ValueError:
            continue
        if not arguments or not PurePosixPath(arguments[0]).name.startswith("qemu-system-") or "-id" not in arguments:
            continue
        position = arguments.index("-id") + 1
        if position < len(arguments) and re.fullmatch(r"[\w-]+_GradleManagedDevice", arguments[position]):
            existing.add(int(fields[0]))
    if len(existing) > 2:
        raise RuntimeError("The shared host has insufficient bounded capacity for a managed restore pair")
    # The shared limit includes reservations made before another job starts QEMU.
    # Keep bounded headroom for those jobs; this run still requests exactly two.
    return 4


def wifi_station_address(raw):
    match = re.search(rb"link/ether ([0-9a-fA-F:]{17})(?:\s|$)", raw)
    if match is None:
        return None
    address = match[1].decode().lower()
    if not re.fullmatch(r"(?:[0-9a-f]{2}:){5}[0-9a-f]{2}", address) or int(address[:2], 16) & 1 or address == "00:00:00:00:00:00":
        return None
    return address


def process_identity(pid):
    result = subprocess.run(["ps", "-p", str(pid), "-o", "lstart=", "-o", "command="],
                            check=True, capture_output=True, text=True, timeout=10)
    fields = result.stdout.strip().split(None, 5)
    if len(fields) != 6:
        raise RuntimeError("The owned process is no longer live")
    return {"birth": " ".join(fields[:5]), "command": fields[5]}


def real_command(adb, serial, *arguments):
    return subprocess.run([adb, "-s", serial, *arguments], check=True,
                          stdin=subprocess.DEVNULL, capture_output=True, timeout=15).stdout


def require_binding(custody, serial):
    """Check process birth identities and the nonce-bearing waiting driver before device use."""
    gradle = custody["gradle"]
    if process_identity(gradle["pid"]) != gradle["identity"]:
        raise RuntimeError("The owned Gradle process changed")
    if custody["nonce"] not in gradle["identity"]["command"]:
        raise RuntimeError("Gradle does not own this acceptance generation")
    binding = custody["targets"][serial]
    if process_identity(binding["pid"]) != binding["identity"]:
        raise RuntimeError("The owned managed QEMU process changed")
    candidates = managed_candidates([f'{binding["pid"]} {binding["identity"]["command"]}'], set(), binding["avd"])
    if candidates.get(binding["pid"], {}).get("managedId") != binding["managedId"]:
        raise RuntimeError("The target is not the owned managed emulator")
    adb, nonce = custody["adb"], custody["nonce"]
    pids = real_command(adb, serial, "shell", "pidof", DRIVER).decode().strip().split()
    if pids != [str(binding["driverPid"])]:
        raise RuntimeError("The managed waiting driver changed")
    receipt = json.loads(real_command(adb, serial, "exec-out", "run-as", DRIVER, "cat", f"no_backup/managed-restore-{nonce}.json"))
    if not validate_driver_receipt(receipt, nonce, binding["driverPid"]) or receipt.get("startTicks") != binding["driverStartTicks"]:
        raise RuntimeError("The managed receipt is stale or no longer ready")
    stat = real_command(adb, serial, "exec-out", "run-as", DRIVER, "cat", f'/proc/{binding["driverPid"]}/stat').decode()
    if stat.rsplit(") ", 1)[1].split()[19] != binding["driverStartTicks"]:
        raise RuntimeError("The waiting driver PID was reused")
    return binding


def managed_candidates(lines, existing, expected_avd):
    matches = {}
    for line in lines:
        fields = line.strip().split(None, 1)
        if len(fields) != 2 or not fields[0].isdigit() or int(fields[0]) in existing:
            continue
        try:
            arguments = shlex.split(fields[1])
        except ValueError:
            continue
        if not arguments or not PurePosixPath(arguments[0]).name.startswith("qemu-system-"):
            continue
        if f"@{expected_avd}" not in arguments or "-id" not in arguments:
            continue
        position = arguments.index("-id") + 1
        if position >= len(arguments) or not re.fullmatch(r"[\w-]+_GradleManagedDevice", arguments[position]):
            continue
        matches[int(fields[0])] = {"managedId": arguments[position], "avd": expected_avd}
    return matches


def validate_driver_receipt(receipt, nonce, current_pid):
    return (isinstance(receipt, dict) and receipt.get("nonce") == nonce
            and receipt.get("phase") == "ready" and type(receipt.get("pid")) is int
            and receipt["pid"] > 0 and receipt["pid"] == current_pid)


def device_command(real_adb, serial, arguments):
    if not re.fullmatch(r"emulator-\d+", serial) or not arguments:
        raise ValueError("An explicit managed-emulator serial and command are required")
    operation, *tail = arguments
    prefix = [real_adb, "-s", serial]
    if operation == "root" and not tail:
        # Verify actual root access without restarting the connection owned by Gradle.
        return [*prefix, "shell", "su 0 id"]
    if operation == "emu" and tail == ["avd", "name"]:
        return [*prefix, operation, *tail]
    if operation not in {"shell", "exec-out", "install", "push", "pull", "logcat", "wait-for-device"}:
        raise ValueError("The managed lifecycle does not permit this operation")
    if operation in {"shell", "exec-out"} and tail:
        script = tail[0] if len(tail) == 1 else shlex.join(tail)
        tokens = shlex.split(script)
        if "uninstall" in tokens or ("clear" in tokens and any(p in tokens for p in ("pm", "package"))):
            raise ValueError("Application data must not be cleared or uninstalled")
        if "rm" in tokens and any("/data/" in p for p in tokens):
            raise ValueError("Application data deletion is forbidden")
        # Probes, run-as, settings and BackupManager retain the shell caller. Only
        # protected payload reads/extraction, the root capability check and Android's
        # root-only reconnect of the owned open emulator network use su.
        protected = tokens and (tokens[0] in {"tar", "chown", "restorecon"}
                                or tokens[:2] == ["id", "-u"]
                                or tokens in (["cmd", "wifi", "connect-network", "AndroidWifi", "open"],
                                              ["ip", "-o", "link", "show", "wlan0"])
                                or (tokens[0] == "cat" and any(p.startswith("/data/user/") for p in tokens[1:])))
        return [*prefix, operation, shlex.join(["su", "0", "sh", "-c", script]) if protected else script]
    return [*prefix, *arguments]


def proxy_main(arguments):
    if len(arguments) < 3 or arguments[0] != "-s":
        raise ValueError("The native harness must select one owned managed target")
    custody = json.loads(Path(os.environ["LOGDATE_MANAGED_CUSTODY"]).read_text())
    if custody.get("phase") != "active":
        raise RuntimeError("Native commands are closed during terminal completion")
    serial = arguments[1]
    if serial not in custody["targets"]:
        raise ValueError("The serial is outside this managed test generation")
    for target in custody["targets"]:
        require_binding(custody, target)
    command = device_command(custody["adb"], serial, arguments[2:])
    # Keep the controller's process handle attached to ADB for cancellation and streaming timeouts.
    return os.execv(command[0], command)


if __name__ == "__main__":
    sys.exit(proxy_main(sys.argv[1:]))
