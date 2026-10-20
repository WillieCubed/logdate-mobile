#!/usr/bin/env python3
"""Verify native transfer, replacement trust and encrypted publication on isolated emulators.

The production worker uses a synthetic session and capture transport. This does not
test hosted uploads, Google credential delivery or setup-wizard migration.
"""
import argparse
import importlib.util
import json
import os
from pathlib import Path
import shlex
import uuid

SPEC = importlib.util.spec_from_file_location("native_backup", Path(__file__).with_name("native-backup-acceptance.py"))
native = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(native)


def probe(serial, stage, fixture, output, metadata=None):
    args = ["am", "instrument", "--user", "0", "-w", "-r", "-e", "class",
            "app.logdate.client.e2e.NativeTransferredPublicationProbeTest",
            "-e", "nativePublicationAcceptance", "true",
            "-e", "publicationFixture", fixture, "-e", "publicationStage", stage]
    if metadata is not None:
        args.extend(["-e", "sourcePublication", json.dumps(metadata)])
    args.append(native.RUNNER)
    result = native.adb(serial, "shell", shlex.join(args))
    native.private_file(output, result)
    if not native.instrumentation_passed(result):
        raise RuntimeError(f"native publication {stage} failed; inspect {output}")


def verify_restarted_publication(serial, fixture, metadata, output):
    probe(serial, "verify", fixture, output / "publication-verify.log", metadata)
    native.adb(serial, "shell", "am", "force-stop", native.PACKAGE)
    probe(serial, "replay", fixture, output / "publication-replay.log", metadata)


def run(options):
    native.prepare(options.source, allow_existing=options.resume_seeded_source)
    native.prepare(options.destination, allow_preinstalled_empty=True)
    if options.resume_seeded_source:
        for apk in (options.app_apk, options.test_apk):
            native.adb(options.source, "install", "-r", "-t", str(apk))
    else:
        native.prepare_source(options.source, options.app_apk, options.test_apk, options.output, "phrase")
    fixture = str(uuid.uuid4())
    native.private_file(options.output / "publication-fixture-id", fixture.encode())
    primary_error = None
    try:
        probe(options.source, "seed", fixture, options.output / "publication-seed.log")
        raw = native.adb(options.source, "exec-out", "run-as", native.PACKAGE, "cat",
                         f"no_backup/native-publication-{fixture}.json")
        native.private_file(options.output / "source-publication.json", raw)
        metadata = json.loads(raw)
        native.transfer(options.source, options.destination, options.app_apk, options.test_apk,
                        options.output, "phrase", seed_source=False, exercise_data_only=True)
        verify_restarted_publication(options.destination, fixture, metadata, options.output)
        native.private_file(options.output / "publication-acceptance.json", json.dumps({
            "nativeTransfer": True, "replacementTrust": True, "restartReplay": True,
            "replacementDevicePublication": True, "originalMediaBytes": True,
            "hostedUpload": False, "googleProviderAcceptance": False,
        }, indent=2).encode())
    except BaseException as error:
        primary_error = error
        raise
    finally:
        try:
            probe(options.source, "cleanup", fixture, options.output / "publication-source-cleanup.log")
        except Exception as error:
            if primary_error is None:
                raise
            primary_error.add_note(f"Source fixture cleanup also failed: {error}")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source", required=True, type=native.emulator_serial)
    parser.add_argument("--destination", required=True, type=native.emulator_serial)
    parser.add_argument("--app-apk", required=True, type=Path)
    parser.add_argument("--test-apk", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--resume-seeded-source", action="store_true")
    options = parser.parse_args()
    if options.source == options.destination:
        parser.error("source and destination must be different emulators")
    if not options.app_apk.is_file() or not options.test_apk.is_file():
        parser.error("both APKs must exist")
    options.app_apk = options.app_apk.resolve()
    options.test_apk = options.test_apk.resolve()
    os.umask(0o077)
    options.output.mkdir(mode=0o700, parents=True, exist_ok=False)
    run(options)


if __name__ == "__main__":
    main()
