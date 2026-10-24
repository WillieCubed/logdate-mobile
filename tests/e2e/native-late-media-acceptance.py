#!/usr/bin/env python3
"""Exercise a retained media callback and app restart on one Gradle-owned emulator."""
import argparse
import json
from pathlib import Path
import runpy
import shlex
import subprocess

NATIVE = runpy.run_path(str(Path(__file__).with_name("native-backup-acceptance.py")))
TEST = "app.logdate.client.e2e.NativeLateMediaRecoveryTest"
PHASES = (
    ("seed", "seedCompletedTransferAndDeliverOnlyMissingOriginal"),
    ("verify", "restartRepairsRealRepositoryAndAcknowledgesCustodyIdempotently"),
)


def run(destination, app_apk, test_apk, output):
    if NATIVE["adb"](destination, "shell", "getprop", "ro.kernel.qemu").strip() != b"1":
        raise RuntimeError("Late-media acceptance requires a Gradle-owned emulator")
    output.mkdir(mode=0o700, parents=True, exist_ok=False)
    for apk in (app_apk, test_apk):
        NATIVE["adb"](destination, "install", "-r", "-t", str(apk))
    for stage, method in PHASES:
        selector = TEST + "#" + method
        shell_command = shlex.join([
            "am", "instrument", "-w", "-r", "-e", "class", selector,
            "-e", "nativeLateMediaStage", stage, NATIVE["RUNNER"],
        ])
        result = subprocess.run(
            NATIVE["managed_adb_command"](destination, ["shell", shell_command]),
            capture_output=True, timeout=180,
        )
        NATIVE["private_file"](output / f"{stage}.log", result.stdout + b"\n" + result.stderr)
        if result.returncode != 0 or not NATIVE["instrumentation_passed"](result.stdout, 1):
            raise RuntimeError(f"Late-media {stage} must pass exactly one case without skips; inspect {output / (stage + '.log')}")
        if stage == "seed":
            NATIVE["adb"](destination, "shell", "am", "force-stop", NATIVE["PACKAGE"])
    NATIVE["private_file"](output / "acceptance.json", json.dumps({
        "managedEmulator": True, "separateProcess": True, "requiredPasses": 2,
        "originalDecodedFromLocalMedia": True, "reconciliationIdempotent": True,
    }, indent=2).encode())


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source", required=True, type=NATIVE["emulator_serial"])
    parser.add_argument("--destination", required=True, type=NATIVE["emulator_serial"])
    parser.add_argument("--app-apk", required=True, type=Path)
    parser.add_argument("--test-apk", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--seed-empty-source", action="store_true", required=True)
    args = parser.parse_args()
    if args.source == args.destination:
        raise SystemExit("Two separate managed roles are required")
    run(args.destination, args.app_apk, args.test_apk, args.output)
