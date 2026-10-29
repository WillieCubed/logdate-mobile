"""Native backup helpers with explicit entry-point dependencies."""

from contextlib import contextmanager
import io
import json
import tarfile
import uuid


def prepare_source(
    h,
    serial,
    app_apk,
    test_apk,
    output,
    identity,
    seed_source=True,
    snapshot_source=False,
    pending_camera_draft=False,
):
    if seed_source:
        print("Seeding a synthetic source journal", flush=True)
        h.adb(serial, "install", "-r", "-t", str(app_apk))
        h.adb(serial, "install", "-r", "-t", str(test_apk))
        h.probe(
            serial,
            "seed",
            output / "source-seed.log",
            identity=identity,
            pending_camera_draft=pending_camera_draft,
        )
        if pending_camera_draft:
            h.probe(
                serial, "pending-camera-source", output / "source-pending-camera.log"
            )
        h.recording_policy_probe(serial, output / "source-recording-policy.log")
    else:
        if snapshot_source:
            print(
                "Reading the retained synthetic source fixture without launching the app",
                flush=True,
            )
        else:
            print("Resuming the existing synthetic source journal", flush=True)
        marker = f"/data/user/0/{h.PACKAGE}/no_backup/native-backup-fixture.json"
        h.adb(serial, "shell", "run-as", h.PACKAGE, "test", "-s", marker)
    # The backup agent's file-based restore mode is a manifest contract. Update a resumed fixture
    # without clearing its synthetic content so the source actually writes that format.
    if not seed_source:
        h.adb(serial, "install", "-r", "-t", str(app_apk))
        h.adb(serial, "install", "-r", "-t", str(test_apk))
        if snapshot_source:
            source_files = set(h.package_user_files(serial))
            recovery_prefix = b"./files/"
            identity_backup = {
                "phrase": b"identity_recovery/phrase_backup",
                "account": b"identity_recovery/account_key_backup",
                "legacy-account": b"identity_recovery/account_key_backup",
            }[identity]
            required_source_files = {
                recovery_prefix + b"db_recovery/passphrase_backup",
                recovery_prefix + identity_backup,
                recovery_prefix + b"identity_recovery/media_key_backup",
            }
            missing_source_files = required_source_files - source_files
            if missing_source_files:
                raise RuntimeError(
                    "The retained synthetic source is missing portable recovery keys: "
                    + ", ".join(path.decode() for path in sorted(missing_source_files))
                )
            raw_fixture = h.adb(serial, "exec-out", "cat", marker)
            try:
                metadata = json.loads(raw_fixture)
            except (UnicodeDecodeError, json.JSONDecodeError) as error:
                raise RuntimeError(
                    "The retained synthetic source fixture is invalid"
                ) from error
            required = {"owner", "device", "fingerprint", "mediaCiphertext"}
            if not isinstance(metadata, dict) or not required.issubset(metadata):
                raise RuntimeError(
                    "The retained synthetic source fixture is incomplete"
                )
            if not all(metadata[key] for key in required):
                raise RuntimeError(
                    "The retained synthetic source fixture is incomplete"
                )
            h.private_file(output / "source-existing-fixture.json", raw_fixture)
        else:
            h.probe(serial, "compact-transfer", output / "source-compact-transfer.log")


@contextmanager
def data_only_source_session(h, source, output, enabled):
    if not enabled:
        yield None
        return
    session_id = str(uuid.uuid4())
    original_radio = h.radio_state(source)
    primary_error = None
    try:
        # The synthetic session has no hosted account. Its draft adoption must finish
        # before the source snapshot, while network work stays queued for real sign-in.
        h.set_offline(source, True)
        h.probe(
            source,
            "source-account-session",
            output / "source-account-session.log",
            source_session_id=session_id,
        )
        yield session_id
    except BaseException as error:
        primary_error = error
        raise
    finally:
        cleanup_errors = []
        actions = (
            lambda: h.probe(
                source,
                "source-account-session-cleanup",
                output / "source-account-session-cleanup.log",
                source_session_id=session_id,
            ),
            lambda: h.adb(source, "shell", "am", "force-stop", h.PACKAGE),
            lambda: h.restore_radio_state(source, original_radio),
        )
        for action in actions:
            try:
                action()
            except Exception as error:
                cleanup_errors.append(error)
        if cleanup_errors:
            if primary_error is None:
                raise RuntimeError(
                    "Synthetic source session or network cleanup failed"
                ) from cleanup_errors[0]
            h.add_failure_note(
                primary_error,
                "Synthetic source cleanup also failed: "
                + ", ".join(type(error).__name__ for error in cleanup_errors),
            )


def restore_transport(h, serial, *, system_widgets=False):
    if system_widgets:
        # A filtered single-package restore never finishes Android's system widget restore.
        # Restore-all is safe here only when the *actual* dataset holds our synthetic app.
        payload = h.adb(
            serial, "exec-out", "tar", "-C", h.TRANSPORT_FILES, "-cf", "-", "1"
        )
        h.validate_widget_restore_dataset(payload)
    arguments = () if system_widgets else (h.PACKAGE,)
    return h.adb(serial, "shell", "bmgr", "restore", "1", *arguments)


def validate_widget_restore_dataset(h, payload):
    found_app = False
    found_metadata = False
    with tarfile.open(fileobj=io.BytesIO(payload), mode="r:") as archive:
        for item in archive:
            name = item.name.removeprefix("./").rstrip("/")
            if (
                item.issym()
                or item.islnk()
                or any(part in {"", ".", ".."} for part in name.split("/"))
            ):
                raise RuntimeError(
                    "System widget restore requires an isolated LogDate dataset"
                )
            if item.isdir() and name in {"1", "1/_full", "1/_delta", "1/_delta/@pm@"}:
                continue
            if item.isfile() and name == f"1/_full/{h.PACKAGE}":
                if found_app:
                    raise RuntimeError(
                        "System widget restore contains duplicate app payloads"
                    )
                found_app = True
            elif (
                item.isfile()
                and name.startswith("1/_delta/@pm@/")
                and len(name.split("/")) == 4
            ):
                found_metadata = True
                continue
            else:
                raise RuntimeError(
                    "System widget restore contains an unrelated payload"
                )
    if not found_app:
        raise RuntimeError("System widget restore is missing the LogDate payload")
    if not found_metadata:
        raise RuntimeError("System widget restore is missing package metadata")


def capture_widget_restore_delivery(h, serial, output):
    h.private_file(
        output.with_suffix(".system.log"),
        h.adb(serial, "logcat", "-b", "all", "-d", "-v", "threadtime"),
    )
    for service in ("appwidget", "activity broadcasts", "package " + h.PACKAGE):
        h.private_file(
            output.with_suffix("." + service.split()[0] + ".txt"),
            h.adb(serial, "shell", "dumpsys", *service.split()),
        )


FUNCTIONS = {
    "prepare_source": prepare_source,
    "data_only_source_session": data_only_source_session,
    "restore_transport": restore_transport,
    "validate_widget_restore_dataset": validate_widget_restore_dataset,
    "capture_widget_restore_delivery": capture_widget_restore_delivery,
}
