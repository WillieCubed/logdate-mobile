from pathlib import Path
import runpy
import tempfile
import unittest
from unittest.mock import patch

MODULE = runpy.run_path(str(Path(__file__).with_name("native-media-reauthentication-acceptance.py")))


class MediaReauthenticationAcceptanceTest(unittest.TestCase):
    def exercise(self, root, *, fetch_while_signed_out=False, fetch_during_assertion=False):
        control = root / "control"
        control.mkdir()
        (control / "media-gate-arm").touch()
        log = control / "media-request-paths.log"
        log.write_text("/api/v1/media/fixture/binary\n")
        events, published = [], []

        def adb(serial, *args, **kwargs):
            if args == ("shell", "cmd", "netpolicy", "list", "wifi-networks"):
                return b"AndroidWifi;none\n"
            if args == ("shell", "cmd", "netpolicy", "get", "restrict-background"):
                return b"disabled"
            events.append(args)
            return b""

        def instrument(serial, selector, output, *, extras, status_actions, timeout):
            stage = extras[-1]
            events.append(stage)
            for marker, action in status_actions.items():
                if fetch_while_signed_out and marker == b"MEDIA_REAUTH_COLD_SIGNED_OUT":
                    log.write_text(log.read_text() + "/api/v1/media/another/binary\n")
                action()

        def publish(path, raw):
            published.append(path.name)
            if fetch_during_assertion and path.name == "media-reauth-assertion-request.json":
                log.write_text(log.read_text() + "/api/v1/media/late/binary\n")
            path.write_bytes(raw)

        def wait(path, *args, **kwargs):
            if not path.exists():
                path.write_bytes(b"{}")

        helpers = {"safe_target": lambda serial: None, "instrument": instrument,
                   "set_metered": lambda serial, network, mode: events.append(("metered", mode))}
        with patch.dict(MODULE["H"], adb=adb), patch.dict(MODULE["MEDIA"], helpers), \
                patch.dict(MODULE["run"].__globals__, read_app_document=lambda *args: b"{}", write_app_document=lambda serial, name, raw: events.append(("private-response", name))):
            try:
                result = MODULE["run"]("emulator-5558", control, object(), root / "output", wait, publish)
            except Exception as error:
                error.acceptance_events, error.published = events, published
                raise
        return result, events, published

    def test_authentication_resumes_after_cold_signed_out_check_and_restores_unset_metering(self):
        with tempfile.TemporaryDirectory() as folder:
            result, events, published = self.exercise(Path(folder))
            stop = ("shell", "am", "force-stop", MODULE["H"]["PACKAGE"])
            self.assertLess(events.index("revoke"), events.index(stop))
            self.assertLess(events.index(stop), events.index("resume"))
            self.assertEqual("media-reauth-complete", published[-1])
            self.assertIn(("private-response", "media-reauth-authentication-boundary.txt"), events)
            self.assertTrue(result["realPasskeyRepositoryResumesSameMediaOperation"])
            self.assertIn(("metered", "undefined"), events)
            self.assertIn(("shell", "cmd", "netpolicy", "set", "restrict-background", "false"), events)

    def test_a_binary_request_during_the_ceremony_cannot_be_accepted(self):
        with tempfile.TemporaryDirectory() as folder:
            with self.assertRaisesRegex(RuntimeError, "signed out") as failure:
                self.exercise(Path(folder), fetch_during_assertion=True)
            self.assertNotIn("media-reauth-complete", failure.exception.published)

    def test_a_signed_out_binary_request_fails_without_publishing_completion(self):
        with tempfile.TemporaryDirectory() as folder:
            with self.assertRaisesRegex(RuntimeError, "signed out") as failure:
                self.exercise(Path(folder), fetch_while_signed_out=True)
            self.assertNotIn("media-reauth-complete", failure.exception.published)
            self.assertIn(("metered", "undefined"), failure.exception.acceptance_events)


if __name__ == "__main__":
    unittest.main()
