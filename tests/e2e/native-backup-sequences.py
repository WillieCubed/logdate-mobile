"""Native backup sequences; dependencies are supplied by the entry point."""

import argparse
from pathlib import Path
import re


def replay_destination_trust(
    h,
    serial,
    output,
    metadata,
    identity,
    *,
    system_widgets=False,
    recording_transport="cloud",
):
    h.adb(serial, "shell", "am", "force-stop", h.PACKAGE)
    replay = h.restore_transport(serial, system_widgets=system_widgets)
    h.private_file(output / "native-restore-after-negotiation.log", replay)
    if b"restoreFinished: 0" not in replay:
        raise RuntimeError(
            "Android did not complete the post-negotiation native restore"
        )
    if system_widgets:
        h.capture_widget_restore_delivery(
            serial, output / "widget-delivery-after-negotiation"
        )
    h.probe(
        serial,
        "verify-restored",
        output / "destination-trust-after-replay.log",
        metadata,
        identity,
        recording_transport=recording_transport,
    )


def verify_quota(h, source, output):
    parameters = (
        h.adb(
            source,
            "shell",
            "settings",
            "get",
            "secure",
            "backup_local_transport_parameters",
        )
        .decode()
        .strip()
    )
    if not parameters or parameters == "null":
        raise RuntimeError(
            "Could not read the original native backup transport settings"
        )
    h.probe(source, "quota-cleanup", output / "source-quota-cleanup-before.log")
    try:
        h.probe(source, "quota", output / "source-quota-seed.log")
        h.adb(
            source,
            "shell",
            "settings",
            "put",
            "secure",
            "backup_local_transport_parameters",
            "is_encrypted=true,is_device_transfer=false",
        )
        result = h.adb(source, "shell", "bmgr", "backupnow", h.PACKAGE)
        h.private_file(output / "source-quota-backup.log", result)
        if b"Size quota exceeded" not in result:
            raise RuntimeError("Android did not reject the oversized cloud backup")
        record = h.adb(
            source,
            "shell",
            "run-as",
            h.PACKAGE,
            "cat",
            "no_backup/.logdate_native_backup_quota_exceeded",
        ).strip()
        if not re.fullmatch(rb"[0-9]+:[0-9]+", record):
            raise RuntimeError(
                "native quota callback did not persist a valid failure record"
            )
        size, quota = map(int, record.split(b":"))
        if size <= quota:
            raise RuntimeError("quota callback did not report an oversized payload")
        h.private_file(output / "source-quota-callback.txt", record)
        print("Native quota rejection and callback checks passed", flush=True)
    finally:
        try:
            h.adb(
                source,
                "shell",
                "settings",
                "put",
                "secure",
                "backup_local_transport_parameters",
                parameters,
            )
        finally:
            h.probe(source, "quota-cleanup", output / "source-quota-cleanup-after.log")


