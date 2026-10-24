"""Keep one original-media recovery through real revocation, cold startup and passkey sign-in."""

from pathlib import Path
import runpy
import shlex

H = runpy.run_path(str(Path(__file__).with_name("native-backup-acceptance.py")))
add_failure_note = H["add_failure_note"]
MEDIA = runpy.run_path(str(Path(__file__).with_name("native-missing-media-acceptance.py")))


def binary_request_count(control):
    log = control / "media-request-paths.log"
    if not (control / "media-gate-arm").is_file() or not log.is_file():
        raise RuntimeError("The real server's original-media observation must be active")
    return sum(line.endswith("/binary") for line in log.read_text().splitlines())


def read_app_document(serial, name):
    raw = H["adb"](serial, "exec-out", "run-as", H["PACKAGE"], "cat", "no_backup/" + name)
    if not 1 <= len(raw) <= 32_768:
        raise RuntimeError("The private authentication document is missing or oversized")
    return raw


def write_app_document(serial, name, raw):
    if not 1 <= len(raw) <= 32_768:
        raise RuntimeError("The private authentication response is missing or oversized")
    script = f"umask 077; cat > no_backup/{name}.part && mv no_backup/{name}.part no_backup/{name}"
    H["adb"](serial, "shell", shlex.join(["run-as", H["PACKAGE"], "sh", "-c", script]), input_bytes=raw)
    if read_app_document(serial, name) != raw:
        raise RuntimeError("Private authentication response did not round-trip through managed custody")


def run(serial, control, server, output, wait_for, publish_control):
    MEDIA["safe_target"](serial)
    output.mkdir(mode=0o700)
    network = "AndroidWifi"
    rows = H["adb"](serial, "shell", "cmd", "netpolicy", "list", "wifi-networks").decode().splitlines()
    original = next((line.rsplit(";", 1)[1] for line in rows if line.rsplit(";", 1)[0] == network), None)
    if original is None:
        raise RuntimeError("The owned emulator's Wi-Fi policy is unavailable")
    saver = b"enabled" in H["adb"](serial, "shell", "cmd", "netpolicy", "get", "restrict-background")
    signed_out_requests = None

    def exchange(request, response):
        publish_control(control / request, read_app_document(serial, request))
        wait_for(control / response, server, seconds=30)
        if request == "media-reauth-assertion-request.json":
            cold_signed_out()
        write_app_document(serial, response, (control / response).read_bytes())

    def release_network():
        MEDIA["set_metered"](serial, network, "false")

    def signed_out():
        nonlocal signed_out_requests
        wait_for(control / "media-reauth-client-refresh-observed", server, seconds=30)
        signed_out_requests = binary_request_count(control)

    def cold_signed_out():
        if signed_out_requests is None or binary_request_count(control) != signed_out_requests:
            raise RuntimeError("An original-media request ran while the restored journal was signed out")

    def authenticated_boundary():
        cold_signed_out()
        write_app_document(serial, "media-reauth-authentication-boundary.txt", b"verified")

    def instrument(stage, actions):
        MEDIA["instrument"](
            serial, "app.logdate.client.e2e.NativeMediaReauthenticationProbeTest#revokedSessionRetainsOriginalUntilRealReauthentication",
            output / f"{stage}.log", extras=["-e", "nativeMediaReauthenticationStage", stage],
            status_actions=actions, timeout=300,
        )

    primary = None
    try:
        H["adb"](serial, "shell", "cmd", "netpolicy", "set", "restrict-background", "false")
        MEDIA["set_metered"](serial, network, "true")
        instrument("revoke", {
            b"MEDIA_REAUTH_REVOKE": lambda: exchange("media-reauth-revoke-request.json", "media-reauth-expired-session.json"),
            b"MEDIA_REAUTH_EXPIRED": release_network,
            b"MEDIA_REAUTH_SIGNED_OUT": signed_out,
        })
        H["adb"](serial, "shell", "am", "force-stop", H["PACKAGE"])
        instrument("resume", {
            b"MEDIA_REAUTH_COLD_SIGNED_OUT": cold_signed_out,
            b"MEDIA_REAUTH_ASSERTION": lambda: exchange("media-reauth-assertion-request.json", "media-reauth-assertion-response.json"),
            b"MEDIA_REAUTH_SERVER_AUTHENTICATED": authenticated_boundary,
        })
        publish_control(control / "media-reauth-complete", b"complete")
        return {"revokedRefreshPreservesOriginalRecovery": True, "signedOutColdRestartDoesNotFetchOriginal": True,
                "realPasskeyRepositoryResumesSameMediaOperation": True}
    except BaseException as error:
        primary = error
        raise
    finally:
        failures = []
        for restore in (
            lambda: MEDIA["set_metered"](serial, network, "undefined" if original == "none" else original),
            lambda: H["adb"](serial, "shell", "cmd", "netpolicy", "set", "restrict-background", str(saver).lower()),
        ):
            try:
                restore()
            except Exception as error:
                failures.append(type(error).__name__)
        if failures:
            if primary is None:
                raise RuntimeError("Reauthentication network cleanup failed: " + ", ".join(failures))
            add_failure_note(primary, "Network cleanup also failed: " + ", ".join(failures))
