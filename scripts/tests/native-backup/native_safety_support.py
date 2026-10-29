"""Shared native harness objects and unchanged synthetic backup fixture."""

import importlib.util
import io
from pathlib import Path
import tarfile

ROOT = Path(__file__).resolve().parents[3]
SPEC = importlib.util.spec_from_file_location(
    "native_acceptance", ROOT / "tests/e2e/native-backup-acceptance.py"
)
HARNESS = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(HARNESS)
D2D_SPEC = importlib.util.spec_from_file_location(
    "native_d2d_acceptance",
    ROOT / "tests/e2e/native-d2d-acceptance.py",
)
D2D = importlib.util.module_from_spec(D2D_SPEC)
D2D_SPEC.loader.exec_module(D2D)


class CredentialsBackupFixture:
    @staticmethod
    def credentials_backup(*, omit=(), extra=()):
        prefix = f"apps/{HARNESS.PACKAGE}"
        files = {
            prefix + "/_manifest": b"android app backup manifest",
            prefix + "/f/db_recovery/passphrase_backup": b"database recovery key",
            prefix + "/f/identity_recovery/phrase_backup": b"identity recovery phrase",
            prefix + "/f/identity_recovery/media_key_backup": b"media recovery key",
            prefix + "/f/logdate.preferences_pb": HARNESS.length_delimited_field(
                1,
                HARNESS.length_delimited_field(1, b"identity.canonical_owner_id")
                + HARNESS.length_delimited_field(
                    2,
                    HARNESS.length_delimited_field(5, b"synthetic-owner"),
                ),
            ),
        }
        files.update(dict(extra))
        payload = io.BytesIO()
        with tarfile.open(
            fileobj=payload, mode="w", format=tarfile.USTAR_FORMAT
        ) as archive:
            for name, contents in files.items():
                if name in omit:
                    continue
                item = tarfile.TarInfo(name)
                item.size = len(contents)
                archive.addfile(item, io.BytesIO(contents))
        return payload.getvalue()
