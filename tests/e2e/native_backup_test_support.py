import argparse
from contextlib import redirect_stderr
import hashlib
import importlib.util
import io
from pathlib import Path
import subprocess
import tarfile
import tempfile
import time
import unittest
from unittest.mock import call, patch


SCRIPT = Path(__file__).with_name("native-backup-acceptance.py")
SPEC = importlib.util.spec_from_file_location("native_backup_acceptance", SCRIPT)
NATIVE_BACKUP_ACCEPTANCE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(NATIVE_BACKUP_ACCEPTANCE)
WORK_SCRIPT = Path(__file__).with_name("native-restore-work-restart-acceptance.py")
WORK_SPEC = importlib.util.spec_from_file_location(
    "native_restore_work_acceptance", WORK_SCRIPT
)
NATIVE_RESTORE_WORK_ACCEPTANCE = importlib.util.module_from_spec(WORK_SPEC)
WORK_SPEC.loader.exec_module(NATIVE_RESTORE_WORK_ACCEPTANCE)
D2D_SCRIPT = Path(__file__).with_name("native-d2d-acceptance.py")


class BackupPayloadFixture:
    @staticmethod
    def make_tar(entries):
        output = io.BytesIO()
        with tarfile.open(
            fileobj=output, mode="w:", format=tarfile.USTAR_FORMAT
        ) as archive:
            for name, contents in entries:
                info = tarfile.TarInfo(name)
                info.size = len(contents)
                if name.endswith("/"):
                    info.type = tarfile.DIRTYPE
                archive.addfile(info, io.BytesIO(contents) if contents else None)
        return output.getvalue()

    @staticmethod
    def preference_entry(name, value):
        preference_value = NATIVE_BACKUP_ACCEPTANCE.length_delimited_field(
            5, value.encode()
        )
        entry = NATIVE_BACKUP_ACCEPTANCE.length_delimited_field(
            1, name.encode()
        ) + NATIVE_BACKUP_ACCEPTANCE.length_delimited_field(2, preference_value)
        return entry
