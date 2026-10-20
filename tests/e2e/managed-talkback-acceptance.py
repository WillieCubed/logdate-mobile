#!/usr/bin/env python3
"""Exercise recovery sheets with TalkBack and native touch input on a current-task GMD."""
import argparse
import json
import os
from pathlib import Path
import re
import shlex
import subprocess
import time
import uuid
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
PACKAGE = "studio.hypertext.logdate.debug"
CLASS = "app.logdate.client.e2e.RestoreTalkBackRuntimeTest"


class NativeTouch:
    def __init__(self, adb, serial, width, height, nonce, binary):
        self.adb, self.serial, self.width, self.height = adb, serial, width, height
        self.nonce = nonce
        self.receipt = f"no_backup/talkback-acceptance-{nonce}.json"
        self.remote = f"/data/local/tmp/logdate-talkback-touch-{nonce}"
        self.require_ready()
        if b"uid=0(root)" not in command(adb, "-s", serial, "shell", "su 0 id").stdout:
            raise RuntimeError("The managed image must support native input through su")
        devices = command(adb, "-s", serial, "shell", "getevent -lp").stdout.decode()
        matches = re.findall(r'add device \d+: (/dev/input/event\d+)\n  name:     "virtio_input_multi_touch_1"', devices)
        if len(matches) != 1:
            raise RuntimeError("Expected one primary native multitouch input device")
        self.device = matches[0]
        command(adb, "-s", serial, "push", str(binary), self.remote)
        command(adb, "-s", serial, "shell", f"chmod 700 {self.remote}")

    def require_ready(self):
        state = json.loads(command(self.adb, "-s", self.serial, "exec-out", "run-as", PACKAGE, "cat", self.receipt).stdout)
        require_ready_receipt(state, self.nonce)

    def send(self, mode, x1, x2, y):
        self.require_ready()
        args = [self.remote, self.device, mode, str(x1), str(x2), str(y)]
        command(self.adb, "-s", self.serial, "shell", "su 0 " + shlex.join(args))

    def swipe(self, forward=True):
        start, end = (.3, .75) if forward else (.75, .3)
        self.send("swipe", round(start * 32767), round(end * 32767), 16384)

    def double_tap(self):
        self.send("doubletap", 16384, 16384, 16384)

    def close(self):
        try:
            self.send("release", 0, 0, 0)
        except (RuntimeError, ValueError, subprocess.SubprocessError):
            pass


def command(*args, check=True):
    return subprocess.run(args, cwd=ROOT, check=check, capture_output=True, timeout=10)


def require_ready_receipt(state, nonce):
    if state.get("nonce") != nonce or state.get("phase") != "ready":
        raise RuntimeError("The current managed fixture no longer accepts native input")


def managed_processes(lines, existing):
    matches = [line.strip().split(None, 1) for line in lines if "qemu-system" in line
               and "@dev34_google_apis_arm64-v8a_Pixel_9_Pro " in line
               and re.search(r"-id [\w-]+_GradleManagedDevice(?:\s|$)", line)]
    matches = [(int(pid), args) for pid, args in matches if int(pid) not in existing]
    if len(matches) > 1:
        raise RuntimeError("Multiple new managed devices; target is ambiguous")
    return matches


def console_ports(listeners):
    ports = {int(port) for port in re.findall(r"^n(?:127\.0\.0\.1|\[::1\]):(\d+)$", listeners, re.M)
             if 5554 <= int(port) <= 5682 and int(port) % 2 == 0}
    if len(ports) > 1:
        raise RuntimeError("Managed-device console port is ambiguous")
    return ports


