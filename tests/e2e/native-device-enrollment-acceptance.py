#!/usr/bin/env python3
"""Verify PostgreSQL device enrollment around a populated Android native backup/restore."""

import argparse
from functools import wraps
import json
import os
from pathlib import Path
import runpy
import shlex
import socket
import stat
import subprocess
import tempfile
import time
from urllib.parse import urlsplit


ROOT = Path(__file__).resolve().parents[2]
H = runpy.run_path(str(Path(__file__).with_name("native-backup-acceptance.py")))
add_failure_note = H["add_failure_note"]
PACKAGE = H["PACKAGE"]
RUNNER = H["RUNNER"]
FIXTURE = "native-device-enrollment-fixture.json"
RECEIPT = "native-device-enrollment-receipt.json"
PROBE = "app.logdate.client.e2e.NativeDeviceEnrollmentProbeTest"
REAUTHENTICATION = runpy.run_path(
    str(Path(__file__).with_name("native-media-reauthentication-acceptance.py"))
)
AUTOMATIC_MEDIA = runpy.run_path(
    str(Path(__file__).with_name("native-automatic-media-worker-acceptance.py"))
)


class _EnrollmentDependencies:
    """Resolve dependencies from the public entry point at each helper call."""

    def __init__(self, namespace):
        self.namespace = namespace

    def __getattr__(self, name):
        return self.namespace[name]


_dependencies = _EnrollmentDependencies(globals())


def _bind_probe(implementation):
    @wraps(implementation)
    def invoke(*args, **kwargs):
        return implementation(_dependencies, *args, **kwargs)

    return invoke


_probe_helpers = runpy.run_path(
    str(Path(__file__).with_name("native-enrollment-probes.py"))
)
globals().update(
    {
        name: _bind_probe(implementation)
        for name, implementation in _probe_helpers["FUNCTIONS"].items()
    }
)


def server_timeout_seconds(environment):
    seconds = int(environment.get("LOGDATE_NATIVE_ACCEPTANCE_TIMEOUT_SECONDS", "1800"))
    if not 60 <= seconds <= 3600:
        raise ValueError("Enrollment acceptance requires a bounded server lifetime")
    return seconds


def server_environment(environment, port):
    if not isinstance(port, int) or not 1 <= port <= 65535:
        raise ValueError("A valid owned loopback server port is required")
    return {
        **environment,
        "ATPROTO_PDS_SERVICE_URL": f"http://10.0.2.2:{port}",
        "WEBAUTHN_ORIGIN": "https://10.0.2.2",
        "WEBAUTHN_RP_ID": "10.0.2.2",
        "ATPROTO_OAUTH_ISSUER": "https://10.0.2.2",
        "ATPROTO_OAUTH_RESOURCE": "https://10.0.2.2",
        "ATPROTO_HOSTED_DID_METHOD": "web",
    }


def publish_control(path, content):
    if path.exists():
        raise FileExistsError("A fresh control document is required")
    with tempfile.TemporaryDirectory(prefix=".pending-", dir=path.parent) as folder:
        staged = Path(folder) / "payload"
        H["private_file"](staged, content)
        os.replace(staged, path)


def cleanup(process, radios, primary=None):
    failures = []
    try:
        if process is not None and process.poll() is None:
            process.terminate()
            try:
                process.wait(timeout=10)
            except subprocess.TimeoutExpired:
                process.kill()
                process.wait(timeout=5)
    except Exception:
        failures.append("owned server shutdown")
    for serial, original in radios.items():
        try:
            checked_emulator(serial)
            H["restore_radio_state"](serial, original)
        except Exception:
            failures.append(serial + " radio restoration")
    if failures:
        if primary is None:
            raise RuntimeError("Enrollment cleanup failed: " + ", ".join(failures))
        for failure in failures:
            add_failure_note(primary, "Enrollment cleanup also failed: " + failure)


