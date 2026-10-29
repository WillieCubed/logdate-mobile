import argparse
import ast
from contextlib import redirect_stderr
import hashlib
import io
from pathlib import Path
import subprocess
import tarfile
import tempfile
import time
import unittest
from unittest.mock import call, patch
from native_backup_test_support import (
    SCRIPT,
    NATIVE_BACKUP_ACCEPTANCE,
    NATIVE_RESTORE_WORK_ACCEPTANCE,
    D2D_SCRIPT,
    BackupPayloadFixture,
)


class CredentialsOnlyBackupFilterTest(BackupPayloadFixture, unittest.TestCase):
    def test_credentials_only_prepares_an_encrypted_cloud_transport(self):
        harness = NATIVE_BACKUP_ACCEPTANCE
        calls = []

        def adb(serial, *arguments, **kwargs):
            calls.append(arguments)
            if arguments == ("shell", "getprop", "ro.kernel.qemu"):
                return b"1"
            if arguments == ("shell", "id", "-u"):
                return b"0"
            return b""

        with (
            patch.object(harness, "adb", side_effect=adb),
            patch.object(harness, "wait_for_package_manager"),
            patch.object(harness, "ensure_emulator_setup_complete"),
            patch.object(harness, "select_local_transport"),
        ):
            harness.prepare("emulator-5554", cloud_backup=True)

        self.assertIn(
            (
                "shell",
                "settings",
                "put",
                "secure",
                "backup_local_transport_parameters",
                "is_encrypted=true,is_device_transfer=false",
            ),
            calls,
        )

    def test_cloud_source_archive_requires_journal_and_live_recovery_key(self):
        harness = NATIVE_BACKUP_ACCEPTANCE
        prefix = f"apps/{harness.PACKAGE}"
        entries = [
            (prefix + "/_manifest", b"manifest"),
            (prefix + "/db/logdate", b"encrypted journal"),
            (prefix + "/f/db_recovery/passphrase_backup", b"cloud key"),
            (prefix + "/f/logdate.preferences_pb", b"owner preferences"),
            (prefix + "/f/identity_recovery/phrase_backup", b"identity key"),
            (prefix + "/f/identity_recovery/media_key_backup", b"media key"),
        ]
        full_backup_name = f"1/_full/{harness.PACKAGE}"

        def outer(items):
            return self.make_tar(
                [(full_backup_name, self.make_tar(items)), ("1/@pm@", b"metadata")]
            )

        harness.assert_cloud_backup_contents(outer(entries))
        for missing in (entries[1][0], entries[2][0]):
            with (
                self.subTest(missing=missing),
                self.assertRaisesRegex(RuntimeError, "cloud backup"),
            ):
                harness.assert_cloud_backup_contents(
                    outer([item for item in entries if item[0] != missing])
                )
        for forbidden in (
            prefix + "/f/native_journal_transfer/current/database/logdate",
            prefix + "/f/media/objects/original.jpg",
        ):
            with (
                self.subTest(forbidden=forbidden),
                self.assertRaisesRegex(RuntimeError, "cloud backup included"),
            ):
                harness.assert_cloud_backup_contents(
                    outer([*entries, (forbidden, b"must not be cloud backed up")])
                )

    def test_credentials_only_preserves_one_recovery_directory_when_source_orders_it_after_key(
        self,
    ):
        harness = NATIVE_BACKUP_ACCEPTANCE
        prefix = f"apps/{harness.PACKAGE}"
        full_backup = self.make_tar(
            [
                (prefix + "/_manifest", b"manifest"),
                (prefix + "/f/db_recovery/passphrase_backup", b"db-key"),
                (prefix + "/f/db_recovery/", b""),
                (prefix + "/f/identity_recovery/phrase_backup", b"identity-key"),
                (prefix + "/f/identity_recovery/media_key_backup", b"media-key"),
                (
                    prefix + "/f/logdate.preferences_pb",
                    harness.length_delimited_field(
                        1,
                        self.preference_entry(
                            "identity.canonical_owner_id", "native-test-owner"
                        ),
                    ),
                ),
            ]
        )

        filtered = harness.credentials_only_from_full_backup(full_backup)

        with tarfile.open(fileobj=io.BytesIO(filtered), mode="r:") as archive:
            directories = [
                member
                for member in archive.getmembers()
                if member.isdir()
                and member.name.rstrip("/") == prefix + "/f/db_recovery"
            ]
        self.assertEqual(1, len(directories))

    def test_credentials_only_outer_transport_retains_android_metadata(self):
        harness = NATIVE_BACKUP_ACCEPTANCE
        prefix = f"apps/{harness.PACKAGE}"
        full_backup = self.make_tar(
            [
                (prefix + "/_manifest", b"manifest"),
                (prefix + "/f/db_recovery/passphrase_backup", b"db-key"),
                (prefix + "/f/identity_recovery/phrase_backup", b"identity-key"),
                (prefix + "/f/identity_recovery/media_key_backup", b"media-key"),
                (
                    prefix + "/f/logdate.preferences_pb",
                    harness.length_delimited_field(
                        1,
                        self.preference_entry(
                            "identity.canonical_owner_id", "native-test-owner"
                        ),
                    ),
                ),
                (prefix + "/db/logdate", b"encrypted journal"),
            ]
        )
        outer = self.make_tar(
            [(f"1/_full/{harness.PACKAGE}", full_backup), ("1/@pm@", b"metadata")]
        )

        filtered = harness.credentials_only(outer)

        with tarfile.open(fileobj=io.BytesIO(filtered), mode="r:") as transport:
            self.assertEqual(b"metadata", transport.extractfile("1/@pm@").read())
            with tarfile.open(
                fileobj=io.BytesIO(
                    transport.extractfile(f"1/_full/{harness.PACKAGE}").read()
                ),
                mode="r:",
            ) as app_backup:
                names = {member.name for member in app_backup.getmembers()}
                self.assertIn(prefix + "/f/db_recovery/passphrase_backup", names)
                self.assertNotIn(prefix + "/db/logdate", names)

    def test_preserves_android_manifest_directories_and_only_portable_credentials(self):
        package = NATIVE_BACKUP_ACCEPTANCE.PACKAGE
        prefix = f"apps/{package}"
        entries = [
            (prefix + "/_manifest", b"manifest"),
            (prefix + "/f/", b""),
            (prefix + "/f/db_recovery/", b""),
            (prefix + "/f/identity_recovery/", b""),
            (prefix + "/f/media/", b""),
            (prefix + "/f/db_recovery/passphrase_backup", b"db-key"),
            (prefix + "/db/logdate", b"encrypted journal"),
            (prefix + "/f/identity_recovery/phrase_backup", b"identity-key"),
            (prefix + "/f/identity_recovery/media_key_backup", b"media-key"),
            (
                prefix + "/f/logdate.preferences_pb",
                b"".join(
                    NATIVE_BACKUP_ACCEPTANCE.length_delimited_field(
                        1, self.preference_entry(name, value)
                    )
                    for name, value in [
                        ("identity.canonical_owner_id", "native-test-owner"),
                        ("identity.canonical_owner_bound", "true"),
                        ("editor_drafts_anonymous", '{"drafts":["unfinished"]}'),
                    ]
                ),
            ),
            (prefix + "/f/media/photo.png", b"original"),
        ]
        backup = self.make_tar(entries)

        filtered = NATIVE_BACKUP_ACCEPTANCE.credentials_only_from_full_backup(backup)

        with tarfile.open(fileobj=io.BytesIO(filtered), mode="r:") as archive:
            members = {member.name: member for member in archive.getmembers()}
            preferences_data = archive.extractfile(
                prefix + "/f/logdate.preferences_pb"
            ).read()
            database_key = archive.extractfile(
                prefix + "/f/db_recovery/passphrase_backup"
            ).read()
        self.assertIn(prefix + "/_manifest", members)
        self.assertTrue(members[prefix + "/f/db_recovery"].isdir())
        self.assertTrue(members[prefix + "/f/identity_recovery"].isdir())
        self.assertIn(prefix + "/f/db_recovery/passphrase_backup", members)
        self.assertEqual(b"db-key", database_key)
        self.assertNotIn(
            prefix
            + "/f/native_journal_transfer/current/files/db_recovery/passphrase_backup",
            members,
        )
        self.assertIn(prefix + "/f/identity_recovery/phrase_backup", members)
        self.assertIn(prefix + "/f/identity_recovery/media_key_backup", members)
        self.assertIn(prefix + "/f/logdate.preferences_pb", members)
        self.assertNotIn(prefix + "/f/media/photo.png", members)
        self.assertNotIn(prefix + "/db/logdate", members)
        preferences = NATIVE_BACKUP_ACCEPTANCE.canonical_preferences(preferences_data)
        names = set()
        for _, _, _, entry in NATIVE_BACKUP_ACCEPTANCE.protobuf_fields(preferences):
            fields = list(NATIVE_BACKUP_ACCEPTANCE.protobuf_fields(entry))
            names.add(
                next(
                    payload
                    for number, wire, _, payload in fields
                    if number == 1 and wire == 2
                ).decode()
            )
        self.assertEqual(
            {"identity.canonical_owner_id", "identity.canonical_owner_bound"}, names
        )

    def test_rejects_credentials_only_archive_without_android_manifest(self):
        package = NATIVE_BACKUP_ACCEPTANCE.PACKAGE
        prefix = f"apps/{package}"
        backup = self.make_tar(
            [
                (prefix + "/f/db_recovery/passphrase_backup", b"db-key"),
                (prefix + "/f/identity_recovery/phrase_backup", b"identity-key"),
                (prefix + "/f/identity_recovery/media_key_backup", b"media-key"),
                (
                    prefix + "/f/logdate.preferences_pb",
                    NATIVE_BACKUP_ACCEPTANCE.length_delimited_field(
                        1,
                        self.preference_entry(
                            "identity.canonical_owner_id", "native-test-owner"
                        ),
                    ),
                ),
            ]
        )

        with self.assertRaisesRegex(
            RuntimeError, "missing Android's app backup manifest"
        ):
            NATIVE_BACKUP_ACCEPTANCE.credentials_only_from_full_backup(backup)

    def test_rejects_credentials_only_archive_without_cloud_database_key(self):
        package = NATIVE_BACKUP_ACCEPTANCE.PACKAGE
        prefix = f"apps/{package}"
        backup = self.make_tar(
            [
                (prefix + "/_manifest", b"manifest"),
                (
                    prefix
                    + "/f/native_journal_transfer/current/files/db_recovery/passphrase_backup",
                    b"selected D2D key",
                ),
                (prefix + "/f/identity_recovery/phrase_backup", b"identity-key"),
                (prefix + "/f/identity_recovery/media_key_backup", b"media-key"),
                (
                    prefix + "/f/logdate.preferences_pb",
                    NATIVE_BACKUP_ACCEPTANCE.length_delimited_field(
                        1,
                        self.preference_entry(
                            "identity.canonical_owner_id", "native-test-owner"
                        ),
                    ),
                ),
            ]
        )

        with self.assertRaisesRegex(RuntimeError, "portable recovery keys"):
            NATIVE_BACKUP_ACCEPTANCE.credentials_only_from_full_backup(backup)
