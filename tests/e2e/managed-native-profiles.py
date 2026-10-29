"""Managed acceptance profiles with explicit entrypoint dependencies."""


def acceptance_receipt(h, mode, native_arguments, followups):
    cloud_selection = mode == "transfer" and any(
        choice in native_arguments
        for choice in ("--exercise-cloud-backup", "--exercise-credentials-only")
    )
    return {
        "managedDeviceLifecycleAccepted": True,
        "outerDrivers": 2,
        "mode": mode,
        "managedNativeTransport": mode
        not in {"work-restart", "network-policy", "late-media", "manual-account"},
        "crossDeviceTransferAccepted": mode
        in {"transfer", "publication", "enrollment", "upgrade", "widget"}
        and not cloud_selection,
        "localCloudSelectionAccepted": cloud_selection
        and "--exercise-cloud-backup" in native_arguments,
        "nativeArguments": native_arguments,
        "followups": followups,
        "manualAccountRecoveryAccepted": mode == "manual-account",
        "googleRestoreProviderAccepted": False,
    }


def acceptance_budget(h, followups, *, mode="transfer"):
    budget = {
        "boot": 600,
        "native": 1800 if mode == "enrollment" else 1200,
        "followup": 600,
        "teardown": 120,
    }
    budget["driverMillis"] = 1000 * (
        budget["boot"]
        + budget["native"]
        + followups * budget["followup"]
        + budget["teardown"]
    )
    if budget["driverMillis"] > 90 * 60_000:
        raise ValueError(
            "The combined profile exceeds the bounded managed lifetime; split its follow-ups"
        )
    return budget


def validate_followups(h, mode, followups):
    if len(followups) != len(set(followups)):
        raise ValueError("Each follow-up must run once per managed generation")
    if mode == "upgrade" and followups:
        raise ValueError(
            "Released upgrades retain their candidate APK; run other profiles in a separate managed generation"
        )


def followup_command(h, name, destination, app, probe, output):
    if name not in h.FOLLOWUPS:
        raise ValueError("Choose an existing native recovery acceptance harness")
    return [
        h.sys.executable,
        str(h.ROOT / "tests/e2e" / h.FOLLOWUPS[name]),
        "--emulator",
        destination,
        "--app-apk",
        str(app),
        "--test-apk",
        str(probe),
        "--output",
        str(output),
        h.SYNTHETIC_DATA_ARGUMENT,
    ]


def scenario_command(
    h,
    mode,
    source,
    destination,
    app,
    probe,
    output,
    arguments,
    *,
    classpath=None,
    fixture_environment=None,
    fixture_control=None,
):
    h.validate_native_arguments(arguments)
    if mode not in {
        "transfer",
        "publication",
        "work-restart",
        "d2d",
        "enrollment",
        "upgrade",
        "network-policy",
        "widget",
        "late-media",
        "manual-account",
    } or (mode != "transfer" and arguments):
        raise ValueError("Only transfer mode accepts native scenario arguments")
    if mode == "manual-account":
        if fixture_control is None:
            raise ValueError("Manual account recovery requires its owned local fixture")
        return [
            h.sys.executable,
            str(h.ROOT / "tests/e2e/manual-account-recovery-acceptance.py"),
            "--source",
            source,
            "--destination",
            destination,
            "--output",
            str(output),
            "--fixture-control",
            str(fixture_control),
            h.SYNTHETIC_DATA_ARGUMENT,
            "--focused",
        ]
    filenames = h.SCENARIO_FILES
    command = [h.sys.executable, str(h.ROOT / "tests/e2e" / filenames[mode])]
    if mode in {"work-restart", "d2d", "network-policy"}:
        command += ["--emulator", source]
    else:
        command += ["--source", source, "--destination", destination]
    command += [
        "--app-apk",
        str(app),
        "--test-apk",
        str(probe),
        "--output",
        str(output),
    ]
    if mode not in {"d2d", "network-policy"}:
        command += ["--seed-empty-source"]
    if mode == "network-policy":
        command += [
            "--emulator",
            destination,
            "--network-only",
            h.SYNTHETIC_DATA_ARGUMENT,
        ]
    if mode == "enrollment":
        if classpath is None or fixture_environment is None:
            raise ValueError(
                "Enrollment requires an existing private database fixture and server classpath"
            )
        command += [
            "--classpath",
            str(classpath),
            "--fixture-environment",
            str(fixture_environment),
        ]
    if mode == "upgrade":
        if fixture_environment is None:
            raise ValueError("Released upgrade requires a private artifact fixture")
        command += ["--fixture-environment", str(fixture_environment)]
    return command + arguments
