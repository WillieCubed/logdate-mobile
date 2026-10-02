#!/usr/bin/env python3
"""Run the disposable two-managed-device recovery acceptance probe.

The server and account live only for this invocation. Credentials and recovery words are
written to a mode-0600 generated androidTest asset, never to Gradle or instrumentation args.
Run only with the Gradle slot reserved for this harness; it starts its own Gradle processes.
"""

import contextlib
import base64
import datetime
import hashlib
import importlib.util
import json
import os
import re
import secrets
import shutil
import signal
import socket
import stat
import subprocess
import sys
import tempfile
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid
from pathlib import Path
from xml.etree import ElementTree


ROOT = Path(__file__).resolve().parents[2]
DATABASE_FIXTURE_VARIABLE = "LOGDATE_ACCEPTANCE_DATABASE_FIXTURE"
PROBE_CLASS = "app.logdate.client.e2e.RecoveryAcceptanceProbeTest"
ID_NAMES = (
    "journalPrimary", "journalSecondary", "textNote", "imageNote", "audioNote",
    "videoNote", "deletedNote", "richDraft", "mediaDraft", "deletedDraft",
    "richTextBlock", "richImageBlock", "richAudioBlock", "richVideoBlock",
    "mediaImageBlock", "mediaVideoBlock", "deletedDraftBlock",
)


def private_file(path: Path, contents: bytes = b"") -> None:
    descriptor = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    with os.fdopen(descriptor, "wb") as output:
        output.write(contents)


def database_environment() -> dict[str, str]:
    fixture_value = os.environ.get(DATABASE_FIXTURE_VARIABLE)
    if not fixture_value:
        raise RuntimeError(
            f"set {DATABASE_FIXTURE_VARIABLE} to a directory holding a mode-0600 environment.json "
            "with a local PostgreSQL DATABASE_URL"
        )
    fixture_dir = Path(fixture_value)
    environment_file = fixture_dir / "environment.json"
    if stat.S_IMODE(environment_file.stat().st_mode) != 0o600:
        raise RuntimeError("database fixture environment must be mode 0600")
    parsed = urllib.parse.urlsplit(json.loads(environment_file.read_text())["DATABASE_URL"])
    if parsed.scheme != "postgresql" or parsed.hostname not in ("127.0.0.1", "localhost"):
        raise RuntimeError("the existing database fixture must be local PostgreSQL")
    if not parsed.username or not parsed.password or not parsed.path.strip("/"):
        raise RuntimeError("the existing database fixture is incomplete")
    return {
        "DATABASE_URL": f"jdbc:postgresql://{parsed.hostname}:{parsed.port}/{parsed.path.lstrip('/')}",
        "DATABASE_USER": urllib.parse.unquote(parsed.username),
        "DATABASE_PASSWORD": urllib.parse.unquote(parsed.password),
    }


def free_port() -> int:
    with socket.socket() as listener:
        listener.bind(("127.0.0.1", 0))
        return listener.getsockname()[1]


def request(url: str, *, token: str | None = None, method: str = "GET", timeout: int = 3):
    headers = {"Authorization": f"Bearer {token}"} if token else {}
    with urllib.request.urlopen(
        urllib.request.Request(url, headers=headers, method=method), timeout=timeout
    ) as response:
        return response.status, response.read()


def wait_for_server(base: str, health_token: str, server: subprocess.Popen) -> None:
    for _ in range(120):
        if server.poll() is not None:
            raise RuntimeError("owned server exited before becoming healthy")
        try:
            probe = urllib.request.Request(
                f"{base}/health", headers={"X-LogDate-Health-Token": health_token}
            )
            with urllib.request.urlopen(probe, timeout=2) as response:
                body = json.load(response)
                if response.status == 200 and body.get("status") == "healthy" and body.get("db_connected") is True:
                    return
        except (OSError, ValueError, urllib.error.URLError):
            pass
        time.sleep(1)
    raise RuntimeError("owned server did not report a connected PostgreSQL database")


