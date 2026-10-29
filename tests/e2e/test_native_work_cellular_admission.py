from contextlib import contextmanager
import importlib.util
from pathlib import Path
import tempfile
import subprocess
import os
import shlex
import unittest
from unittest.mock import patch

SPEC = importlib.util.spec_from_file_location(
    "work_cellular",
    Path(__file__).with_name("native-restore-work-restart-acceptance.py"),
)
WORK = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(WORK)


def state(enabled=True, sub=1):
    return (sub, enabled, enabled)


def wire(*, airplane=b"0", wifi=b"1", mobile=b"1", sub=b"1", after=None, phone=None):
    if phone is None:
        enabled = b"true" if mobile == b"1" else b"false"
        phone = (
            b"GsmCdmaPhone extends:\nPhone: subId="
            + sub
            + b"\n mPhoneId=0\nDataSettingsManager-0:\n isUserDataEnabled="
            + enabled
            + b"\n device_provisioned=1\n"
        )
    return (
        b"\n".join(
            [
                b"LOGDATE_RADIO_SETTINGS",
                airplane,
                wifi,
                mobile,
                sub,
                b"LOGDATE_PHONE_BEGIN",
                phone,
                b"LOGDATE_PHONE_END",
                sub if after is None else after,
            ]
        )
        + b"\n"
    )


