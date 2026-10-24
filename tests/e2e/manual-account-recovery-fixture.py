#!/usr/bin/env python3
"""Create a bounded local synthetic account/source asset with the existing native server harness."""

import argparse
import json
import os
from pathlib import Path
import re
import runpy
import signal
import stat
import socket
import subprocess
import time
from urllib.parse import urlsplit
import uuid

ROOT = Path(__file__).resolve().parents[2]
NATIVE = runpy.run_path(str(Path(__file__).with_name("native-device-enrollment-acceptance.py")))
RECOVERY = runpy.run_path(str(Path(__file__).with_name("recovery-acceptance.py")))


def source_asset(fixture):
    owner = str(uuid.UUID(fixture["owner"]))
    origin = fixture["origin"]
    parsed = urlsplit(origin)
    if not (parsed.scheme == "http" and parsed.hostname == "10.0.2.2" and parsed.port
            and parsed.username is None and parsed.password is None and not parsed.path
            and not parsed.query and not parsed.fragment):
        raise ValueError("Only a task-owned loopback fixture is allowed")
    for key in ("accessToken", "refreshToken"):
        if not isinstance(fixture[key], str) or not fixture[key]:
            raise ValueError("The local server did not issue complete fixture credentials")
    return {
        "serverOrigin": origin,
        "accessToken": fixture["accessToken"],
        "refreshToken": fixture["refreshToken"],
        "accountId": owner,
        "recoveryWords": RECOVERY["recovery_words"](),
        "marker": uuid.uuid4().hex[:12],
        "ids": {name: str(uuid.uuid4()) for name in RECOVERY["ID_NAMES"]},
    }


def publish_assets(control, assets):
    fixture_file = control / "fixture.json"
    if fixture_file.is_symlink() or stat.S_IMODE(fixture_file.stat().st_mode) != 0o600 or fixture_file.stat().st_size > 32_768:
        raise ValueError("A bounded private server fixture is required")
    fixture = json.loads(fixture_file.read_text())
    asset = source_asset(fixture)
    assets.mkdir(mode=0o700)
    RECOVERY["private_file"](assets / "recovery-acceptance.json", json.dumps(asset).encode())
    return asset


def serve(options):
    if not re.fullmatch(r"android:apk-key-hash:[A-Za-z0-9_-]{43}", options.android_origin):
        raise ValueError("Supply the actual selected debug APK's public WebAuthn signing origin")
    if not 60 <= options.lifetime <= 3600:
        raise ValueError("The fixture requires a finite local lifetime")
    classpath = options.classpath.read_text().strip()
    if not classpath:
        raise ValueError("The existing native server classpath is empty")
    environment = dict(os.environ)
    environment.update(NATIVE["fixture_environment"](options.fixture_environment))
    environment.update(
        LOGDATE_ENV="development", LOGDATE_OAUTH_PASSKEY_SIGN_IN_ENABLED="true",
        WEBAUTHN_STRICT_VERIFICATION="true", BILLING_PROVIDER="disabled",
        WEBAUTHN_ALLOWED_ORIGINS="https://10.0.2.2," + options.android_origin,
        JAVA_TOOL_OPTIONS="-Djava.awt.headless=true",
    )
    with socket.socket() as reservation:
        reservation.bind(("127.0.0.1", 0))
        port = reservation.getsockname()[1]
    environment = NATIVE["server_environment"](environment, port)
    options.output.mkdir(mode=0o700)
    control = options.output / "server-control"
    control.mkdir(mode=0o700)
    started = time.monotonic()
    deadline = started + options.lifetime
    process = None
    try:
        log_path = options.output / "server.log"
        RECOVERY["private_file"](log_path)
        with log_path.open("ab") as log:
            process = subprocess.Popen(
                ["java", "-Xmx1g", "-Djava.awt.headless=true", "-cp", classpath,
                 "app.logdate.integration.e2e.harness.NativeDeviceEnrollmentHarness",
                 str(control), str(port), str(options.lifetime)],
                cwd=ROOT, env=environment, stdout=log, stderr=subprocess.STDOUT,
            )
        while not (control / "ready").is_file():
            if process.poll() is not None or time.monotonic() >= min(deadline, started + 120):
                raise RuntimeError("The owned local native fixture did not become ready; inspect its private log")
            time.sleep(0.1)
        publish_assets(control, options.output / "assets")
        RECOVERY["private_file"](options.output / "fixture-ready", b"ready")
        print("Automatic local account and source asset ready", flush=True)
        while process.poll() is None:
            if time.monotonic() >= deadline:
                raise RuntimeError("The local manual recovery fixture reached its lifetime limit")
            time.sleep(0.1)
        if process.returncode != 0 or not (control / "complete").is_file():
            raise RuntimeError("The local fixture stopped before its owned acceptance completed")
    finally:
        (options.output / "fixture-ready").unlink(missing_ok=True)
        if process is not None and process.poll() is None:
            process.terminate()
            try:
                process.wait(timeout=15)
            except subprocess.TimeoutExpired:
                process.kill()
                process.wait(timeout=5)


def interrupted(_signal, _frame):
    raise InterruptedError("Local fixture stopped by its owner")


if __name__ == "__main__":
    os.umask(0o077)
    signal.signal(signal.SIGTERM, interrupted)
    signal.signal(signal.SIGINT, interrupted)
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--classpath", required=True, type=Path)
    parser.add_argument("--fixture-environment", required=True, type=Path)
    parser.add_argument("--android-origin", required=True)
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--lifetime", type=int, default=1200)
    arguments = parser.parse_args()
    serve(arguments)