def run(font, output, sdk):
    output.mkdir()
    nonce = str(uuid.uuid4())
    adb = str(sdk / "platform-tools/adb")
    receipt_path = f"no_backup/talkback-acceptance-{nonce}.json"
    env = dict(os.environ, ANDROID_HOME=str(sdk), ANDROID_SDK_ROOT=str(sdk),
               GRADLE_USER_HOME=str(Path.home() / ".gradle"), JAVA_TOOL_OPTIONS="-Djava.awt.headless=true")
    compilers = sorted(sdk.glob("ndk/*/toolchains/llvm/prebuilt/*/bin/aarch64-linux-android34-clang"))
    if not compilers:
        raise RuntimeError("Install an Android NDK with the API 34 ARM64 compiler for this managed image")
    binary = output / "native-touch"
    command(str(compilers[0]), "-O2", "-Wall", "-Werror", str(ROOT / "tests/e2e/native-talkback-touch.c"), "-o", str(binary))
    args = ["./gradlew", ":app:android-main:phoneApi34DebugAndroidTest",
            f"-Plogdate.androidTestClass={CLASS}",
            "-Pandroid.testInstrumentationRunnerArguments.restoreTalkBackAcceptance=true",
            f"-Pandroid.testInstrumentationRunnerArguments.restoreTalkBackNonce={nonce}",
            f"-Pandroid.testInstrumentationRunnerArguments.restoreTalkBackFontScale={font}", "--console=plain"]
    touch = None
    serial = None
    seen = []
    existing_pids = {int(line.split(None, 1)[0]) for line in command("ps", "-axo", "pid,args").stdout.decode().splitlines()
                     if "qemu-system" in line and line.strip().split(None, 1)[0].isdigit()}
    with (output / "gradle.log").open("wb") as log:
        process = subprocess.Popen(args, cwd=ROOT, env=env, stdout=log, stderr=subprocess.STDOUT)
        deadline = time.monotonic() + 480
        try:
            while process.poll() is None:
                if time.monotonic() > deadline:
                    raise RuntimeError("TalkBack managed-device acceptance exceeded eight minutes")
                processes = command("ps", "-axo", "pid,args").stdout.decode().splitlines()
                managed = managed_processes(processes, existing_pids)
                candidates = []
                if managed:
                    listeners = command("lsof", "-nP", "-a", "-p", str(managed[0][0]), "-iTCP", "-sTCP:LISTEN", "-Fn", check=False).stdout.decode()
                    ports = console_ports(listeners)
                    candidates = [f"emulator-{port}" for port in ports]
                for candidate in candidates:
                    raw = command(adb, "-s", candidate, "exec-out", "run-as", PACKAGE, "cat", receipt_path, check=False)
                    try:
                        state = json.loads(raw.stdout)
                    except (ValueError, UnicodeError):
                        continue
                    if state.get("nonce") != nonce:
                        continue
                    (output / "last-state.json").write_text(json.dumps(state, indent=2))
                    if state.get("phase") != "ready":
                        continue
                    if serial is None:
                        avd = command(adb, "-s", candidate, "emu", "avd", "name").stdout.decode().splitlines()[0]
                        processes = command("ps", "-axo", "args").stdout.decode().splitlines()
                        gmd = [line for line in processes if "qemu-system" in line and f"@{avd} " in line
                               and re.search(r"-id [\w-]+_GradleManagedDevice(?:\s|$)", line)]
                        if len(gmd) != 1 or avd != "dev34_google_apis_arm64-v8a_Pixel_9_Pro":
                            raise RuntimeError("Nonce-bearing target is not the expected current-task managed device")
                        if command(adb, "-s", candidate, "shell", "getprop", "ro.kernel.qemu").stdout.strip() != b"1":
                            raise RuntimeError("Expected emulator")
                        serial = candidate
                        dimensions = command(adb, "-s", serial, "shell", "wm", "size").stdout.decode()
                        width, height = map(int, re.findall(r"(\d+)x(\d+)", dimensions)[-1])
                        touch = NativeTouch(adb, serial, width, height, nonce, binary)
                        dump = command(adb, "-s", serial, "shell", "dumpsys", "accessibility").stdout
                        (output / "accessibility.txt").write_bytes(dump)
                        if b"touchExplorationEnabled=true" not in dump or b"TalkBackService" not in dump:
                            raise RuntimeError("TalkBack touch exploration is not bound")
                    if candidate != serial:
                        raise RuntimeError("Acceptance nonce appeared on another device")
                    scene, action = state["scene"], state["action"]
                    if scene in seen:
                        continue
                    # Native swipe navigation, then backward/forward verification, then activation.
                    for _ in range(35):
                        if state.get("focus", []) and state["focus"][-1] == action:
                            break
                        touch.swipe()
                        time.sleep(.45)
                        state = json.loads(command(adb, "-s", serial, "exec-out", "run-as", PACKAGE, "cat", receipt_path).stdout)
                    else:
                        raise RuntimeError(f"TalkBack did not reach {action}: {state.get('focus')}")
                    touch.swipe(False)
                    time.sleep(.6)
                    touch.swipe()
                    time.sleep(.6)
                    state = json.loads(command(adb, "-s", serial, "exec-out", "run-as", PACKAGE, "cat", receipt_path).stdout)
                    if not state.get("focus") or state["focus"][-1] != action:
                        raise RuntimeError(f"TalkBack did not return to {action}")
                    (output / f"{scene}-focus.json").write_text(json.dumps(state, indent=2))
                    (output / f"{scene}.png").write_bytes(command(adb, "-s", serial, "exec-out", "screencap", "-p").stdout)
                    touch.double_tap()
                    seen.append(scene)
                time.sleep(.2)
            if process.returncode != 0:
                raise RuntimeError(f"Managed-device task failed; see {output / 'gradle.log'}")
            result = ROOT / "app/android-main/build/outputs/androidTest-results/managedDevice/debug/phoneApi34/TEST-phoneApi34-_app_android-main-.xml"
            (output / "result.xml").write_bytes(result.read_bytes())
            cases = list(ET.parse(result).getroot().iter("testcase"))
            if len(cases) != 1 or cases[0].get("classname") != CLASS or any(cases[0].find(tag) is not None for tag in ("failure", "error", "skipped")):
                raise RuntimeError("Expected one passing TalkBack case without skips")
            if seen != ["partial", "paused", "preview", "missing-key", "late-key", "app-lock", "app-lock-resumed"]:
                raise RuntimeError(f"Incomplete gesture sequence: {seen}")
            (output / "acceptance.json").write_text(json.dumps({"fontScale": font, "scenes": seen,
                "nativeTouchInput": True, "talkBackFocusForwardBackwardAndActivation": True,
                "settingsRestoredByInstrumentation": True, "realImportExecuted": False,
                "actualStartupKeyRecovery": True, "syntheticLateKeyDelivery": True, "lockedJournalSemanticsHidden": True}, indent=2) + "\n")
        finally:
            if touch:
                touch.close()
            # Preserve the Gradle process and its cleanup if observation fails; never relaunch it.
            if process.poll() is None:
                (output / "active-process.json").write_text(json.dumps({"pid": process.pid, "nonce": nonce,
                    "serial": serial, "receipt": receipt_path, "log": str(output / "gradle.log")}))
                try:
                    process.wait(timeout=60)
                except subprocess.TimeoutExpired:
                    pass


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--font-scale", choices=("1.0", "2.0"), default="1.0")
    parser.add_argument("--sdk", type=Path, default=Path.home() / "Library/Android/sdk")
    args = parser.parse_args()
    args.output = args.output.resolve()
    if args.output.exists():
        raise SystemExit("Use a fresh evidence directory")
    run(args.font_scale, args.output, args.sdk)