def registry_check(control, process, stage, identifiers, owner, proof=None):
    expected = sorted(identifiers)
    publish_control(
        control / f"expected-{stage}.json",
        json.dumps({"devices": expected, "proof": proof}).encode(),
    )
    response = control / f"verified-{stage}.json"
    wait_for(response, process, seconds=60)
    verified = json.loads(response.read_text())
    if (
        verified.get("owner") != owner
        or sorted(verified.get("devices", [])) != expected
        or verified.get("duplicates") is not False
    ):
        raise RuntimeError(
            "Server registry verification returned an unexpected account or installation"
        )
    if stage != "source":
        actual = verified.get("transferProof", {})
        if (
            not proof
            or actual.get("encryptedCaptureRetained") is not True
            or actual.get("encryptedDraft") is not True
            or any(
                actual.get(key) != proof.get(key)
                for key in (
                    "device",
                    "historicalCreatedAt",
                    "draftCreatedAt",
                    "offlineCreatedAt",
                )
            )
        ):
            raise RuntimeError(
                "The server did not confirm replacement-device attribution and retained creation metadata"
            )


def fixture_environment(path):
    if stat.S_IMODE(path.stat().st_mode) != 0o600 or path.stat().st_size > 32_768:
        raise RuntimeError(
            "The local database fixture environment must be a private bounded JSON file"
        )
    configuration = json.loads(path.read_text())
    database_url = configuration.get("DATABASE_URL", "")
    try:
        parsed = urlsplit(database_url.removeprefix("jdbc:"))
        local = (
            database_url.startswith("jdbc:postgresql://")
            and parsed.hostname == "127.0.0.1"
            and parsed.username is None
            and parsed.password is None
            and parsed.port is not None
            and not parsed.query
            and not parsed.fragment
        )
    except ValueError:
        local = False
    if not local:
        raise RuntimeError("Only a local PostgreSQL acceptance fixture is allowed")
    required = (
        "DATABASE_USER",
        "DATABASE_PASSWORD",
        "SERVER_ENCRYPTION_KEY",
        "LOGDATE_BLOB_STORAGE_DIR",
    )
    if not all(configuration.get(name) for name in required):
        raise RuntimeError("The PostgreSQL acceptance fixture is incomplete")
    return configuration


def require_installation_trust_fixture(metadata):
    if metadata.get("installationTrustFixture") is not True:
        raise RuntimeError("Native transfer did not exercise installation trust")


