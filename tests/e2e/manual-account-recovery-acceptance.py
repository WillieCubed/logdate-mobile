#!/usr/bin/env python3
"""Seed source content and exercise manual recovery on two already-owned managed guests."""

import argparse
import json
import os
from pathlib import Path
import runpy
import shlex
import shutil
import subprocess
import time

H = runpy.run_path(str(Path(__file__).with_name("native-backup-acceptance.py")))
add_failure_note = H["add_failure_note"]
MEDIA = runpy.run_path(str(Path(__file__).with_name("native-missing-media-acceptance.py")))
SELECTOR = "app.logdate.client.e2e.ManualAccountRecoveryE2ETest#ordinarySignInThenSettingsPhraseRecoversOriginalContent"
SOURCE_SELECTOR = "app.logdate.client.e2e.RecoveryAcceptanceProbeTest"
BACKGROUND_CALLBACK_CLASS = "app.logdate.client.e2e.NativeBackgroundCredentialCallbackTest"
BACKGROUND_CALLBACK_CASES = json.loads(
    Path(__file__).with_name("native-modern-callback-cases.json").read_text()
)[BACKGROUND_CALLBACK_CLASS]
ROOT = Path(__file__).resolve().parents[2]


def install_passkey_provider(destination, output, provider, sdk, *, execute=subprocess.run, adb=H["adb"]):
    if not provider.is_file():
        raise RuntimeError("The emulator-only passkey provider APK was not built")
    output.mkdir(mode=0o700, parents=True, exist_ok=True)
    pinned = output / "passkey-provider.apk"
    with pinned.open("xb") as destination_file, provider.open("rb") as source_file:
        shutil.copyfileobj(source_file, destination_file)
    pinned.chmod(0o400)
    versions = sorted((sdk / "build-tools").glob("*/aapt2"), reverse=True)
    if not versions:
        raise RuntimeError("Android SDK has no aapt2 for the passkey provider identity check")
    result = execute([str(versions[0]), "dump", "packagename", str(pinned)], capture_output=True, check=True)
    if result.stdout.strip() != b"app.logdate.tools.passkeyprovider":
        raise RuntimeError("The pinned passkey provider APK has an unexpected package identity")
    adb(destination, "install", "-r", "-t", str(pinned))
    return pinned


def publish_source_fixture(source, destination, output):
    raw = H["adb"](source, "exec-out", "run-as", H["PACKAGE"], "cat", "no_backup/manual-account-recovery-source-proof.json")
    if not 1 <= len(raw) <= 32_768:
        raise RuntimeError("The actual source proof must be private and bounded")
    proof = json.loads(raw)
    if proof.get("synthetic") is not True or not proof.get("sourceFingerprint") or not proof.get("bootstrapAccessToken"):
        raise RuntimeError("The actual synthetic source proof is incomplete")
    H["private_file"](output / "source-proof.json", raw)
    script = ("umask 077; mkdir -p no_backup && cat > no_backup/manual-account-recovery.json.part "
              "&& mv no_backup/manual-account-recovery.json.part no_backup/manual-account-recovery.json")
    H["adb"](destination, "shell", shlex.join(["run-as", H["PACKAGE"], "sh", "-c", script]), input_bytes=raw)
    observed = H["adb"](destination, "exec-out", "run-as", H["PACKAGE"], "cat", "no_backup/manual-account-recovery.json")
    if observed != raw:
        raise RuntimeError("The private source proof did not reach the selected destination intact")


def require_offline(serial):
    for _ in range(40):
        if H["radio_state"](serial) == (True, b"0"):
            return
        time.sleep(0.25)
    raise RuntimeError("The owned destination did not confirm airplane mode and disabled Wi-Fi")