def main(h):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source", required=True, type=h.emulator_serial)
    parser.add_argument("--destination", required=True, type=h.emulator_serial)
    parser.add_argument("--app-apk", required=True, type=Path)
    parser.add_argument("--test-apk", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument(
        "--identity", choices=("phrase", "account", "legacy-account"), default="phrase"
    )
    parser.add_argument(
        "--exercise-quota",
        action="store_true",
        help="also verify native quota rejection on the synthetic source",
    )
    parser.add_argument(
        "--exercise-archive-recovery",
        action="store_true",
        help="transfer an edited partial archive and recover its originals on the destination",
    )
    parser.add_argument(
        "--exercise-missing-keys",
        action="store_true",
        help="omit portable key files from the native payload and verify safe recovery",
    )
    parser.add_argument(
        "--exercise-missing-privacy",
        action="store_true",
        help="omit privacy metadata, verify protected recovery, then deliver original preferences",
    )
    parser.add_argument(
        "--exercise-cloud-backup",
        action="store_true",
        help="restore the complete encrypted-cloud selection, replay it, and verify offline editing without original media",
    )
    parser.add_argument(
        "--exercise-credentials-only",
        action="store_true",
        help="restore portable recovery keys without journal or media data",
    )
    parser.add_argument(
        "--exercise-data-only",
        action="store_true",
        help="verify journal and media restore without an account session",
    )
    parser.add_argument(
        "--exercise-duplicate-restore",
        action="store_true",
        help="replay native restore before app launch and verify idempotent durable state",
    )
    parser.add_argument(
        "--exercise-partial-media",
        action="store_true",
        help="omit one original from native transport and verify recovery state",
    )
    parser.add_argument(
        "--exercise-pending-camera-draft",
        action="store_true",
        help="transfer and replay a pending camera draft with its selected original",
    )
    parser.add_argument(
        "--resume-seeded-source",
        action="store_true",
        help="resume from a retained synthetic source fixture without clearing or reseeding app data",
    )
    parser.add_argument(
        "--snapshot-existing-source",
        action="store_true",
        help="record a synthetic source's existing restore fixture without replacing journal data",
    )
    parser.add_argument(
        "--seed-empty-source",
        action="store_true",
        help="seed an installed emulator only when it contains no journal database or media files",
    )
    options = parser.parse_args()
    h.validate_options(options, parser)
    if not options.app_apk.is_file() or not options.test_apk.is_file():
        parser.error("both APKs must exist")
    options.output.mkdir(mode=0o700, parents=True, exist_ok=False)
    cloud_selection = (
        options.exercise_credentials_only
        or options.exercise_cloud_backup
        or options.exercise_missing_privacy
    )
    h.prepare(
        options.source,
        allow_existing=options.resume_seeded_source or options.snapshot_existing_source,
        allow_preinstalled_empty=options.seed_empty_source,
        cloud_backup=cloud_selection,
    )
    h.prepare(
        options.destination, allow_preinstalled_empty=True, cloud_backup=cloud_selection
    )
    h.transfer(
        options.source,
        options.destination,
        options.app_apk.resolve(),
        options.test_apk.resolve(),
        options.output.resolve(),
        options.identity,
        options.exercise_archive_recovery,
        options.exercise_missing_keys,
        options.exercise_credentials_only,
        seed_source=not (
            options.resume_seeded_source or options.snapshot_existing_source
        ),
        snapshot_source=options.snapshot_existing_source,
        exercise_duplicate_restore=options.exercise_duplicate_restore,
        exercise_partial_media=options.exercise_partial_media,
        exercise_data_only=options.exercise_data_only,
        exercise_missing_privacy=options.exercise_missing_privacy,
        exercise_cloud_backup=options.exercise_cloud_backup,
        exercise_pending_camera_draft=options.exercise_pending_camera_draft,
    )
    if options.exercise_quota:
        h.verify_quota(options.source, options.output.resolve())


def validate_options(h, options, parser):
    if options.source == options.destination:
        parser.error("source and destination must be different emulators")
    filtered_scenarios = [
        options.exercise_cloud_backup,
        options.exercise_missing_keys,
        options.exercise_credentials_only,
        options.exercise_archive_recovery,
        options.exercise_partial_media,
        options.exercise_missing_privacy,
        options.exercise_pending_camera_draft,
    ]
    if sum(filtered_scenarios) > 1:
        parser.error("choose one restore recovery scenario per destination fixture")
    if options.exercise_data_only and any(filtered_scenarios):
        parser.error("data-only acceptance requires a complete native app-data restore")
    acceptance_scenarios = [
        *filtered_scenarios,
        options.exercise_duplicate_restore,
        options.exercise_data_only,
    ]
    if options.resume_seeded_source and not any(acceptance_scenarios):
        parser.error("resuming a seeded source requires a recovery acceptance scenario")
    if options.resume_seeded_source and options.seed_empty_source:
        parser.error(
            "choose either a retained synthetic source or an empty installed source"
        )
    if options.snapshot_existing_source and (
        options.resume_seeded_source or options.seed_empty_source
    ):
        parser.error(
            "snapshot an existing source separately from resuming a fixture or seeding an empty source"
        )
    if options.snapshot_existing_source and not any(acceptance_scenarios):
        parser.error(
            "snapshotting an existing source requires a recovery acceptance scenario"
        )
    if options.snapshot_existing_source and options.exercise_archive_recovery:
        parser.error(
            "snapshotting an existing source cannot run archive seeding because it mutates the source journal"
        )
    if options.resume_seeded_source and options.exercise_pending_camera_draft:
        parser.error(
            "pending camera acceptance requires a freshly seeded source fixture"
        )
    if options.snapshot_existing_source and options.exercise_pending_camera_draft:
        parser.error(
            "pending camera acceptance requires a freshly seeded source fixture"
        )
    if options.snapshot_existing_source and options.identity == "legacy-account":
        parser.error(
            "legacy-account is a source seeding mode and cannot snapshot an existing source"
        )
    if options.exercise_data_only and options.exercise_credentials_only:
        parser.error(
            "data-only and credentials-only acceptance are distinct restore scenarios"
        )


FUNCTIONS = {
    "replay_destination_trust": replay_destination_trust,
    "verify_quota": verify_quota,
    "main": main,
    "validate_options": validate_options,
}