def recovery_words() -> list[str]:
    wordlist_source = (
        ROOT / "client/device/src/commonMain/kotlin/app/logdate/client/device/crypto/Bip39Wordlist.kt"
    ).read_text()
    words = re.findall(r'^\s*"([a-z]+)",?\s*$', wordlist_source, re.MULTILINE)
    if len(words) != 2048 or len(set(words)) != 2048:
        raise RuntimeError("BIP39 wordlist is incomplete")
    entropy = secrets.token_bytes(16)
    bits = f"{int.from_bytes(entropy, 'big'):0128b}" + f"{hashlib.sha256(entropy).digest()[0] >> 4:04b}"
    return [words[int(bits[index:index + 11], 2)] for index in range(0, 132, 11)]


def load_passkey_verifier():
    for dependency in ("requests", "cryptography", "fido2"):
        if importlib.util.find_spec(dependency) is None:
            raise RuntimeError("passkey verifier dependencies are missing; install scripts/passkey-verify/requirements.txt")
    path = ROOT / "scripts/passkey-verify/sim.py"
    spec = importlib.util.spec_from_file_location("logdate_passkey_verifier", path)
    if spec is None or spec.loader is None:
        raise RuntimeError("passkey verifier unavailable")
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def run_gradle(command: list[str], environment: dict[str, str], log_path: Path) -> int:
    with log_path.open("ab") as output:
        process = subprocess.Popen(
            command, cwd=ROOT, env=environment, stdout=output, stderr=subprocess.STDOUT,
            start_new_session=True,
        )
        try:
            return process.wait()
        except BaseException:
            stop_owned_server(process)
            raise


def run_managed_device(device: str, mode: str, backend: str, asset_dir: Path, log_path: Path) -> dict:
    results_dir = ROOT / "app/android-main/build/outputs/androidTest-results/managedDevice/debug" / device
    for old_result in results_dir.glob("TEST-*.xml"):
        old_result.unlink()
    command = [
        str(ROOT / "gradlew"), f":app:android-main:{device}DebugAndroidTest",
        f"-Plogdate.backendUrl={backend}",
        f"-Plogdate.recoveryAcceptanceAssetsDir={asset_dir}",
        f"-Plogdate.recoveryAcceptanceMode={mode}",
        f"-Plogdate.androidTestClass={PROBE_CLASS}",
        "-Plogdate.androidTestOrchestrator=false",
        "-Plogdate.androidTestCoverage=false",
        "--no-daemon", "--no-configuration-cache", "--max-workers=1", "--console=plain",
        "-Dorg.gradle.jvmargs=-Xmx3g -XX:MaxMetaspaceSize=768m -Dfile.encoding=UTF-8",
        "-Pkotlin.compiler.execution.strategy=in-process",
    ]
    if run_gradle(command, os.environ.copy(), log_path) != 0:
        raise RuntimeError(f"{device} {mode} managed-device task failed")
    suites = [ElementTree.parse(path).getroot() for path in results_dir.glob("TEST-*.xml")]
    cases = [case for suite in suites for case in suite.iter("testcase") if case.get("classname") == PROBE_CLASS]
    if len(cases) != 1 or any(case.find(tag) is not None for case in cases for tag in ("failure", "error", "skipped")):
        raise RuntimeError(f"{device} {mode} did not execute exactly one passing recovery probe")
    return {"device": device, "phase": mode, "tests": 1, "failures": 0, "skipped": 0}


def stop_owned_server(server: subprocess.Popen | None) -> None:
    if server is None or server.poll() is not None:
        return
    try:
        os.killpg(server.pid, signal.SIGTERM)
    except ProcessLookupError:
        return
    try:
        server.wait(timeout=15)
    except subprocess.TimeoutExpired:
        try:
            os.killpg(server.pid, signal.SIGKILL)
        except ProcessLookupError:
            pass
        server.wait(timeout=15)


def interrupted(_signum, _frame) -> None:
    raise InterruptedError("acceptance harness interrupted")


