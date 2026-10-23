"""Preserve native PIN evidence before disposable managed devices retire."""
import importlib.util
from pathlib import Path
import stat
import tempfile
import unittest
from unittest.mock import Mock, patch

SCRIPT = Path(__file__).with_name('native-post-transfer-acceptance.py')
SPEC = importlib.util.spec_from_file_location('post_transfer_evidence', SCRIPT)
POST = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(POST)
STAGES = ('native-enrollment', 'enrollment-canceled', 'initial-pin-challenge', 'foreground-pin-challenge',
          'recreated-pin-challenge', 'initial-unlocked', 'foreground-unlocked', 'recreated-unlocked')

class LockEvidenceTest(unittest.TestCase):
    def invoke(self, output, instrument_error=None, capture_error=None, missing=None):
        def adb(serial, *args, **kwargs):
            self.assertEqual(serial, 'emulator-5582')
            if args == ('shell', 'getprop', 'ro.kernel.qemu'):
                return b'1'
            if args == ('shell', 'am', 'force-stop', POST.NATIVE['PACKAGE']):
                return b''
            self.assertEqual(args[:2], ('pull', f"/sdcard/Android/data/{POST.NATIVE['PACKAGE']}/cache/restored-app-lock"))
            if capture_error:
                raise capture_error
            target = Path(args[2])
            target.mkdir(parents=True, exist_ok=True)
            for stage in STAGES:
                for suffix in ('png', 'xml', 'txt'):
                    name = stage + ('-state.txt' if suffix == 'txt' else '.' + suffix)
                    if name != missing:
                        (target / name).write_bytes(b'fixture native evidence')
            return b'copied'
        def instrument(serial, classname, *args, **kwargs):
            if 'RestoredJournalLockRuntimeTest' in classname and instrument_error:
                raise instrument_error
        mock = Mock(side_effect=adb)
        with patch.dict(POST.NATIVE, {'adb': mock}), patch.object(POST, 'instrument', side_effect=instrument):
            POST.run('emulator-5582', output)
        return mock

    def test_success_retains_each_pin_stage_privately(self):
        with tempfile.TemporaryDirectory() as temporary:
            output = Path(temporary) / 'contracts'
            self.invoke(output)
            target = output / 'restored-app-lock'
            self.assertTrue(target.is_dir(), 'PIN evidence was discarded with the emulator')
            self.assertEqual(stat.S_IMODE(target.stat().st_mode), 0o700)
            self.assertEqual(len(list(target.iterdir())), 24)
            for path in target.iterdir():
                self.assertEqual(stat.S_IMODE(path.stat().st_mode), 0o600)

    def test_failed_pin_still_retains_available_diagnostics(self):
        original = RuntimeError('original PIN acceptance failure')
        with tempfile.TemporaryDirectory() as temporary:
            output = Path(temporary) / 'contracts'
            with self.assertRaises(RuntimeError) as failure:
                self.invoke(output, instrument_error=original)
            self.assertIs(failure.exception, original)
            self.assertTrue((output / 'restored-app-lock/native-enrollment.png').is_file())

    def test_capture_failure_cannot_replace_failed_pin(self):
        original = RuntimeError('original PIN acceptance failure')
        with tempfile.TemporaryDirectory() as temporary:
            with self.assertRaises(RuntimeError) as failure:
                self.invoke(Path(temporary) / 'contracts', instrument_error=original,
                            capture_error=RuntimeError('capture transport failed'))
        self.assertIs(failure.exception, original)
        self.assertTrue(any('capture transport failed' in note for note in getattr(original, '__notes__', [])))

    def test_pass_requires_complete_evidence(self):
        with tempfile.TemporaryDirectory() as temporary:
            with self.assertRaisesRegex(RuntimeError, 'evidence'):
                self.invoke(Path(temporary) / 'contracts', missing='foreground-unlocked.png')

    def test_pass_cannot_ignore_capture_transport_failure(self):
        with tempfile.TemporaryDirectory() as temporary:
            with self.assertRaisesRegex(RuntimeError, 'capture transport failed'):
                self.invoke(Path(temporary) / 'contracts', capture_error=RuntimeError('capture transport failed'))

if __name__ == '__main__':
    unittest.main()
