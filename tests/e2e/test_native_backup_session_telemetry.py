import unittest
from native_backup_test_support import NATIVE_BACKUP_ACCEPTANCE


class SessionTelemetrySnapshotTest(unittest.TestCase):
    def test_sdk_process_session_may_change_without_changing_the_journal(self):
        path = "files/datastore/firebaseSessions/sessionDataStore.data"
        journal = {"databases/logdate": {"bytes": 1, "sha256": "accepted-journal"}}
        before = {**journal, path: {"bytes": 1, "sha256": "old-sdk-process"}}
        after = {**journal, path: {"bytes": 2, "sha256": "new-sdk-process"}}
        NATIVE_BACKUP_ACCEPTANCE.assert_snapshot_preserved(before, after)
        NATIVE_BACKUP_ACCEPTANCE.assert_snapshot_preserved(journal, after)

    def test_exemption_cannot_hide_drafts_keys_policy_records_or_unknown_sdk_files(
        self,
    ):
        for path in (
            "databases/logdate",
            "files/logdate.preferences_pb",
            "files/datastore/entry_drafts.preferences_pb",
            "files/db_recovery/passphrase_backup",
            "files/archive_recovery/records/recovery.json",
            "files/datastore/firebaseSessions/user-draft.preferences_pb",
            "files/datastore/firebaseSessions/sessionDataStore.data.extra",
        ):
            with self.subTest(path=path):
                before = {
                    "databases/logdate": {"bytes": 1, "sha256": "journal"},
                    path: {"bytes": 1, "sha256": "before"},
                }
                after = {**before, path: {"bytes": 1, "sha256": "after"}}
                with self.assertRaisesRegex(
                    RuntimeError, "snapshot changed durable data"
                ):
                    NATIVE_BACKUP_ACCEPTANCE.assert_snapshot_preserved(before, after)


if __name__ == "__main__":
    unittest.main()
