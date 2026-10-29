"""Managed acceptance artifacts with explicit entrypoint dependencies."""


def candidate_apk_paths(h, app_override, probe_override, root):
    if (app_override is None) != (probe_override is None):
        raise ValueError("Provide both pinned candidate APK paths or neither")
    if app_override is None:
        return (
            root / "app/android-main/build/outputs/apk/debug/android-main-debug.apk",
            root
            / "app/android-main/build/outputs/apk/androidTest/debug/android-main-debug-androidTest.apk",
        )
    if any(
        not path.is_absolute() or not path.is_file()
        for path in (app_override, probe_override)
    ):
        raise ValueError("Pinned candidate APKs must be existing absolute files")
    return app_override.resolve(), probe_override.resolve()


def pinned_install_arguments(h, app, probe):
    if any(not path.is_absolute() or not path.is_file() for path in (app, probe)):
        raise ValueError(
            "Managed installation requires both existing absolute pinned APK paths"
        )
    return [
        f"-Plogdate.nativeAcceptanceAppApk={app}",
        f"-Plogdate.nativeAcceptanceProbeApk={probe}",
    ]


def initial_installation(h, mode, app, probe, fixture_environment, output):
    """Start upgrades on the released APK and freeze their inputs before managed boot."""
    if mode != "upgrade":
        return app, probe, fixture_environment
    compatibility = h.runpy.run_path(
        str(h.Path(__file__).with_name("native-released-upgrade-acceptance.py"))
    )
    document, artifacts = compatibility["fixture_artifacts"](
        fixture_environment, output
    )
    if (
        h.hashlib.sha256(app.read_bytes()).hexdigest()
        != document["candidateApp"]["sha256"]
    ):
        raise RuntimeError(
            "The selected candidate differs from the released-upgrade fixture"
        )
    frozen_document = dict(document)
    for role, artifact in artifacts.items():
        frozen_document[role] = dict(
            path=str(artifact), sha256=document[role]["sha256"]
        )
    frozen = output / "released-upgrade-fixture.json"
    descriptor = h.os.open(frozen, h.os.O_WRONLY | h.os.O_CREAT | h.os.O_EXCL, 0o600)
    with h.os.fdopen(descriptor, "w") as stream:
        h.json.dump(frozen_document, stream)
    return artifacts["baselineApp"], artifacts["baselineProbe"], frozen


def assemble_candidate_apks(
    h,
    output,
    package,
    *,
    root=None,
    run=None,
    recovery_assets=None,
    app_only=False,
    sdk=None,
):
    if not h.re.fullmatch(
        r"studio\.hypertext\.logdate(?:\.[a-z][a-z0-9_]*)*\.debug", package
    ):
        raise ValueError(
            "Native acceptance requires the matching LogDate debug application ID"
        )
    application_id = package.removesuffix(".debug")
    arguments = ["./gradlew", ":app:android-main:assembleDebug"]
    if not app_only:
        arguments.append(":app:android-main:assembleDebugAndroidTest")
    if recovery_assets is not None:
        if app_only or not recovery_assets.is_dir():
            raise ValueError(
                "Manual recovery requires an existing private acceptance asset directory"
            )
        arguments.append(f"-Plogdate.recoveryAcceptanceAssetsDir={recovery_assets}")
    if application_id != "studio.hypertext.logdate":
        arguments.append(f"-Plogdate.applicationId={application_id}")
    arguments.extend(("--no-daemon", "--max-workers=2", "--console=plain"))
    output.mkdir(mode=0o700, parents=True, exist_ok=True)
    with (output / "candidate-build.log").open("wb") as log:
        environment = (
            dict(h.os.environ, ANDROID_HOME=str(sdk), ANDROID_SDK_ROOT=str(sdk))
            if sdk is not None
            else None
        )
        run(
            arguments,
            cwd=root,
            env=environment,
            stdout=log,
            stderr=h.subprocess.STDOUT,
            check=True,
            timeout=1800,
        )


def android_signing_origin(h, sdk, apk, *, execute=None):
    execute = execute or h.command
    versions = sorted((sdk / "build-tools").glob("*/apksigner"), reverse=True)
    if not versions:
        raise RuntimeError("Android SDK has no apksigner for the selected APK")
    result = execute(str(versions[0]), "verify", "--print-certs", str(apk))
    digests = h.re.findall(
        rb"(?m)^Signer #\d+ certificate SHA-256 digest: ([0-9a-fA-F]{64})$",
        result.stdout,
    )
    if len(digests) != 1:
        raise RuntimeError("The selected APK must have exactly one signer")
    return "android:apk-key-hash:" + h.base64.urlsafe_b64encode(
        bytes.fromhex(digests[0].decode())
    ).decode().rstrip("=")


def verify_pinned_packages(h, sdk, app, probe, package, *, execute=None):
    execute = execute or h.command
    versions = sorted((sdk / "build-tools").glob("*/aapt2"), reverse=True)
    if not versions:
        raise RuntimeError("Android SDK has no aapt2 to verify pinned APK identities")
    for apk, expected in ((app, package), (probe, f"{package}.test")):
        actual = (
            execute(str(versions[0]), "dump", "packagename", str(apk))
            .stdout.decode()
            .strip()
        )
        if actual != expected:
            raise RuntimeError(
                f"Pinned {apk.name} has application ID {actual!r}; expected {expected!r}"
            )


def start_manual_fixture(h, options, output, sdk, package):
    if any(
        path is None or not path.is_file()
        for path in (options.classpath, options.fixture_environment)
    ):
        raise ValueError(
            "Manual account recovery requires a private server classpath and fixture environment"
        )
    h.assemble_candidate_apks(output, package, app_only=True, sdk=sdk)
    candidate = (
        h.ROOT / "app/android-main/build/outputs/apk/debug/android-main-debug.apk"
    )
    if not candidate.is_file():
        raise RuntimeError("The candidate application APK was not built")
    origin = h.android_signing_origin(sdk, candidate)
    fixture = output / "manual-account-fixture"
    if fixture.exists():
        raise RuntimeError(
            "Manual account recovery requires a fresh private fixture directory"
        )
    arguments = [
        h.sys.executable,
        str(h.ROOT / "tests/e2e/manual-account-recovery-fixture.py"),
        "--classpath",
        str(options.classpath),
        "--fixture-environment",
        str(options.fixture_environment),
        "--android-origin",
        origin,
        "--output",
        str(fixture),
        "--lifetime",
        "3600",
    ]
    with (output / "manual-account-fixture.log").open("wb") as log:
        process = h.subprocess.Popen(
            arguments, cwd=h.ROOT, stdout=log, stderr=h.subprocess.STDOUT
        )
    deadline = h.time.monotonic() + 150
    while not (fixture / "fixture-ready").is_file():
        if process.poll() is not None or h.time.monotonic() >= deadline:
            if process.poll() is None:
                process.terminate()
                process.wait(timeout=15)
            raise RuntimeError(
                "The private manual recovery fixture did not become ready; inspect manual-account-fixture.log"
            )
        h.time.sleep(0.1)
    return process, fixture, origin