class WorkCellularAdmissionTest(unittest.TestCase):
    @contextmanager
    def clock(self):
        now = [0.0]
        with (
            patch.object(WORK.time, "monotonic", side_effect=lambda: now[0]),
            patch.object(
                WORK.time,
                "sleep",
                side_effect=lambda seconds: now.__setitem__(0, now[0] + seconds),
            ),
        ):
            yield now

    def test_connected_cellular_requires_explicit_disable_before_admission(self):
        commands, disabled = [], [False]
        with self.clock():

            def adb(serial, *args, **kwargs):
                commands.append(args)
                if args == ("shell", "svc", "data", "disable"):
                    disabled[0] = True
                if args == ("shell", "dumpsys", "connectivity"):
                    return (
                        b"Active default network: none\n"
                        if disabled[0]
                        else b"Active default network: 101\nMOBILE[HSPA] CONNECTED VALIDATED\n"
                    )
                return b""

            with (
                patch.object(WORK, "adb", side_effect=adb),
                patch.object(
                    WORK,
                    "radio_state",
                    side_effect=lambda *a, **k: (True, b"0", state(not disabled[0])),
                ),
            ):
                WORK.set_offline("emulator-5554")
        self.assertIn(("shell", "svc", "data", "disable"), commands)

    def test_state_reader_requires_explicit_single_phone_subscription_and_user_policy(
        self,
    ):
        for enabled in (True, False):
            with (
                self.subTest(enabled=enabled),
                self.clock(),
                patch.object(
                    WORK, "adb", return_value=wire(mobile=b"1" if enabled else b"0")
                ) as adb,
            ):
                self.assertEqual(
                    (False, b"1", state(enabled)),
                    WORK.radio_state("emulator-5554", deadline=10),
                )
                self.assertEqual(
                    1,
                    adb.call_count,
                    "Fixed radio reads must not multiply owned-wrapper round trips",
                )

    def test_unknown_or_drifting_subscription_and_policy_are_not_defaults(self):
        samples = [
            wire(mobile=b"null"),
            wire(sub=b"null"),
            wire(sub=b"-1"),
            wire(after=b"2"),
            wire(airplane=b"null"),
            wire(phone=b""),
            wire(
                phone=b"GsmCdmaPhone extends:\nPhone: subId=2\n mPhoneId=0\nDataSettingsManager-0:\n isUserDataEnabled=true\n"
            ),
            wire(
                phone=b"GsmCdmaPhone extends:\nPhone: subId=1\n mPhoneId=0\nDataSettingsManager-0:\n isUserDataEnabled=true\nDataSettingsManager-1:\n isUserDataEnabled=false\n"
            ),
        ]
        for raw in samples:
            with (
                self.subTest(raw=raw),
                self.clock(),
                patch.object(WORK, "adb", return_value=raw),
            ):
                with self.assertRaises(RuntimeError):
                    WORK.radio_state("emulator-5554", deadline=10)

    def test_restores_original_enabled_and_disabled_cellular_state(self):
        for enabled in (True, False):
            original = (False, b"1", state(enabled))
            calls = []
            with (
                self.clock(),
                patch.object(
                    WORK,
                    "adb",
                    side_effect=lambda serial, *args, **kwargs: calls.append(args),
                ),
                patch.object(WORK, "radio_state", return_value=original),
            ):
                WORK.restore_radio_state("emulator-5554", original)
            self.assertIn(
                ("shell", "svc", "data", "enable" if enabled else "disable"), calls
            )

    def test_changed_subscription_is_never_mutated_during_restore(self):
        calls = []
        with (
            self.clock(),
            patch.object(
                WORK,
                "adb",
                side_effect=lambda serial, *args, **kwargs: calls.append(args),
            ),
            patch.object(
                WORK, "radio_state", return_value=(False, b"1", state(False, sub=2))
            ),
        ):
            with self.assertRaises(RuntimeError):
                WORK.restore_radio_state("emulator-5554", (False, b"1", state(True)))
        self.assertFalse(any(args[:3] == ("shell", "svc", "data") for args in calls))
        self.assertIn(("shell", "svc", "wifi", "enable"), calls)

    def test_cellular_setting_alone_cannot_hide_connected_network(self):
        with (
            self.clock() as now,
            patch.object(WORK, "radio_state", return_value=(True, b"0", state(False))),
            patch.object(
                WORK,
                "adb",
                side_effect=lambda serial, *args, **kwargs: (
                    b"Active default network: 101\n"
                    if args == ("shell", "dumpsys", "connectivity")
                    else b""
                ),
            ),
        ):
            with self.assertRaises(RuntimeError):
                WORK.set_offline("emulator-5554")
            self.assertLessEqual(now[0], 10)

    def test_original_state_capture_has_a_five_second_bound_without_caller_deadline(
        self,
    ):
        with self.clock(), patch.object(WORK, "adb", return_value=wire()) as adb:
            WORK.radio_state("emulator-5554")
            self.assertEqual(5, adb.call_args.kwargs["timeout"])

    def test_incoherent_initial_policy_refuses_before_any_radio_mutation(self):
        calls = []
        with (
            self.clock(),
            patch.object(
                WORK, "radio_state", return_value=(False, b"1", (1, False, True))
            ),
            patch.object(
                WORK,
                "adb",
                side_effect=lambda serial, *args, **kwargs: calls.append(args),
            ),
        ):
            with self.assertRaisesRegex(RuntimeError, "changing"):
                WORK.set_offline("emulator-5554")
        self.assertEqual([], calls)

    def test_actual_user_policy_must_settle_before_offline_admission(self):
        readings = [
            (False, b"1", state()),
            (True, b"0", (1, False, True)),
            (True, b"0", state(False)),
        ]
        with (
            self.clock() as now,
            patch.object(WORK, "radio_state", side_effect=readings) as radio,
            patch.object(
                WORK,
                "adb",
                side_effect=lambda serial, *args, **kwargs: (
                    b"Active default network: none\n"
                    if args == ("shell", "dumpsys", "connectivity")
                    else b""
                ),
            ),
        ):
            WORK.set_offline("emulator-5554")
            self.assertEqual(3, radio.call_count)
            self.assertGreater(now[0], 0)
            self.assertLess(now[0], 10)

    def test_failed_control_still_restores_others_within_original_ceiling(self):
        calls = []
        original = (False, b"1", state())
        with self.clock() as now:

            def adb(serial, *args, timeout):
                calls.append((args, timeout, now[0]))
                if args[:3] == ("shell", "cmd", "connectivity"):
                    now[0] += 4
                    raise RuntimeError("airplane control failed")
                now[0] += 0.1

            with (
                patch.object(WORK, "adb", side_effect=adb),
                patch.object(WORK, "radio_state", return_value=original),
            ):
                WORK.restore_radio_state("emulator-5554", original)
            self.assertIn(
                ("shell", "svc", "wifi", "enable"), [call[0] for call in calls]
            )
            self.assertIn(
                ("shell", "svc", "data", "enable"), [call[0] for call in calls]
            )
            for _, timeout, started in calls:
                self.assertLessEqual(timeout, min(5, 15 - started))
            self.assertLessEqual(now[0], 15)

    def test_outer_capture_drift_is_refused_before_controls_or_application_probes(self):
        controls = []
        with tempfile.TemporaryDirectory() as folder, self.clock():
            root = Path(folder)
            app, test = root / "app.apk", root / "test.apk"
            app.touch()
            test.touch()
            argv = [
                "work",
                "--emulator",
                "emulator-5554",
                "--app-apk",
                str(app),
                "--test-apk",
                str(test),
                "--output",
                str(root / "output"),
            ]

            def adb(serial, *args, **kwargs):
                if (
                    args[:3] == ("shell", "svc", "data")
                    or args[:3] == ("shell", "svc", "wifi")
                    or args[:3] == ("shell", "cmd", "connectivity")
                ):
                    controls.append(args)
                return b"1"

            with (
                patch("sys.argv", argv),
                patch.object(WORK, "adb", side_effect=adb),
                patch.object(WORK, "require_empty_install"),
                patch.object(
                    WORK,
                    "radio_state",
                    side_effect=[
                        (False, b"1", state(sub=1)),
                        (False, b"1", state(sub=2)),
                    ],
                ),
                patch.object(WORK, "probe") as probe,
                patch.object(WORK, "restore_radio_state") as restore,
            ):
                with self.assertRaises(RuntimeError):
                    WORK.main()
                self.assertEqual(
                    [],
                    controls,
                    "Admission must not mutate sub2 after capturing original sub1",
                )
                probe.assert_not_called()
                restore.assert_not_called()

    def test_transitional_original_wifi_is_rejected_before_any_mutation(self):
        calls = []
        with (
            self.clock(),
            patch.object(WORK, "radio_state", return_value=(False, b"2", state())),
            patch.object(
                WORK,
                "adb",
                side_effect=lambda serial, *args, **kwargs: calls.append(args) or b"",
            ),
        ):
            with self.assertRaises(RuntimeError):
                WORK.set_offline("emulator-5554")
        self.assertEqual(
            [],
            calls,
            "Original Wi-Fi2 cannot be promised exact restoration by svc wifi enable",
        )

    def test_real_fixed_shell_read_preserves_framing_and_policy(self):
        with tempfile.TemporaryDirectory() as folder:
            binary = Path(folder)
            settings = binary / "settings"
            settings.write_text(
                '#!/bin/sh\ncase "$3" in airplane_mode_on) echo 0;; wifi_on|mobile_data|multi_sim_data_call) echo 1;; *) exit 3;; esac\n'
            )
            settings.chmod(0o700)
            dumpsys = binary / "dumpsys"
            dumpsys.write_text(
                "#!/bin/sh\n"
                "if [ \"$*\" = 'activity service com.android.phone/.TelephonyDebugService' ]; then\n"
                "printf 'GsmCdmaPhone extends:\\nPhone: subId=1\\n mPhoneId=0\\nDataSettingsManager-0:\\n isUserDataEnabled=true\\n device_provisioned=1\\n'\n"
                'elif [ "$*" = phone ]; then\n'
                "printf '%s\\n' '------- PhoneGlobals -------' 'mDefaultDataSubId=1' '------- End PhoneGlobals -------'\n"
                "else exit 3; fi\n"
            )
            dumpsys.chmod(0o700)

            def adb(serial, *args, **kwargs):
                self.assertEqual("shell", args[0])
                shell, flag, script = shlex.split(args[1])
                self.assertEqual(("sh", "-c"), (shell, flag))
                return subprocess.run(
                    ["/bin/sh", "-c", script],
                    capture_output=True,
                    check=True,
                    env={**os.environ, "PATH": str(binary)},
                    timeout=kwargs["timeout"],
                ).stdout

            with self.clock(), patch.object(WORK, "adb", side_effect=adb):
                try:
                    observed = WORK.radio_state("emulator-5554", deadline=10)
                except RuntimeError as failure:
                    self.fail(
                        f"The read must target actual telephony debug service rather than phone binder: {failure}"
                    )
                self.assertEqual((False, b"1", state()), observed)

    def test_cleanup_errors_do_not_replace_original_probe_failure(self):
        original = RuntimeError("original probe failure")
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            app, test = root / "app.apk", root / "test.apk"
            app.touch()
            test.touch()
            argv = [
                "work",
                "--emulator",
                "emulator-5554",
                "--app-apk",
                str(app),
                "--test-apk",
                str(test),
                "--output",
                str(root / "output"),
            ]

            def successful_admission(*args, **kwargs):
                kwargs["on_radio_mutation"]()

            with (
                patch("sys.argv", argv),
                patch.object(WORK, "adb", return_value=b"1"),
                patch.object(WORK, "require_empty_install"),
                patch.object(WORK, "radio_state", return_value=(False, b"1", state())),
                patch.object(WORK, "set_offline", side_effect=successful_admission),
                patch.object(
                    WORK,
                    "probe",
                    side_effect=[original, RuntimeError("cleanup failed")],
                ),
                patch.object(
                    WORK,
                    "restore_radio_state",
                    side_effect=RuntimeError("restore failed"),
                ) as restore,
            ):
                with self.assertRaises(RuntimeError) as failure:
                    WORK.main()
                self.assertIs(original, failure.exception)
                restore.assert_called_once()
                self.assertEqual(2, len(failure.exception.__notes__))


if __name__ == "__main__":
    unittest.main()
