"""Exercise production media workers while the local enrollment server is still live."""

from pathlib import Path
import runpy
import uuid

MEDIA = runpy.run_path(str(Path(__file__).with_name("native-missing-media-acceptance.py")))


def run(serial, control, server, output, wait_for, failure_diagnostics=None):
    MEDIA["safe_target"](serial)
    output.mkdir(mode=0o700)
    network = "AndroidWifi"
    rows = MEDIA["adb"](serial, "shell", "cmd", "netpolicy", "list", "wifi-networks").stdout.decode().splitlines()
    original = next((line.rsplit(";", 1)[1] for line in rows if line.rsplit(";", 1)[0] == network), None)
    if original is None:
        raise RuntimeError("The owned emulator's Wi-Fi network is not configured")
    saver = b"enabled" in MEDIA["adb"](serial, "shell", "cmd", "netpolicy", "get", "restrict-background").stdout
    airplane = MEDIA["adb"](serial, "shell", "settings", "get", "global", "airplane_mode_on").stdout.strip() == b"1"
    wifi = MEDIA["adb"](serial, "shell", "settings", "get", "global", "wifi_on").stdout.strip()
    cellular = MEDIA["adb"](serial, "shell", "settings", "get", "global", "mobile_data").stdout.strip()
    if wifi not in (b"0", b"1", b"2") or airplane or wifi == b"0":
        raise RuntimeError("Original-media acceptance requires the owned fixture online")
    if cellular not in (b"0", b"1"):
        raise RuntimeError("The owned fixture's cellular setting must be readable before network isolation")

    def release_network():
        MEDIA["adb"](serial, "shell", "cmd", "netpolicy", "set", "restrict-background", "false")
        MEDIA["set_metered"](serial, network, "false")

    def arm_after_startup():
        (control / "media-gate-arm").write_bytes(b"armed")
        MEDIA["adb"](serial, "shell", "run-as", MEDIA["PACKAGE"], "touch", "no_backup/native-media-worker-gate-armed")

    def arm_upload_after_startup():
        (control / "media-upload-gate-arm").write_bytes(b"armed")
        MEDIA["adb"](serial, "shell", "run-as", MEDIA["PACKAGE"], "touch", "no_backup/native-media-upload-gate-armed")

    def instrument(mode, stage, actions=None):
        selector = ("NativeDeferredOriginalUploadProbeTest#policyLiftAfterUploadPhaseResumesTheRetainedOriginal"
                    if mode == "upload" else "NativeAutomaticMediaWorkerProbeTest#automaticRecovery")
        nonce = uuid.uuid4().hex if stage == "recover" else None

        def handoff():
            if mode in {"metered", "upload"}:
                prefix = "media-upload" if mode == "upload" else "media"
                wait_for(control / (prefix + "-gate-held"), server, seconds=30)
            MEDIA["release_network_handoff"](serial, network, nonce, output / f"{mode}-{stage}-handoff.json")

        extras = ["-e", "nativeAutomaticMediaMode", mode, "-e", "nativeAutomaticMediaStage", stage]
        if nonce is not None:
            extras += ["-e", "restoreNetworkHandoff", nonce]
        MEDIA["instrument"](
            serial, "app.logdate.client.e2e." + selector,
            output / f"{mode}-{stage}.log",
            extras=extras, release_network=handoff if nonce is not None else None,
            status_actions=actions, timeout=300 if nonce is not None else 180,
            failure_diagnostics=failure_diagnostics,
        )

    primary = None
    try:
        # Metering AndroidWifi cannot exercise policy changes while cellular is the default.
        MEDIA["adb"](serial, "shell", "svc", "data", "disable")
        MEDIA["adb"](serial, "shell", "svc", "wifi", "enable")
        MEDIA["adb"](serial, "shell", "cmd", "wifi", "connect-network", network, "open")
        MEDIA["set_metered"](serial, network, "true")
        MEDIA["adb"](serial, "shell", "cmd", "netpolicy", "set", "restrict-background", "false")
        instrument("metered", "recover", {
            b"RESTORE_MEDIA_READY": arm_after_startup,
            b"RESTORE_MEDIA_POLICY_LIFTED": lambda: (control / "media-gate-release").write_bytes(b"release"),
        })
        MEDIA["set_metered"](serial, network, "true")
        MEDIA["adb"](serial, "shell", "cmd", "netpolicy", "set", "restrict-background", "true")
        instrument("restricted", "recover")
        MEDIA["set_metered"](serial, network, "true")
        instrument("restart", "prepare")
        MEDIA["safe_target"](serial)
        MEDIA["adb"](serial, "shell", "am", "force-stop", MEDIA["PACKAGE"])
        release_network()
        instrument("restart", "verify")
        MEDIA["set_metered"](serial, network, "true")
        instrument("upload", "recover", {
            b"RESTORE_MEDIA_UPLOAD_READY": arm_upload_after_startup,
            b"RESTORE_MEDIA_UPLOAD_POLICY_LIFTED": lambda: (control / "media-upload-gate-release").write_bytes(b"release"),
        })
    except BaseException as failure:
        primary = failure
        for label, arguments in (("wifi", ("cmd", "wifi", "status")), ("connectivity", ("dumpsys", "connectivity"))):
            try:
                snapshot = MEDIA["adb"](serial, "shell", *arguments).stdout
                path = output / f"failure-network-{label}.txt"
                path.write_bytes(snapshot)
                path.chmod(0o600)
            except Exception as error:
                failure.add_note(f"Could not retain managed {label} diagnostics: {type(error).__name__}")
        raise
    finally:
        errors = []
        for operation in (
            lambda: (control / "media-gate-release").write_bytes(b"release"),
            lambda: (control / "media-upload-gate-release").write_bytes(b"release"),
            lambda: MEDIA["restore_environment"](serial, network, original, saver, airplane, wifi, {}),
            lambda: MEDIA["adb"](serial, "shell", "svc", "data", "enable" if cellular == b"1" else "disable"),
        ):
            try:
                operation()
            except Exception as error:
                errors.append(error)
        try:
            actual = MEDIA["adb"](serial, "shell", "settings", "get", "global", "mobile_data").stdout.strip()
            if actual != cellular:
                raise RuntimeError("The owned fixture's cellular setting was not restored")
        except Exception as error:
            errors.append(error)
        if errors:
            if primary is None:
                raise errors[0]
            for error in errors:
                primary.add_note("Automatic media cleanup also failed: " + type(error).__name__)
    return {"automaticOriginalMediaRecovery": True, "mediaInboxOperationRetainedAcrossRestart": True,
            "deferredOriginalUploadResumesAfterPolicyLiftDuringWorker": True}
