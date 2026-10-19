#!/usr/bin/env python3
"""The rehearsal's test authenticator produces real P-256 keys and valid CBOR, and stays on loopback."""
import hashlib
import importlib.util
import json
import pathlib
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[3]


def load():
    spec = importlib.util.spec_from_file_location("rehearsal_accounts", ROOT / "scripts/identity-publishing/rehearsal-accounts.py")
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


class RehearsalAccountsTest(unittest.TestCase):
    def setUp(self):
        self.accounts = load()

    def test_p256_arithmetic_matches_known_points(self):
        # 2G for NIST P-256, from the SEC 2 / NIST test vectors.
        two_g = (
            0x7CF27B188D034F7E8A52380304B51AC3C08969E277F21B35A60B48FC47669978,
            0x07775510DB8ED040293D9AC69F7430DBBA7DADE63CE982299E04B79D227873D1,
        )
        self.assertEqual(self.accounts.G, self.accounts.scalar_multiply(1))
        self.assertEqual(two_g, self.accounts.scalar_multiply(2))
        self.assertIsNone(self.accounts.scalar_multiply(self.accounts.N))
        self.assertTrue(self.accounts.on_curve(self.accounts.new_public_key()))

    def test_cbor_matches_rfc_8949_examples(self):
        cbor = self.accounts.cbor
        self.assertEqual(bytes.fromhex("00"), cbor(0))
        self.assertEqual(bytes.fromhex("1818"), cbor(24))
        self.assertEqual(bytes.fromhex("1903e8"), cbor(1000))
        self.assertEqual(bytes.fromhex("20"), cbor(-1))
        self.assertEqual(bytes.fromhex("3863"), cbor(-100))
        self.assertEqual(bytes.fromhex("4401020304"), cbor(b"\x01\x02\x03\x04"))
        self.assertEqual(bytes.fromhex("6449455446"), cbor("IETF"))
        self.assertEqual(bytes.fromhex("83010203"), cbor([1, 2, 3]))
        self.assertEqual(bytes.fromhex("a201020304"), cbor({1: 2, 3: 4}))

    def test_registration_answers_the_challenge_for_the_relying_party(self):
        _, client_data, attestation = self.accounts.registration("challenge-value", "localhost", "https://localhost")
        self.assertEqual(
            {"type": "webauthn.create", "challenge": "challenge-value", "origin": "https://localhost", "crossOrigin": False},
            json.loads(client_data),
        )
        rp_hash = hashlib.sha256(b"localhost").digest()
        self.assertIn(rp_hash, attestation)
        cbor = self.accounts.cbor
        self.assertTrue(attestation.startswith(bytes([0xA3]) + cbor("fmt") + cbor("none") + cbor("attStmt") + cbor({})))

    def test_only_loopback_servers_get_test_accounts(self):
        for url in ("http://127.0.0.1:8080", "http://localhost:8080", "http://[::1]:8080"):
            self.accounts.require_loopback(url)
        for url in ("https://cloud.logdate.app", "http://10.0.0.5:8080", "ftp://127.0.0.1"):
            with self.assertRaises(self.accounts.RehearsalError, msg=url):
                self.accounts.require_loopback(url)


if __name__ == "__main__":
    unittest.main()
