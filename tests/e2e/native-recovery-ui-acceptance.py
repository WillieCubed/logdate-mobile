#!/usr/bin/env python3
"""Exercise production Android recovery sheets at phone, tablet, and system large-text sizes."""

import argparse
import json
import os
from pathlib import Path
import re
import runpy
import uuid

shared = runpy.run_path(str(Path(__file__).with_name("native-missing-media-acceptance.py")))
adb = shared["adb"]
safe_target = shared["safe_target"]
instrument = shared["instrument"]
PACKAGE = shared["PACKAGE"]


def display_state(serial):
    state = {key: adb(serial, "shell", "wm", key).stdout.decode().strip() for key in ["size", "density"]}
    for key in state:
        if not re.search(r"Physical " + key + r": \S+", state[key]):
            raise RuntimeError("Could not determine original Android display configuration")
    state["font"] = adb(serial, "shell", "settings", "get", "system", "font_scale").stdout.decode().strip()
    if state["font"] != "null" and not re.fullmatch(r"[0-9]+(?:\.[0-9]+)?", state["font"]):
        raise RuntimeError("Could not determine original Android font scale")
    return state


def mutate(serial, *args):
    safe_target(serial)
    return adb(serial, "shell", *args)


def restore_display(serial, original):
    errors = []
    for key in ["density", "size"]:
        override = re.search(r"Override " + key + r": (\S+)", original[key])
        try:
            mutate(serial, "wm", key, override[1] if override else "reset")
        except Exception as error:
            errors.append(key + ": " + type(error).__name__)
    try:
        if original["font"] == "null":
            mutate(serial, "settings", "delete", "system", "font_scale")
        else:
            mutate(serial, "settings", "put", "system", "font_scale", original["font"])
    except Exception as error:
        errors.append("font: " + type(error).__name__)
    if display_state(serial) != original:
        errors.append("Display configuration readback did not match")
    if errors:
        raise RuntimeError("Could not restore emulator display: " + "; ".join(errors))


def run(args):
    serial = args.emulator
    avd = safe_target(serial)
    for apk in [args.app_apk, args.test_apk]:
        safe_target(serial)
        adb(serial, "install", "-r", "-t", apk)
    original = display_state(serial)
    (args.output / "environment-before.json").write_text(json.dumps(original, indent=2) + "\n")
    results = []
    try:
        for name, size, density, font in [
            ("phone", "1080x2400", "420", "1"),
            ("phone-200", "1080x2400", "420", "2"),
            ("tablet", "1920x1200", "240", "1"),
        ]:
            mutate(serial, "am", "force-stop", PACKAGE)
            mutate(serial, "wm", "size", size)
            mutate(serial, "wm", "density", density)
            mutate(serial, "settings", "put", "system", "font_scale", font)
            label = name + "-" + uuid.uuid4().hex
            directory = args.output / label
            directory.mkdir(mode=0o700)
            instrument(
                serial,
                "app.logdate.client.e2e.RestoreStateAccessibilityRuntimeTest#partialRecoveryNeverClaimsCompletionAndActionsRemainReachable",
                directory / "runtime.log",
                extras=["-e", "recoveryUiAcceptance", "true", "-e", "recoveryUiLabel", label,
                        "-e", "recoveryUiFontScale", font],
            )
            safe_target(serial)
            adb(serial, "pull", "/sdcard/Android/data/" + PACKAGE + "/files/recovery-state-acceptance/" + label,
                directory / "frames")
            if len(list((directory / "frames").glob("*.png"))) != 14:
                raise RuntimeError("Every recovery state needs content and action captures")
            results.append({"variant": name, "fontScale": float(font), "label": label,
                            "states": 7, "renderedSheetScaleVerified": True, "actionsPassed": True})
    finally:
        restore_display(serial, original)
    receipt = {"serial": serial, "avd": avd, "displayRestored": True, "variants": results,
               "talkBackNavigationVerified": False}
    (args.output / "acceptance.json").write_text(json.dumps(receipt, indent=2) + "\n")
    print(json.dumps(receipt))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--emulator", required=True)
    parser.add_argument("--app-apk", type=Path, required=True)
    parser.add_argument("--test-apk", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--synthetic-data", action="store_true", required=True)
    args = parser.parse_args()
    os.umask(0o077)
    args.output.mkdir(mode=0o700, parents=True)
    run(args)


if __name__ == "__main__":
    main()
