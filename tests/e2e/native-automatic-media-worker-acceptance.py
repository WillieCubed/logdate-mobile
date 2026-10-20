"""Exercise production media workers while the local enrollment server is still live."""

from pathlib import Path
import runpy

MEDIA = runpy.run_path(str(Path(__file__).with_name("native-missing-media-acceptance.py")))


def run(serial, control, server, output, wait_for):
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
    if wifi not in (b"0", b"1", b"2") or airplane or wifi == b"0":
        raise RuntimeError("Original-media acceptance requires the owned fixture online")

    def release_network():
        MEDIA["adb"](serial, "shell", "cmd", "netpolicy", "set", "restrict-background", "false")
        MEDIA["set_metered"](serial, network, "false")

    def release_after_media_phase():
        wait_for(control / "media-gate-held", server, seconds=30)
        release_network()

    def arm_after_startup():
        (control / "media-gate-arm").write_bytes(b"armed")
        MEDIA["adb"](serial, "shell", "run-as", MEDIA["PACKAGE"], "touch", "no_backup/native-media-worker-gate-armed")

    def instrument(mode, stage, actions=None):
        MEDIA["instrument"](
            serial, "app.logdate.client.e2e.NativeAutomaticMediaWorkerProbeTest#automaticRecovery",
            output / f"{mode}-{stage}.log",
            extras=["-e", "nativeAutomaticMediaMode", mode, "-e", "nativeAutomaticMediaStage", stage],
            status_actions=actions, timeout=180,
        )

    try:
        MEDIA["set_metered"](serial, network, "true")
        MEDIA["adb"](serial, "shell", "cmd", "netpolicy", "set", "restrict-background", "false")
        instrument("metered", "recover", {
            b"RESTORE_MEDIA_READY": arm_after_startup,
            b"RESTORE_MEDIA_DEFERRED": release_after_media_phase,
            b"RESTORE_MEDIA_POLICY_LIFTED": lambda: (control / "media-gate-release").write_bytes(b"release"),
        })
        MEDIA["set_metered"](serial, network, "true")
        MEDIA["adb"](serial, "shell", "cmd", "netpolicy", "set", "restrict-background", "true")
        instrument("restricted", "recover", {b"RESTORE_MEDIA_DEFERRED": release_network})
        MEDIA["set_metered"](serial, network, "true")
        instrument("restart", "prepare")
        MEDIA["safe_target"](serial)
        MEDIA["adb"](serial, "shell", "am", "force-stop", MEDIA["PACKAGE"])
        release_network()
        instrument("restart", "verify")
    finally:
        # Always release an armed test-server request, including failed app probes.
        (control / "media-gate-release").write_bytes(b"release")
        MEDIA["restore_environment"](serial, network, original, saver, airplane, wifi, {})
    return {"automaticOriginalMediaRecovery": True, "mediaInboxOperationRetainedAcrossRestart": True}
