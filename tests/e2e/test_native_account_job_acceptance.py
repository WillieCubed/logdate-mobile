import runpy
from pathlib import Path
import unittest
import tempfile
import json
from types import SimpleNamespace
from unittest.mock import Mock, patch

MODULE = runpy.run_path(str(Path(__file__).with_name('native-account-job-acceptance.py')))
JOB = int('4e415252', 16)
PACKAGE = 'studio.hypertext.logdate.debug'


class NativeAccountJobAcceptanceTest(unittest.TestCase):
    def test_failure_count_belongs_to_the_registered_recovery_service(self):
        dump = f'''Registered 2 jobs:
  JOB #u0a123/{JOB}: c001 {PACKAGE}/app.logdate.client.data.restore.NativeAccountRecoveryJobService
    Num failures: 2
  JOB #u0a123/42: c002 {PACKAGE}/androidx.work.impl.background.systemjob.SystemJobService
    Num failures: 99
'''
        self.assertEqual(2, MODULE['registered_job_failures'](dump, PACKAGE))

    def test_a_neighbouring_job_failure_does_not_prove_recovery_ran(self):
        dump = f'''Registered 2 jobs:
  JOB #u0a123/{JOB}: c001 {PACKAGE}/app.logdate.client.data.restore.NativeAccountRecoveryJobService
    Enqueue time: -1m
  JOB #u0a123/42: c002 {PACKAGE}/androidx.work.impl.background.systemjob.SystemJobService
    Num failures: 99
'''
        self.assertEqual(0, MODULE['registered_job_failures'](dump, PACKAGE))

    def test_wrong_service_or_missing_registered_job_is_rejected(self):
        for dump in ('', f'  JOB #u0a123/{JOB}: c001 {PACKAGE}/OtherService\n    Num failures: 9\n'):
            with self.subTest(dump=dump), self.assertRaises(RuntimeError):
                MODULE['registered_job_failures'](dump, PACKAGE)

    def test_restart_acceptance_uses_a_read_only_destination_oracle(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            (root / 'native-transfer').mkdir()
            (root / 'native-transfer' / 'source-fixture.json').write_text(json.dumps({'owner': 'synthetic'}))
            request = {'delivery': '2f18404e-e9f5-434d-8b03-2f04f09ac523'}
            def adb(serial, *arguments):
                if arguments[-1] == 'ro.kernel.qemu': return b'1'
                if arguments[-1] == 'no_backup/.logdate_account_restore_pending': return request['delivery'].encode()
                return b'OK (1 test)'
            native = dict(MODULE['NATIVE'], adb=Mock(side_effect=adb), probe=Mock(), instrumentation_passed=Mock(return_value=True))
            namespace = MODULE['run'].__globals__
            with patch.dict(namespace, NATIVE=native, read_request=Mock(return_value=request),
                            assert_system_request=Mock(), registered_job_failures=Mock(return_value=0), require_retry=Mock(return_value=1)):
                MODULE['run'](SimpleNamespace(emulator='emulator-5580', output=root / 'account-job'))
            native['probe'].assert_called_once_with('emulator-5580', 'verify-restored', root / 'account-job' / 'journal-preserved.log', {'owner': 'synthetic'}, 'account')
            self.assertTrue((root / 'account-job' / 'acceptance.json').exists())


if __name__ == '__main__':
    unittest.main()