def main() -> int:
    os.umask(0o077)
    signal.signal(signal.SIGTERM, interrupted)
    signal.signal(signal.SIGINT, interrupted)
    # Fail before starting Gradle or the server when the verifier venv has not been prepared.
    verifier = load_passkey_verifier()
    work_dir = Path(tempfile.mkdtemp(prefix="logdate-recovery-acceptance-"))
    os.chmod(work_dir, 0o700)
    asset_dir = work_dir / "assets"
    asset_dir.mkdir(mode=0o700)
    blob_dir = work_dir / "blobs"
    blob_dir.mkdir(mode=0o700)
    server_log = work_dir / "server.log"
    device_log = work_dir / "managed-devices.log"
    private_file(server_log)
    private_file(device_log)
    server = None
    account_token = None
    passkey_state = None
    account_deleted = False
    device_results = []
    started_at = datetime.datetime.now(datetime.timezone.utc).isoformat()
    result = 1
    try:
        port = free_port()
        host_base = f"http://127.0.0.1:{port}"
        emulator_base = f"http://10.0.2.2:{port}"
        health_token = secrets.token_urlsafe(24)
        server_environment = os.environ.copy()
        server_environment.update(database_environment())
        server_environment.update({
            "LOGDATE_ENV": "development", "HOST": "127.0.0.1", "PORT": str(port),
            "HEALTH_INTERNAL_TOKEN": health_token,
            "ATPROTO_PDS_SERVICE_URL": emulator_base,
            "ATPROTO_OAUTH_ISSUER": "https://logdate.app",
            "ATPROTO_HOSTED_DID_METHOD": "web",
            "LOGDATE_BLOB_STORAGE_DIR": str(blob_dir),
            "WEBAUTHN_STRICT_VERIFICATION": "true",
            "WEBAUTHN_RP_ID": "logdate.app",
            "WEBAUTHN_ORIGIN": "https://logdate.app",
            "BILLING_PROVIDER": "disabled",
            "ENCRYPTION_MODE": "AT_REST_ONLY",
            "SERVER_ENCRYPTION_ENABLED": "true",
            "SERVER_ENCRYPTION_KEY": base64.b64encode(secrets.token_bytes(32)).decode(),
            "SERVER_ENCRYPTION_KEY_ID": "disposable-recovery-fixture",
        })
        if run_gradle(
            [str(ROOT / "gradlew"), ":server:installDist", "--no-daemon",
             "--no-configuration-cache", "--max-workers=1", "--console=plain",
             "-Dorg.gradle.jvmargs=-Xmx3g -XX:MaxMetaspaceSize=768m -Dfile.encoding=UTF-8",
             "-Pkotlin.compiler.execution.strategy=in-process"],
            server_environment, server_log,
        ) != 0:
            raise RuntimeError("server distribution build failed")
        server_executable = ROOT / "server/build/install/server/bin/server"
        if not server_executable.is_file() or not os.access(server_executable, os.X_OK):
            raise RuntimeError("server distribution executable missing")
        with server_log.open("ab") as output:
            server = subprocess.Popen(
                [str(server_executable)], cwd=ROOT, env=server_environment,
                stdout=output, stderr=subprocess.STDOUT, start_new_session=True,
            )
        wait_for_server(host_base, health_token, server)

        username = f"recovery_probe_{uuid.uuid4().hex[:12]}"
        with server_log.open("a") as output, contextlib.redirect_stdout(output), contextlib.redirect_stderr(output):
            passkey_state = verifier.signup(
                verifier.requests.Session(), host_base, "https://logdate.app", "logdate.app",
                username, "Disposable recovery acceptance",
            )
        access_token, refresh_token, account_id, _ = verifier.bound_auth_values(
            passkey_state["auth"], passkey_state
        )
        account_token = access_token
        credential_path = work_dir / "passkey-credential.json"
        with server_log.open("a") as output, contextlib.redirect_stdout(output), contextlib.redirect_stderr(output):
            verifier.save_credential(
                credential_path, host_base, "https://logdate.app", "logdate.app", passkey_state
            )
        fixture = {
            "serverOrigin": emulator_base,
            "accessToken": access_token,
            "refreshToken": refresh_token,
            "accountId": account_id,
            "recoveryWords": recovery_words(),
            "marker": uuid.uuid4().hex[:12],
            "ids": {name: str(uuid.uuid4()) for name in ID_NAMES},
        }
        private_file(asset_dir / "recovery-acceptance.json", json.dumps(fixture).encode())
        print("Disposable PostgreSQL-backed server and account ready; running phone create probe.", flush=True)
        device_results.append(run_managed_device("flagshipPhoneApi36", "create", emulator_base, asset_dir, device_log))
        print("Phone create probe passed; running fresh tablet recovery probe.", flush=True)
        device_results.append(run_managed_device("largeScreenTabletApi35", "read", emulator_base, asset_dir, device_log))
        print("Tablet recovery, media integrity, deletion, and offline probes passed.", flush=True)
        result = 0
    except Exception as error:
        print(f"Acceptance probe failed ({type(error).__name__}); see private task logs.", file=sys.stderr)
    finally:
        if account_token and server and server.poll() is None:
            try:
                if verifier is not None and passkey_state is not None:
                    try:
                        with server_log.open("a") as output, contextlib.redirect_stdout(output), contextlib.redirect_stderr(output):
                            fresh_auth = verifier.signin(
                                verifier.requests.Session(), host_base, "https://logdate.app",
                                "logdate.app", passkey_state,
                            )
                        account_token = verifier.bound_auth_values(fresh_auth, passkey_state)[0]
                    except Exception:
                        # A failed fresh sign-in must not suppress deletion with the original token.
                        pass
                status, _ = request(f"{host_base}/api/v1/auth/me", token=account_token, method="DELETE")
                if status in (204, 404):
                    try:
                        request(f"{host_base}/api/v1/auth/me", token=account_token)
                    except urllib.error.HTTPError as after_delete:
                        account_deleted = after_delete.code in (401, 404)
            except urllib.error.HTTPError as deletion_error:
                account_deleted = deletion_error.code == 404
            except (OSError, urllib.error.URLError):
                pass
        stop_owned_server(server)
        # The private asset contains credentials and is copied into instrumentation build output.
        # Delete the task's packaged androidTest outputs before removing the source asset.
        android_build = ROOT / "app/android-main/build"
        for relative in (
            "outputs/apk/androidTest", "intermediates/merged_assets/debugAndroidTest",
            "intermediates/packaged_assets/debugAndroidTest",
        ):
            shutil.rmtree(android_build / relative, ignore_errors=True)
        if account_token and not account_deleted:
            print("Disposable account cleanup was not verified; private fixture retained for recovery.", file=sys.stderr)
            print(f"Private recovery directory: {work_dir}", file=sys.stderr)
            result = 1
        else:
            if result == 0:
                proof_path = Path(tempfile.gettempdir()) / f"logdate-recovery-acceptance-proof-{uuid.uuid4().hex}.json"
                proof = {
                    "startedAt": started_at,
                    "endedAt": datetime.datetime.now(datetime.timezone.utc).isoformat(),
                    "backend": "isolated PostgreSQL-backed local server",
                    "accountDeleted": account_deleted,
                    "testClass": PROBE_CLASS,
                    "managedDevices": device_results,
                }
                private_file(proof_path, json.dumps(proof, indent=2).encode())
                print(f"Sanitized acceptance proof: {proof_path}", flush=True)
            if result == 0:
                shutil.rmtree(work_dir)
            else:
                shutil.rmtree(asset_dir, ignore_errors=True)
                (work_dir / "passkey-credential.json").unlink(missing_ok=True)
                shutil.rmtree(work_dir / "blobs", ignore_errors=True)
                print(f"Private acceptance logs retained: {work_dir}", file=sys.stderr)
    return result


if __name__ == "__main__":
    sys.exit(main())
