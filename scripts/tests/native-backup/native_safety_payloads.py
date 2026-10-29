"""Native acceptance safety contracts for payloads."""

import io
import tarfile

from native_safety_support import HARNESS


class NativePayloadsSafety:
    def test_missing_key_fixture_omits_only_portable_recovery_keys(self):
        full_backup = io.BytesIO()
        long_path = (
            f"apps/{HARNESS.PACKAGE}/f/media/objects/sha256/" + "b" * 90 + "/item.jpg"
        )
        with tarfile.open(
            fileobj=full_backup, mode="w", format=tarfile.USTAR_FORMAT
        ) as archive:
            for name, contents in [
                (
                    f"apps/{HARNESS.PACKAGE}/f/native_journal_transfer/current/files/db_recovery/passphrase_backup",
                    b"database key",
                ),
                (
                    f"apps/{HARNESS.PACKAGE}/f/identity_recovery/phrase_backup",
                    b"identity key",
                ),
                (
                    f"apps/{HARNESS.PACKAGE}/f/identity_recovery/media_key_backup",
                    b"media key",
                ),
                (
                    f"apps/{HARNESS.PACKAGE}/f/native_journal_transfer/current/database/logdate",
                    b"encrypted journal",
                ),
                (f"apps/{HARNESS.PACKAGE}/f/user_media/original", b"original media"),
                (
                    f"apps/{HARNESS.PACKAGE}/f/media/objects/sha256/ab/"
                    + "a" * 64
                    + ".jpg",
                    b"long media path",
                ),
                (long_path, b"long path"),
                (
                    "apps/org.example.other/f/db_recovery/passphrase_backup",
                    b"other app",
                ),
            ]:
                item = tarfile.TarInfo(name)
                item.size = len(contents)
                archive.addfile(item, io.BytesIO(contents))

        payload = io.BytesIO()
        with tarfile.open(
            fileobj=payload, mode="w", format=tarfile.USTAR_FORMAT
        ) as archive:
            name = f"1/_full/{HARNESS.PACKAGE}"
            item = tarfile.TarInfo(name)
            item.size = len(full_backup.getvalue())
            archive.addfile(item, io.BytesIO(full_backup.getvalue()))
            transport_metadata = tarfile.TarInfo("1/_delta/@pm@/synthetic")
            transport_metadata.size = len(b"transport metadata")
            archive.addfile(transport_metadata, io.BytesIO(b"transport metadata"))

        filtered = HARNESS.without_portable_recovery_keys(payload.getvalue())
        self.assertNotIn(
            b"././@PaxHeader",
            filtered,
            "The native Android restore reader must receive legacy tar headers",
        )
        with tarfile.open(fileobj=io.BytesIO(filtered), mode="r:") as transport:
            self.assertFalse(
                any(
                    item.type in (tarfile.XHDTYPE, tarfile.XGLTYPE)
                    for item in transport.getmembers()
                )
            )
            self.assertEqual(
                b"transport metadata",
                transport.extractfile("1/_delta/@pm@/synthetic").read(),
            )
            nested = transport.extractfile(f"1/_full/{HARNESS.PACKAGE}").read()
        with tarfile.open(fileobj=io.BytesIO(nested), mode="r:") as archive:
            retained = {
                item.name: archive.extractfile(item).read()
                for item in archive.getmembers()
            }
            self.assertFalse(
                any(
                    item.type in (tarfile.XHDTYPE, tarfile.XGLTYPE)
                    for item in archive.getmembers()
                ),
                "Android's full-restore parser cannot consume Python PAX extension headers",
            )

        self.assertNotIn(
            f"apps/{HARNESS.PACKAGE}/f/native_journal_transfer/current/files/db_recovery/passphrase_backup",
            retained,
        )
        self.assertNotIn(
            f"apps/{HARNESS.PACKAGE}/f/identity_recovery/phrase_backup", retained
        )
        self.assertNotIn(
            f"apps/{HARNESS.PACKAGE}/f/identity_recovery/media_key_backup", retained
        )
        self.assertEqual(
            b"encrypted journal",
            retained[
                f"apps/{HARNESS.PACKAGE}/f/native_journal_transfer/current/database/logdate"
            ],
        )
        self.assertEqual(
            b"original media", retained[f"apps/{HARNESS.PACKAGE}/f/user_media/original"]
        )
        self.assertEqual(
            b"long media path",
            retained[
                f"apps/{HARNESS.PACKAGE}/f/media/objects/sha256/ab/" + "a" * 64 + ".jpg"
            ],
        )
        self.assertEqual(b"long path", retained[long_path])
        self.assertEqual(
            b"other app",
            retained["apps/org.example.other/f/db_recovery/passphrase_backup"],
        )

    def test_credentials_only_fixture_keeps_all_recovery_keys_and_removes_journal_data(
        self,
    ):
        prefix = f"apps/{HARNESS.PACKAGE}"
        names = [
            prefix + "/db/logdate",
            prefix + "/f/media/objects/original.jpg",
            f"apps/org.example.other/f/db_recovery/passphrase_backup",
        ]
        payload = self.credentials_backup(
            extra=((name, name.encode()) for name in names)
        )

        retained = HARNESS.credentials_only_from_full_backup(payload)

        with tarfile.open(fileobj=io.BytesIO(retained), mode="r:") as archive:
            names_after_filter = {item.name for item in archive.getmembers()}
            contents = {
                item.name: archive.extractfile(item).read()
                for item in archive.getmembers()
                if item.isfile()
            }
        self.assertEqual(
            {
                prefix + "/_manifest",
                prefix + "/f/db_recovery",
                prefix + "/f/db_recovery/passphrase_backup",
                prefix + "/f/identity_recovery/phrase_backup",
                prefix + "/f/identity_recovery/media_key_backup",
                prefix + "/f/logdate.preferences_pb",
            },
            names_after_filter,
        )
        self.assertEqual(
            b"android app backup manifest", contents[prefix + "/_manifest"]
        )
        self.assertEqual(
            b"database recovery key",
            contents[prefix + "/f/db_recovery/passphrase_backup"],
        )
        self.assertEqual(
            b"identity recovery phrase",
            contents[prefix + "/f/identity_recovery/phrase_backup"],
        )
        self.assertEqual(
            b"media recovery key",
            contents[prefix + "/f/identity_recovery/media_key_backup"],
        )
        preference = HARNESS.canonical_preferences(
            contents[prefix + "/f/logdate.preferences_pb"]
        )
        self.assertTrue(preference)

    def test_credentials_only_acceptance_detects_nested_media_file_paths(self):
        self.assertTrue(
            HARNESS.contains_media_files(
                {"files/media/objects/sha256/photo.jpg": {"bytes": 1}}
            )
        )
        self.assertTrue(
            HARNESS.contains_media_files(
                {b"./files/audio_notes/recording.m4a": {"bytes": 1}}
            )
        )
        self.assertTrue(
            HARNESS.contains_media_files(
                {"files/camera-captures/logdate-camera-unfinished.mp4": {"bytes": 1}}
            )
        )
        self.assertFalse(
            HARNESS.contains_media_files({"files/datastore/preferences": {"bytes": 1}})
        )

    def test_credentials_only_fixture_rejects_missing_database_recovery_key(self):
        database_key = f"apps/{HARNESS.PACKAGE}/f/db_recovery/passphrase_backup"
        full_backup = self.credentials_backup(omit=(database_key,))

        with self.assertRaisesRegex(
            RuntimeError, "missing required portable recovery keys"
        ):
            HARNESS.credentials_only_from_full_backup(full_backup)

    def test_credentials_only_transport_preserves_android_headers_and_other_packages(
        self,
    ):
        prefix = f"apps/{HARNESS.PACKAGE}"
        full_backup = self.credentials_backup(
            extra=(
                (prefix + "/db/logdate", b"encrypted journal"),
                (prefix + "/f/media/original.jpg", b"original media"),
                ("apps/org.example.other/f/settings", b"other app data"),
            ),
        )

        payload = io.BytesIO()
        with tarfile.open(
            fileobj=payload, mode="w", format=tarfile.USTAR_FORMAT
        ) as archive:
            item = tarfile.TarInfo(f"1/_full/{HARNESS.PACKAGE}")
            item.size = len(full_backup)
            archive.addfile(item, io.BytesIO(full_backup))
            metadata = tarfile.TarInfo("1/_delta/@pm@/synthetic")
            metadata.size = len(b"transport metadata")
            archive.addfile(metadata, io.BytesIO(b"transport metadata"))

        filtered = HARNESS.credentials_only(payload.getvalue())
        self.assertNotIn(b"././@PaxHeader", filtered)
        with tarfile.open(fileobj=io.BytesIO(filtered), mode="r:") as transport:
            self.assertEqual(
                b"transport metadata",
                transport.extractfile("1/_delta/@pm@/synthetic").read(),
            )
            nested = transport.extractfile(f"1/_full/{HARNESS.PACKAGE}").read()
        with tarfile.open(fileobj=io.BytesIO(nested), mode="r:") as archive:
            self.assertEqual(
                {
                    prefix + "/_manifest",
                    prefix + "/f/db_recovery",
                    prefix + "/f/db_recovery/passphrase_backup",
                    prefix + "/f/identity_recovery/phrase_backup",
                    prefix + "/f/identity_recovery/media_key_backup",
                    prefix + "/f/logdate.preferences_pb",
                },
                {item.name for item in archive.getmembers()},
            )
