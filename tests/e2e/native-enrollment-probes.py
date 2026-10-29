"""Enrollment probes and bounded diagnostics with entry-point dependencies."""

import time


def checked_emulator(h, serial):
    h.H["emulator_serial"](serial)
    if h.H["adb"](serial, "shell", "getprop", "ro.kernel.qemu").strip() != b"1":
        raise RuntimeError("An Android emulator is required")


def bring_fixture_online(h, serial, output=None):
    h.checked_emulator(serial)
    h.H["set_offline"](serial, False)
    try:
        h.H["adb"](
            serial, "shell", "cmd", "wifi", "connect-network", "AndroidWifi", "open"
        )
    except h.subprocess.CalledProcessError as failure:
        if output is not None:
            h.H["private_file"](
                output,
                (failure.stdout or b"")[-2000:] + (failure.stderr or b"")[-2000:],
            )
        raise RuntimeError("The owned emulator Wi-Fi reconnect failed") from failure


def probe(h, serial, stage, output):
    h.checked_emulator(serial)
    raw = h.H["adb"](
        serial,
        "shell",
        "am",
        "instrument",
        "-w",
        "-r",
        "-e",
        "class",
        h.PROBE,
        "-e",
        "nativeDeviceEnrollment",
        "true",
        "-e",
        "nativeDeviceEnrollmentStage",
        stage,
        h.RUNNER,
    )
    h.H["private_file"](output, raw)
    if not h.H["instrumentation_passed"](raw):
        failure = RuntimeError(f"Enrollment {stage} failed; inspect {output}")
        try:
            h.capture_failure_diagnostics(
                serial, output.parent / "failure-diagnostics" / output.stem
            )
        except Exception as diagnostic:
            h.add_failure_note(
                failure,
                "Enrollment diagnostics also failed: " + type(diagnostic).__name__,
            )
        raise failure


def retained_draft_probe(h, serial, stage, output):
    h.checked_emulator(serial)
    raw = h.H["adb"](
        serial,
        "shell",
        "am",
        "instrument",
        "-w",
        "-r",
        "-e",
        "class",
        "app.logdate.client.e2e.NativeRetainedDraftShortcutProbeTest",
        "-e",
        "retainedDraftShortcutStage",
        stage,
        h.RUNNER,
    )
    h.H["private_file"](output, raw)
    failure = None
    if not h.H["instrumentation_passed"](raw):
        failure = RuntimeError(
            f"Current-editor draft {stage} did not pass; inspect {output}"
        )
    if b"retainedDraftScreenshot=saved" in raw:
        try:
            screenshot = h.H["adb"](
                serial,
                "exec-out",
                "run-as",
                h.PACKAGE,
                "cat",
                "no_backup/native-retained-draft-shortcut.png",
            )
            h.H["private_file"](output.with_suffix(".png"), screenshot)
        except Exception as diagnostic:
            if failure is None:
                raise RuntimeError(
                    f"Current-editor draft {stage} screenshot collection failed; inspect {output}"
                ) from diagnostic
            h.add_failure_note(
                failure,
                "Retained-editor screenshot collection also failed: "
                + type(diagnostic).__name__,
            )
    if failure is not None:
        raise failure


def current_draft_probe(h, serial, stage, output):
    h.checked_emulator(serial)
    raw = h.H["adb"](
        serial,
        "shell",
        "am",
        "instrument",
        "-w",
        "-r",
        "-e",
        "class",
        "app.logdate.client.e2e.NativeCurrentDraftRecoveryProbeTest",
        "-e",
        "nativeCurrentDraftStage",
        stage,
        h.RUNNER,
    )
    h.H["private_file"](output, raw)
    if not h.H["instrumentation_passed"](raw):
        raise RuntimeError(
            f"Current-editor recovery {stage} did not pass; inspect {output}"
        )


def private_fixture(h, serial, fixture):
    h.checked_emulator(serial)
    h.H["adb"](serial, "shell", "run-as", h.PACKAGE, "mkdir", "-p", "no_backup")
    command = h.shlex.join(
        ["run-as", h.PACKAGE, "sh", "-c", f"umask 077; cat > no_backup/{h.FIXTURE}"]
    )
    payload = h.json.dumps(fixture).encode()
    h.H["adb"](serial, "shell", command, input_bytes=payload)
    actual = h.H["adb"](
        serial, "exec-out", "run-as", h.PACKAGE, "cat", "no_backup/" + h.FIXTURE
    )
    if actual != payload:
        raise RuntimeError(
            "Private enrollment fixture did not round-trip through the owned transport"
        )


def receipt(h, serial):
    h.checked_emulator(serial)
    raw = h.H["adb"](
        serial, "exec-out", "run-as", h.PACKAGE, "cat", "no_backup/" + h.RECEIPT
    )
    return h.json.loads(raw)


def capture_failure_diagnostics(h, serial, output, *, clock=time.monotonic):
    """Read bounded private evidence through the existing owned-emulator route."""
    deadline = clock() + 15
    h.H["emulator_serial"](serial)
    if (
        h.H["adb"](serial, "shell", "getprop", "ro.kernel.qemu", timeout=3).strip()
        != b"1"
    ):
        raise RuntimeError("Failure diagnostics require an owned emulator")
    output.mkdir(mode=448, parents=True, exist_ok=True)
    commands = {
        "lastanr": "dumpsys -t 2 activity lastanr",
        "window": "dumpsys -t 2 window",
        "input": "dumpsys -t 2 input",
        "meminfo": "dumpsys -t 2 meminfo",
        "logcat-all": "logcat -b all -d -t 2000 -v threadtime",
    }
    report = {}
    for label, command in commands.items():
        remaining = deadline - clock()
        if remaining <= 0:
            report[label] = "budget exhausted"
            continue
        try:
            raw = h.H["adb"](
                serial, "shell", command + " | head -c 65536", timeout=min(3, remaining)
            )
            h.H["private_file"](output / (label + ".txt"), raw[:65536])
            report[label] = "retained"
        except Exception as error:
            report[label] = type(error).__name__
    h.H["private_file"](output / "capture.json", h.json.dumps(report).encode())


def wait_for(h, path, process, seconds=120):
    deadline = h.time.monotonic() + seconds
    while not path.is_file():
        if process.poll() is not None:
            raise RuntimeError(
                "Owned enrollment server stopped; inspect its private server log"
            )
        if h.time.monotonic() >= deadline:
            raise RuntimeError(f"Enrollment acceptance did not reach {path.name}")
        h.time.sleep(0.1)


FUNCTIONS = {
    "wait_for": wait_for,
    "bring_fixture_online": bring_fixture_online,
    "capture_failure_diagnostics": capture_failure_diagnostics,
    "checked_emulator": checked_emulator,
    "current_draft_probe": current_draft_probe,
    "private_fixture": private_fixture,
    "probe": probe,
    "receipt": receipt,
    "retained_draft_probe": retained_draft_probe,
}