def run(source, destination, output, *, focused=False):
    MEDIA["safe_target"](source)
    MEDIA["safe_target"](destination)
    if source == destination:
        raise ValueError("Manual recovery requires two distinct owned managed guests")
    output.mkdir(mode=0o700)
    original_radio = H["radio_state"](destination)
    if original_radio[0] or original_radio[1] not in (b"1", b"2"):
        raise RuntimeError("Manual recovery must start on an online task-owned managed guest")
    primary = None
    try:
        install_passkey_provider(destination, output, ROOT / "tools/passkey-test-provider/build/outputs/apk/debug/passkey-test-provider-debug.apk",
                                 Path(os.environ["ANDROID_HOME"]))
        if not focused:
            MEDIA["instrument"](
                source, "app.logdate.client.e2e.NativeRetainedJournalWorkspaceTest",
                output / "retained-journal-workspace.log",
                extras=["-e", "retainedJournalWorkspace", "true"], timeout=90,
            )
            MEDIA["instrument"](
                source, "app.logdate.client.e2e.RetainedDatabaseCopyTest",
                output / "retained-database-copy.log", timeout=90,
            )
            MEDIA["instrument"](
                source, BACKGROUND_CALLBACK_CLASS,
                output / "native-background-credential-callback.log", timeout=120,
                expected_tests=len(BACKGROUND_CALLBACK_CASES),
            )
            MEDIA["instrument"](
                source, "app.logdate.client.e2e.ArchivePickerCancellationE2ETest#cancellationDuringProviderIoAndLatePickerResults",
                output / "archive-picker-cancellation.log",
                extras=["-e", "archivePickerCancellationAcceptance", "true"], timeout=60,
            )
        MEDIA["instrument"](
            source, SOURCE_SELECTOR, output / "source-create.log",
            extras=["-e", "logdate.recoveryAcceptanceMode", "create"], timeout=240,
        )
        MEDIA["instrument"](
            source, SELECTOR, output / "source-proof.log",
            extras=["-e", "logdate.recoveryAcceptanceMode", "manual-account-source-proof"], timeout=60,
        )
        publish_source_fixture(source, destination, output)
        for phase in (("manual-account-recover",) if focused else ("manual-account-recover", "manual-account-offline")):
            if phase == "manual-account-offline":
                H["adb"](destination, "shell", "am", "force-stop", H["PACKAGE"])
                H["set_offline"](destination, True)
                require_offline(destination)
            MEDIA["instrument"](
                destination, SELECTOR, output / f"{phase}.log",
                extras=["-e", "logdate.recoveryAcceptanceMode", phase],
                timeout=420 if phase == "manual-account-recover" else 90,
            )
    except BaseException as error:
        primary = error
        raise
    finally:
        try:
            H["restore_radio_state"](destination, original_radio)
        except Exception as cleanup:
            if primary is None:
                raise
            add_failure_note(primary, "Managed radio restoration also failed: " + type(cleanup).__name__)
    receipt = {
        "retainedWorkspaceAndSqlCipherCopyAccepted": not focused,
        "nativeCredentialCallbackAutomaticRetryAccepted": not focused,
        "archivePickerCancellationLifecycleAccepted": not focused,
        "automaticLocalSourceFixture": True,
        "ordinaryRenderedSignInUsesRealCredentialManager": True,
        "ordinarySignInAdoptsPreviouslyUnboundOwner": True,
        "accountSessionDoesNotUnlockJournalKeys": True,
        "productionSettingsPhraseEntryViaNavigationHostAccepted": True,
        "signInUiAndSettingsNavigationAccepted": True,
        "sourceJournalIdentityAndOriginalMediaRetained": True,
        "coldOfflineReadability": not focused,
        "wrongAccountPhraseRejectedBeforeKeyMutation": True,
        "retainedLockedDatabaseRecoveryAccepted": False,
        "googleRestoreProviderAccepted": False,
    }
    H["private_file"](output / "acceptance.json", json.dumps(receipt, indent=2).encode() + b"\n")
    return receipt


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source", required=True, type=H["emulator_serial"])
    parser.add_argument("--destination", required=True, type=H["emulator_serial"])
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--fixture-control", required=True, type=Path)
    parser.add_argument("--synthetic-data", required=True, action="store_true")
    parser.add_argument("--focused", action="store_true", help="Run only the negative and positive manual recovery journey")
    options = parser.parse_args()
    primary = None
    try:
        run(options.source, options.destination, options.output, focused=options.focused)
    except BaseException as failure:
        primary = failure
        raise
    finally:
        try:
            H["private_file"](options.fixture_control / "complete", b"complete")
        except Exception as cleanup:
            if primary is None:
                raise
            add_failure_note(primary, "Local fixture completion notification also failed: " + type(cleanup).__name__)
