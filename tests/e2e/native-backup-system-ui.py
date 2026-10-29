"""Native backup helpers with explicit entry-point dependencies."""

import re
import subprocess
import time
import xml.etree.ElementTree as ET


def timeline_is_open(h, hierarchy):
    nodes = list(ET.fromstring(hierarchy).iter("node"))
    if any(
        node.get("package") == "android"
        and node.get("resource-id") == "android:id/alertTitle"
        and (node.get("text") or "").endswith("isn't responding")
        for node in nodes
    ):
        return False
    texts = {node.get("text") for node in nodes}
    return {
        "Search your log",
        "Timeline",
        "Text retained through native backup",
    }.issubset(texts)


def system_ui_unresponsive_dialog_present(h, hierarchy):
    """Detect known platform ANRs even when their Wait control is absent."""
    try:
        nodes = ET.fromstring(hierarchy).iter("node")
    except ET.ParseError:
        return False
    return any(
        node.get("package") == "android"
        and node.get("resource-id") == "android:id/alertTitle"
        and node.get("text")
        in {
            "System UI isn't responding",
            "Process system isn't responding",
            "Digital Wellbeing isn't responding",
        }
        for node in nodes
    )


def system_ui_unresponsive_wait_target(h, hierarchy):
    """Return Wait only for known platform ANRs; never dismiss a LogDate failure."""
    try:
        nodes = list(ET.fromstring(hierarchy).iter("node"))
    except ET.ParseError:
        return None
    has_system_anr = any(
        node.get("package") == "android"
        and node.get("resource-id") == "android:id/alertTitle"
        and node.get("text")
        in {
            "System UI isn't responding",
            "Process system isn't responding",
            "Digital Wellbeing isn't responding",
        }
        for node in nodes
    )
    if not has_system_anr:
        return None
    for node in nodes:
        if (
            node.get("package") != "android"
            or node.get("resource-id") != "android:id/aerr_wait"
        ):
            continue
        bounds = re.fullmatch(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", node.get("bounds", ""))
        if bounds is None:
            return None
        left, top, right, bottom = map(int, bounds.groups())
        return ((left + right) // 2, (top + bottom) // 2)
    return None


def bluetooth_crash_dialog_present(h, hierarchy):
    """Detect Android's unrelated Bluetooth crash dialog even without a close control."""
    try:
        nodes = ET.fromstring(hierarchy).iter("node")
    except ET.ParseError:
        return False
    return any(
        node.get("package") == "android"
        and node.get("resource-id") == "android:id/alertTitle"
        and node.get("text") == "Bluetooth keeps stopping"
        for node in nodes
    )


def bluetooth_crash_dialog_close_target(h, hierarchy):
    """Return the close-button center only for Android's unrelated Bluetooth crash dialog."""
    try:
        nodes = list(ET.fromstring(hierarchy).iter("node"))
    except ET.ParseError:
        return None
    has_bluetooth_crash = any(
        node.get("package") == "android"
        and node.get("resource-id") == "android:id/alertTitle"
        and node.get("text") == "Bluetooth keeps stopping"
        for node in nodes
    )
    if not has_bluetooth_crash:
        return None
    for node in nodes:
        if (
            node.get("package") != "android"
            or node.get("resource-id") != "android:id/aerr_close"
        ):
            continue
        bounds = re.fullmatch(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", node.get("bounds", ""))
        if bounds is None:
            return None
        left, top, right, bottom = map(int, bounds.groups())
        return ((left + right) // 2, (top + bottom) // 2)
    return None


def wake_emulator(h, serial):
    """Make the replacement app visible before asserting its post-restore destination."""
    h.adb(serial, "shell", "input", "keyevent", "KEYCODE_WAKEUP")
    h.adb(serial, "shell", "wm", "dismiss-keyguard")


def wait_for_timeline(h, serial, output, timeout_seconds=60, prefix="destination"):
    h.wake_emulator(serial)
    deadline = time.monotonic() + timeout_seconds
    ui_path = output / f"{prefix}-ui.xml"
    screenshot_path = output / f"{prefix}-journal.png"
    while True:
        try:
            dumped = h.adb(
                serial,
                "shell",
                "uiautomator",
                "dump",
                "/data/local/tmp/logdate-native-acceptance-ui.xml",
            )
        except subprocess.CalledProcessError as error:
            if error.returncode != 137:
                raise
            print(
                "Android killed the UI inspector; rechecking within the existing Timeline deadline",
                flush=True,
            )
            dumped = b""
        if b"dumped to:" in dumped:
            hierarchy = h.adb(
                serial,
                "exec-out",
                "cat",
                "/data/local/tmp/logdate-native-acceptance-ui.xml",
            )
            screenshot = h.adb(serial, "exec-out", "screencap", "-p")
            h.overwrite_private_file(ui_path, hierarchy)
            h.overwrite_private_file(screenshot_path, screenshot)
            wait_target = h.system_ui_unresponsive_wait_target(hierarchy)
            if wait_target is not None:
                print(
                    "Waiting for an Android platform app to recover over the restored Timeline",
                    flush=True,
                )
                h.adb(
                    serial,
                    "shell",
                    "input",
                    "tap",
                    str(wait_target[0]),
                    str(wait_target[1]),
                )
                time.sleep(0.25)
            elif h.system_ui_unresponsive_dialog_present(hierarchy):
                pass
            elif h.bluetooth_crash_dialog_present(hierarchy):
                close_target = h.bluetooth_crash_dialog_close_target(hierarchy)
                if close_target is not None:
                    print(
                        "Dismissing Android's unrelated Bluetooth crash dialog over the restored Timeline",
                        flush=True,
                    )
                    h.adb(
                        serial,
                        "shell",
                        "input",
                        "tap",
                        str(close_target[0]),
                        str(close_target[1]),
                    )
                    time.sleep(0.25)
            elif h.timeline_is_open(hierarchy):
                return
        remaining = deadline - time.monotonic()
        if remaining <= 0:
            raise RuntimeError(
                "replacement app did not open its Timeline after verifying restored records and media"
            )
        time.sleep(min(1.0, remaining))


FUNCTIONS = {
    "timeline_is_open": timeline_is_open,
    "system_ui_unresponsive_dialog_present": system_ui_unresponsive_dialog_present,
    "system_ui_unresponsive_wait_target": system_ui_unresponsive_wait_target,
    "bluetooth_crash_dialog_present": bluetooth_crash_dialog_present,
    "bluetooth_crash_dialog_close_target": bluetooth_crash_dialog_close_target,
    "wake_emulator": wake_emulator,
    "wait_for_timeline": wait_for_timeline,
}
