from importlib.util import module_from_spec, spec_from_file_location
import json
from pathlib import Path
import stat
import subprocess
import tempfile
import threading
import time
import unittest
from unittest.mock import patch


SPEC = spec_from_file_location(
    "managed_native_resources", Path(__file__).with_name("managed-native-resources.py")
)
RESOURCES = module_from_spec(SPEC)
SPEC.loader.exec_module(RESOURCES)


class ManagedNativeResourcesTest(unittest.TestCase):
    def rows(self, output):
        file = output / "host-resources.jsonl"
        self.assertTrue(file.is_file(), "Observer must retain a private JSONL artifact")
        return [json.loads(line) for line in file.read_text().splitlines()]

    def wait_for(self, predicate):
        deadline = time.monotonic() + 2
        while time.monotonic() < deadline:
            if predicate():
                return
            time.sleep(0.005)
        self.fail("Observer did not publish bounded evidence")

    def test_process_rows_keep_only_numeric_identity_name_and_rss(self):
        result = RESOURCES._parse_processes(
            "10 1 2048 /private/java\n"
            "11 1 4096 /sdk/qemu-system-aarch64\n"
            "12 1 100 /private/credential-reader\n"
            "13 1 100 /private/java --password=do-not-retain\n"
            "14 invalid 100 java\n"
            "15 1 -100 java\n"
        )
        self.assertEqual(
            [
                {"pid": 10, "ppid": 1, "name": "java", "rssKiB": 2048},
                {"pid": 11, "ppid": 1, "name": "qemu-system", "rssKiB": 4096},
            ],
            result["processes"],
        )
        encoded = json.dumps(result)
        for private in ("private", "sdk", "credential", "password", "do-not-retain"):
            self.assertNotIn(private, encoded)

    def test_process_capture_has_a_fixed_limit_and_honest_total(self):
        result = RESOURCES._parse_processes(
            "\n".join(f"{pid} 1 10 java" for pid in range(1, 200))
        )
        self.assertEqual(128, len(result["processes"]))
        self.assertEqual(199, result["matchedProcessCount"])
        self.assertTrue(result["processesTruncated"])

    def test_linux_readers_keep_only_expected_numeric_fields(self):
        memory = RESOURCES._parse_meminfo(
            "MemAvailable: 100 kB\nSwapFree: 20 kB\nSecret: never-retain\n"
        )
        self.assertEqual({"memAvailableBytes": 102400, "swapFreeBytes": 20480}, memory)
        pressure = RESOURCES._parse_pressure(
            "some avg10=0.01 avg60=0.02 avg300=0.03 total=42\n"
            "full avg10=0.00 avg60=0.00 avg300=0.00 total=2\n"
            "private credential=never-retain\n"
        )
        self.assertEqual(42, pressure["some"]["totalMicroseconds"])
        self.assertEqual(0.03, pressure["some"]["avg300"])
        self.assertEqual(2, pressure["full"]["totalMicroseconds"])
        self.assertNotIn("private", json.dumps(pressure))

    def test_host_sample_retains_read_errors_by_type_without_private_messages(self):
        with (
            patch.object(RESOURCES.sys, "platform", "linux"),
            patch.object(
                RESOURCES, "_read_text", side_effect=PermissionError("secret-file")
            ),
            patch.object(
                RESOURCES.shutil, "disk_usage", side_effect=OSError("secret-path")
            ),
            patch.object(
                RESOURCES.subprocess,
                "run",
                side_effect=subprocess.TimeoutExpired(
                    "secret-command", 1, output=b"secret-output"
                ),
            ),
        ):
            sample = RESOURCES._host_sample(Path("/unused"))
        encoded = json.dumps(sample)
        self.assertIn("PermissionError", encoded)
        self.assertIn("TimeoutExpired", encoded)
        self.assertNotIn("secret", encoded)

    def test_process_read_command_never_requests_arguments_or_environment(self):
        with patch.object(
            RESOURCES.subprocess,
            "run",
            return_value=subprocess.CompletedProcess([], 0, "10 1 20 java\n"),
        ) as command:
            sample = RESOURCES._host_sample(Path(tempfile.gettempdir()))
        self.assertTrue(
            command.called, "Observer must collect bounded process metadata"
        )
        self.assertEqual(
            ["ps", "-axo", "pid=,ppid=,rss=,comm="], command.call_args.args[0]
        )
        self.assertEqual(1, command.call_args.kwargs["timeout"])
        self.assertEqual("java", sample["processes"][0]["name"])

    def test_private_output_phase_and_terminal_close_preserve_primary_failure(self):
        with (
            tempfile.TemporaryDirectory() as directory,
            patch.object(
                RESOURCES, "_host_sample", return_value={"loadAverage": [0.1, 0.2, 0.3]}
            ),
            patch.object(RESOURCES, "SAMPLE_INTERVAL_SECONDS", 0.01),
        ):
            output = Path(directory)
            observer = RESOURCES.start_resource_observer(output)
            observer.set_phase("drivers")
            self.wait_for(
                lambda: any(row.get("phase") == "drivers" for row in self.rows(output))
            )
            primary = RuntimeError("acceptance failed")
            with self.assertRaises(RuntimeError) as caught:
                try:
                    raise primary
                finally:
                    observer.close()
            self.assertIs(primary, caught.exception)
            observer.close()
            self.assertFalse(observer._thread.is_alive())
            rows = self.rows(output)
            self.assertEqual("closed", rows[-1]["event"])
            self.assertEqual(
                0o600, stat.S_IMODE((output / "host-resources.jsonl").stat().st_mode)
            )
            self.assertEqual(
                "RSS is not PSS; configured heap caps are not usage",
                rows[0]["measurementLimit"],
            )

    def test_all_rows_including_header_and_terminal_are_bounded(self):
        with (
            tempfile.TemporaryDirectory() as directory,
            patch.object(RESOURCES, "_host_sample", return_value={}),
            patch.object(RESOURCES, "SAMPLE_INTERVAL_SECONDS", 0.001),
            patch.object(RESOURCES, "MAX_ROWS", 5),
        ):
            observer = RESOURCES.start_resource_observer(Path(directory))
            try:
                observer._thread.join(2)
                self.assertFalse(observer._thread.is_alive())
            finally:
                observer.close()
            rows = self.rows(Path(directory))
            self.assertEqual(5, len(rows))
            self.assertEqual("closed", rows[-1]["event"])
            self.assertEqual("row_limit", rows[-1]["reason"])

    def test_sampling_failure_is_bounded_and_does_not_escape_close(self):
        with (
            tempfile.TemporaryDirectory() as directory,
            patch.object(
                RESOURCES, "_host_sample", side_effect=RuntimeError("private contents")
            ),
        ):
            observer = RESOURCES.start_resource_observer(Path(directory))
            self.wait_for(
                lambda: any(
                    row.get("event") == "sample_error"
                    for row in self.rows(Path(directory))
                )
            )
            observer.close()
            encoded = json.dumps(self.rows(Path(directory)))
            self.assertIn("RuntimeError", encoded)
            self.assertNotIn("private contents", encoded)
            self.assertFalse(observer._thread.is_alive())

    def test_existing_artifact_and_symlink_are_never_overwritten(self):
        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory)
            file = output / "host-resources.jsonl"
            file.write_text("retained evidence")
            with self.assertRaises(FileExistsError):
                RESOURCES.start_resource_observer(output)
            self.assertEqual("retained evidence", file.read_text())
            file.unlink()
            target = output / "untouched"
            target.write_text("original")
            file.symlink_to(target)
            with self.assertRaises(FileExistsError):
                RESOURCES.start_resource_observer(output)
            self.assertEqual("original", target.read_text())

    def test_phase_rejects_arbitrary_content_and_closed_observer_cannot_append(self):
        with (
            tempfile.TemporaryDirectory() as directory,
            patch.object(RESOURCES, "_host_sample", return_value={}),
        ):
            observer = RESOURCES.start_resource_observer(Path(directory))
            try:
                for phase in (
                    "/private/path",
                    "session secret",
                    "a" * 65,
                    "",
                    "bad\nphase",
                ):
                    with self.assertRaises(ValueError):
                        observer.set_phase(phase)
            finally:
                observer.close()
            before = (Path(directory) / "host-resources.jsonl").read_bytes()
            observer.set_phase("finished")
            self.assertEqual(
                before, (Path(directory) / "host-resources.jsonl").read_bytes()
            )

    def test_close_interrupts_interval_instead_of_waiting_five_seconds(self):
        sampled = threading.Event()
        with (
            tempfile.TemporaryDirectory() as directory,
            patch.object(
                RESOURCES, "_host_sample", side_effect=lambda _: sampled.set() or {}
            ),
        ):
            observer = RESOURCES.start_resource_observer(Path(directory))
            self.assertTrue(sampled.wait(1))
            started = time.monotonic()
            observer.close()
            self.assertLess(time.monotonic() - started, 1)
            self.assertFalse(observer._thread.is_alive())

    def test_unresponsive_sample_cannot_replace_primary_failure_or_block_close(self):
        entered = threading.Event()
        released = threading.Event()

        def blocked_sample(_):
            entered.set()
            released.wait(2)
            return {}

        with (
            tempfile.TemporaryDirectory() as directory,
            patch.object(RESOURCES, "_host_sample", side_effect=blocked_sample),
            patch.object(RESOURCES, "CLOSE_TIMEOUT_SECONDS", 0.01, create=True),
        ):
            observer = RESOURCES.start_resource_observer(Path(directory))
            try:
                self.assertTrue(entered.wait(1))
                primary = RuntimeError("acceptance failed")
                with self.assertRaises(RuntimeError) as caught:
                    try:
                        raise primary
                    finally:
                        self.assertFalse(observer.close())
                self.assertIs(primary, caught.exception)
            finally:
                released.set()
                observer._thread.join(2)
                observer.close()
            self.assertFalse(observer._thread.is_alive())
            self.assertEqual("closed", self.rows(Path(directory))[-1]["event"])


if __name__ == "__main__":
    unittest.main()
