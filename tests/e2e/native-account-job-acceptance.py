#!/usr/bin/env python3
"""Verify Android schedules a restored account request across retry and process restart."""
import argparse
import json
from pathlib import Path
import re
import runpy
import shlex
import time
import uuid

NATIVE = runpy.run_path(str(Path(__file__).with_name('native-backup-acceptance.py')))
JOB_ID = 0x4e415252
SERVICE = 'app.logdate.client.data.restore.NativeAccountRecoveryJobService'


def registered_job_failures(dump, package):
    headers = list(re.finditer(r'^\s*JOB\s+#\S+/(\d+):[^\n]*', dump, re.MULTILINE))
    for index, header in enumerate(headers):
        end = headers[index + 1].start() if index + 1 < len(headers) else len(dump)
        block = dump[header.start():end]
        if int(header[1]) != JOB_ID or package + '/' + SERVICE not in header[0]:
            continue
        # Stop before other top-level queues and histories, which can repeat job counters.
        block = re.split(r'\n[^ \n][^\n]*:\n', block, maxsplit=1)[0]
        failures = re.search(r'^\s*Num failures:\s*(\d+)\s*$', block, re.MULTILINE)
        return int(failures[1]) if failures else 0
    raise RuntimeError('The native account service has no matching registered system job')


def read_request(serial):
    raw = NATIVE['adb'](serial, 'exec-out', 'run-as', NATIVE['PACKAGE'], 'cat', 'no_backup/native-account-recovery.json')
    if len(raw) > 4096:
        raise RuntimeError('Native account recovery custody exceeds its size limit')
    request = json.loads(raw)['request']
    for key in ('delivery', 'generation', 'owner'):
        uuid.UUID(request[key])
    if request.get('binding') != 'true' or not re.fullmatch('[a-f0-9]{64}', request.get('scope', '')):
        raise RuntimeError('Account-job acceptance requires the account-bound native fixture')
    return request


def assert_system_request(serial, request, output):
    arguments = ['am', 'instrument', '-w', '-r', '-e', 'class',
                 'app.logdate.client.e2e.NativeAccountJobStateProbeTest',
                 '-e', 'nativeAccountJobAcceptance', 'true', '-e', 'nativeAccountExpectedRequest',
                 json.dumps(request, separators=(',', ':')), NATIVE['RUNNER']]
    result = NATIVE['adb'](serial, 'shell', shlex.join(arguments))
    NATIVE['private_file'](output, result)
    if not NATIVE['instrumentation_passed'](result, 1):
        raise RuntimeError('The Android job did not preserve its original persisted request')


def require_retry(serial, previous, output):
    command = NATIVE['adb'](serial, 'shell', 'cmd', 'jobscheduler', 'run', '-f', NATIVE['PACKAGE'], str(JOB_ID))
    NATIVE['private_file'](output / 'force-job.log', command)
    if b'Running job' not in command:
        raise RuntimeError('Android did not accept the native account job execution request')
    deadline = time.monotonic() + 120
    while True:
        dump = NATIVE['adb'](serial, 'shell', 'dumpsys', 'jobscheduler', NATIVE['PACKAGE']).decode()
        failures = registered_job_failures(dump, NATIVE['PACKAGE'])
        if failures > previous:
            NATIVE['private_file'](output / 'job-after-retry.log', dump.encode())
            return failures
        if time.monotonic() >= deadline:
            NATIVE['private_file'](output / 'job-timeout.log', dump.encode())
            raise RuntimeError('The system job did not retain retry work after its execution')
        time.sleep(.5)


def run(options):
    serial = options.emulator
    if NATIVE['adb'](serial, 'shell', 'getprop', 'ro.kernel.qemu').strip() != b'1':
        raise RuntimeError('Managed emulator required')
    options.output.mkdir(mode=0o700, parents=True, exist_ok=False)
    metadata = json.loads((options.output.parent / 'native-transfer' / 'source-fixture.json').read_text())
    request = read_request(serial)
    assert_system_request(serial, request, options.output / 'persisted-before.log')
    before = registered_job_failures(NATIVE['adb'](serial, 'shell', 'dumpsys', 'jobscheduler', NATIVE['PACKAGE']).decode(), NATIVE['PACKAGE'])
    first = options.output / 'first-attempt'
    first.mkdir(mode=0o700)
    require_retry(serial, before, first)
    if read_request(serial) != request:
        raise RuntimeError('A retry changed the retained native request')
    NATIVE['adb'](serial, 'shell', 'am', 'force-stop', NATIVE['PACKAGE'])
    assert_system_request(serial, request, options.output / 'persisted-after-restart.log')
    second = options.output / 'recreated-attempt'
    second.mkdir(mode=0o700)
    restarted = registered_job_failures(NATIVE['adb'](serial, 'shell', 'dumpsys', 'jobscheduler', NATIVE['PACKAGE']).decode(), NATIVE['PACKAGE'])
    require_retry(serial, restarted, second)
    if read_request(serial) != request:
        raise RuntimeError('Process restart changed the retained native request')
    pending = NATIVE['adb'](serial, 'exec-out', 'run-as', NATIVE['PACKAGE'], 'cat', 'no_backup/.logdate_account_restore_pending').decode().strip()
    if pending != request['delivery']:
        raise RuntimeError('An unavailable credential acknowledged account recovery')
    NATIVE['probe'](serial, 'verify-restored', options.output / 'journal-preserved.log', metadata, 'account')
    retained = NATIVE['adb'](serial, 'shell', 'am', 'instrument', '-w', '-r', '-e', 'class',
                             'app.logdate.client.e2e.NativeRetainedOfflineEditProbeTest',
                             '-e', 'nativeRetainedEdit', 'true', NATIVE['RUNNER'])
    NATIVE['private_file'](options.output / 'retained-offline-edit.log', retained)
    if not NATIVE['instrumentation_passed'](retained, 1):
        raise RuntimeError('The existing offline edit did not survive account-job retry')
    NATIVE['private_file'](options.output / 'acceptance.json', json.dumps({
        'persistedSystemRequest': True, 'realJobServiceRetry': True, 'processRestartRetry': True,
        'originalRequestPreserved': True, 'journalPreserved': True, 'googleProviderAccepted': False,
    }, indent=2).encode())


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--emulator', required=True, type=NATIVE['emulator_serial'])
    parser.add_argument('--app-apk', required=True, type=Path)
    parser.add_argument('--test-apk', required=True, type=Path)
    parser.add_argument('--output', required=True, type=Path)
    parser.add_argument('--synthetic-data', action='store_true', required=True)
    run(parser.parse_args())