def run_acceptance(options, control, process):
    wait_for(control / "ready", process)
    fixture = json.loads((control / "fixture.json").read_text())
    for serial in (options.source, options.destination):
        checked_emulator(serial)
        H["prepare"](
            serial,
            allow_preinstalled_empty=getattr(options, "seed_empty_source", False),
        )
    bring_fixture_online(
        options.source, options.output / "source-network-preflight.txt"
    )
    for apk in (options.app_apk, options.test_apk):
        H["adb"](options.source, "install", "-r", "-t", str(apk))
    private_fixture(options.source, fixture)
    probe(options.source, "source-prepare", options.output / "source-prepare.log")
    H["probe"](
        options.source,
        "seed",
        options.output / "source-seed.log",
        identity="account",
        installation_trust=True,
    )
    retained_draft_probe(
        options.source, "seed", options.output / "source-editor-draft.log"
    )
    probe(options.source, "source-enroll", options.output / "source-enroll.log")
    retained_draft_probe(
        options.source, "upload", options.output / "source-editor-original-upload.log"
    )
    source = receipt(options.source)
    registry_check(control, process, "source", {source["device"]}, fixture["owner"])
    probe(options.source, "cleanup", options.output / "source-session-cleanup.log")
    H["adb"](options.source, "shell", "am", "force-stop", PACKAGE)

    # Use Android's actual backup service, including duplicate native delivery and offline restart.
    native = options.output / "native-transfer"
    native.mkdir(mode=0o700)
    H["transfer"](
        options.source,
        options.destination,
        options.app_apk,
        options.test_apk,
        native,
        "account",
        seed_source=False,
        exercise_duplicate_restore=True,
    )
    metadata = json.loads((native / "source-fixture.json").read_text())
    require_installation_trust_fixture(metadata)
    if any(
        metadata[field] != source[field] for field in ("owner", "device", "fingerprint")
    ):
        raise RuntimeError("The native source identity changed before backup")
    # The no-backup credential file must be absent until after native journal verification.
    checked_emulator(options.destination)
    H["adb"](
        options.destination,
        "shell",
        "run-as",
        PACKAGE,
        "test",
        "!",
        "-e",
        "no_backup/" + FIXTURE,
    )
    private_fixture(
        options.destination,
        {
            **fixture,
            "sourceDevice": source["device"],
            "sourceFingerprint": source["fingerprint"],
            "sourceProof": source["proof"],
        },
    )
    H["set_offline"](options.destination, True)
    probe(
        options.destination,
        "destination-edit",
        options.output / "destination-offline-edit.log",
    )
    bring_fixture_online(
        options.destination, options.output / "destination-network-reconnect.txt"
    )
    probe(
        options.destination,
        "destination-enroll",
        options.output / "destination-enroll.log",
    )
    destination = receipt(options.destination)
    if destination.get("automaticSyncObserved") is not True or not destination.get(
        "syncWorkIds"
    ):
        raise RuntimeError("Normal startup did not observe real WorkManager sync")
    expected = {source["device"], destination["device"]}
    if (
        len(expected) != 2
        or destination["owner"] != source["owner"]
        or destination["fingerprint"] != source["fingerprint"]
    ):
        raise RuntimeError(
            "Replacement device identity did not retain the journal with a fresh installation ID"
        )
    proof = {**destination["proof"], **source["proof"], "device": destination["device"]}
    registry_check(control, process, "destination", expected, fixture["owner"], proof)
    checked_emulator(options.destination)
    H["adb"](options.destination, "shell", "am", "force-stop", PACKAGE)
    probe(
        options.destination,
        "replay",
        options.output / "destination-process-restart.log",
    )
    restarted = receipt(options.destination)
    if any(
        restarted[field] != destination[field]
        for field in ("owner", "device", "fingerprint")
    ):
        raise RuntimeError("Process restart replaced destination identity")
    registry_check(control, process, "replay", expected, fixture["owner"], proof)
    media = AUTOMATIC_MEDIA["run"](
        options.destination,
        control,
        process,
        options.output / "automatic-media",
        wait_for,
        failure_diagnostics=lambda serial, log, failure: capture_failure_diagnostics(
            serial,
            options.output / "failure-diagnostics" / log.stem,
        ),
    )
    media.update(
        REAUTHENTICATION["run"](
            options.destination,
            control,
            process,
            options.output / "media-reauthentication",
            wait_for,
            publish_control,
        )
    )
    H["set_offline"](options.destination, True)
    current_draft_probe(
        options.destination, "prepare", options.output / "current-draft-prepare.log"
    )
    H["adb"](options.destination, "shell", "am", "force-stop", PACKAGE)
    current_draft_probe(
        options.destination,
        "quarantine",
        options.output / "current-draft-quarantine.log",
    )
    bring_fixture_online(
        options.destination, options.output / "draft-network-reconnect.txt"
    )
    H["adb"](options.destination, "shell", "am", "force-stop", PACKAGE)
    current_draft_probe(
        options.destination, "complete", options.output / "current-draft-complete.log"
    )
    retained_draft_probe(
        options.destination,
        "verify",
        options.output / "destination-editor-shortcut.log",
    )
    H["adb"](options.destination, "shell", "am", "force-stop", PACKAGE)
    current_draft_probe(
        options.destination, "reopened", options.output / "current-draft-reopened.log"
    )
    H["set_offline"](options.destination, True)
    current_draft_probe(
        options.destination,
        "base-prepare",
        options.output / "current-draft-base-prepare.log",
    )
    H["adb"](options.destination, "shell", "am", "force-stop", PACKAGE)
    bring_fixture_online(
        options.destination, options.output / "base-network-reconnect.txt"
    )
    current_draft_probe(
        options.destination,
        "base-complete",
        options.output / "current-draft-base-complete.log",
    )
    probe(
        options.destination,
        "cleanup",
        options.output / "destination-session-cleanup.log",
    )
    publish_control(control / "complete", b"complete")
    if process.wait(timeout=15) != 0:
        raise RuntimeError(
            "The PostgreSQL registry did not confirm all native enrollment stages"
        )
    return {
        **media,
        "currentEditorDraftShortcutRetained": True,
        "currentEditorColdProviderCollisionQuarantined": True,
        "currentEditorOriginalRecoveredByStartupWorker": True,
        "currentEditorMissingRecordingMetadataRetained": True,
        "currentEditorRecoveredBasePublishesWithoutFalseConflict": True,
        "nativeBackupServiceTransfer": True,
        "duplicateNativeDelivery": True,
        "offlineJournalAndOriginalsRetained": True,
        "sourceSessionNotTransferred": True,
        "journalOwnerAndKeyRetained": True,
        "distinctInstallationIds": True,
        "realPostgresRegistryRetainsBothDevicesOnce": True,
        "processRestartRetainsEnrollment": True,
        "normalStartupScheduledAndRanSync": True,
        "replacementDraftDeviceAttributedByServer": True,
        "offlineDraftEditUploadedAndDecrypted": True,
        "historicalCreationAndCaptureMetadataRetained": True,
        "fixtureSessionsCleared": True,
        "googleProviderAccepted": False,
        "playSignedArtifact": False,
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source", required=True, type=H["emulator_serial"])
    parser.add_argument("--destination", required=True, type=H["emulator_serial"])
    parser.add_argument("--app-apk", required=True, type=Path)
    parser.add_argument("--test-apk", required=True, type=Path)
    parser.add_argument("--classpath", required=True, type=Path)
    parser.add_argument("--fixture-environment", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--seed-empty-source", action="store_true")
    options = parser.parse_args()
    if options.source == options.destination:
        parser.error("source and destination must be distinct emulators")
    for path in (
        options.app_apk,
        options.test_apk,
        options.classpath,
        options.fixture_environment,
    ):
        if not path.is_file():
            parser.error(
                "APKs, harness classpath, and private database fixture must exist"
            )
    os.umask(0o077)
    environment = os.environ.copy()
    server_lifetime = server_timeout_seconds(environment)
    environment.update(fixture_environment(options.fixture_environment))
    environment.update(
        LOGDATE_OAUTH_PASSKEY_SIGN_IN_ENABLED="true",
        WEBAUTHN_STRICT_VERIFICATION="true",
        JAVA_TOOL_OPTIONS="-Djava.awt.headless=true",
    )
    with socket.socket() as reservation:
        reservation.bind(("127.0.0.1", 0))
        port = reservation.getsockname()[1]
    environment = server_environment(environment, port)
    options.output.mkdir(mode=0o700, parents=True, exist_ok=False)
    control = options.output / "server-control"
    control.mkdir(mode=0o700)
    radios = {}
    process = None
    primary = None
    try:
        for serial in (options.source, options.destination):
            checked_emulator(serial)
            radios[serial] = H["radio_state"](serial)
        with (options.output / "server.log").open("wb") as log:
            process = subprocess.Popen(
                [
                    "java",
                    "-Xmx1g",
                    "-Djava.awt.headless=true",
                    "-cp",
                    options.classpath.read_text().strip(),
                    "app.logdate.integration.e2e.harness.NativeDeviceEnrollmentHarness",
                    str(control),
                    str(port),
                    str(server_lifetime),
                ],
                cwd=ROOT,
                env=environment,
                stdout=log,
                stderr=subprocess.STDOUT,
            )
        result = run_acceptance(options, control, process)
    except BaseException as failure:
        primary = failure
        raise
    finally:
        cleanup(process, radios, primary=primary)
    H["private_file"](
        options.output / "acceptance.json",
        json.dumps(result, indent=2).encode() + b"\n",
    )
    print(
        "Native transfer retained the journal and registered distinct installations without duplicates",
        flush=True,
    )


if __name__ == "__main__":
    main()
