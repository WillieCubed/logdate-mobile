#!/usr/bin/env python3
"""Regression coverage for the final advertising-ID release gate."""

import importlib.util
from pathlib import Path
import tempfile
import unittest
from zipfile import ZipFile

ROOT = Path(__file__).resolve().parents[3]
SPEC = importlib.util.spec_from_file_location(
    "ad_id_policy", ROOT / "scripts/verify-android-advertising-id.py"
)
POLICY = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(POLICY)
ANDROID = "http://schemas.android.com/apk/res/android"


class AdvertisingIdPolicyTest(unittest.TestCase):
    def setUp(self):
        self.runtime = tempfile.TemporaryDirectory()
        self.addCleanup(self.runtime.cleanup)
        self.root = Path(self.runtime.name)

    def manifest(
        self, permission="", enabled="false", collection="true", firebase=True
    ):
        path = self.root / "AndroidManifest.xml"
        components = (
            ""
            if not firebase
            else """
          <service android:name="com.google.firebase.components.ComponentDiscoveryService">
            <meta-data android:name="com.google.firebase.components:com.google.firebase.analytics.connector.internal.AnalyticsConnectorRegistrar" android:value="com.google.firebase.components.ComponentRegistrar"/>
            <meta-data android:name="com.google.firebase.components:com.google.firebase.crashlytics.CrashlyticsRegistrar" android:value="com.google.firebase.components.ComponentRegistrar"/>
          </service>"""
        )
        path.write_text(f'''<manifest xmlns:android="{ANDROID}">
          {permission}<application>
            <meta-data android:name="google_analytics_adid_collection_enabled" android:value="{enabled}"/>
            <meta-data android:name="firebase_analytics_collection_enabled" android:value="{collection}"/>
            {components}</application></manifest>''')
        return path

    def bundle(self, manifests):
        path = self.root / "app.aab"
        with ZipFile(path, "w") as archive:
            for module, payload in manifests.items():
                archive.writestr(module + "/manifest/AndroidManifest.xml", payload)
            archive.writestr(
                "base/dex/classes.dex", b"com.google.android.gms.permission.AD_ID"
            )
        return path

    def test_accepts_opt_out_with_analytics_and_crashlytics_retained(self):
        POLICY.verify_manifest(self.manifest(), require_firebase=True)

    def test_rejects_inherited_advertising_id_permissions(self):
        for permission in (
            "com.google.android.gms.permission.AD_ID",
            "android.permission.ACCESS_ADSERVICES_AD_ID",
        ):
            with (
                self.subTest(permission=permission),
                self.assertRaisesRegex(ValueError, "permission"),
            ):
                POLICY.verify_manifest(
                    self.manifest(f'<uses-permission android:name="{permission}"/>')
                )

    def test_requires_explicit_advertising_id_opt_out(self):
        with self.assertRaisesRegex(ValueError, "collection"):
            POLICY.verify_manifest(self.manifest(enabled="true"))

    def test_does_not_allow_disabling_ordinary_analytics(self):
        with self.assertRaisesRegex(ValueError, "Analytics"):
            POLICY.verify_manifest(self.manifest(collection="false"))

    def test_requires_phone_firebase_components(self):
        with self.assertRaisesRegex(ValueError, "Firebase"):
            POLICY.verify_manifest(self.manifest(firebase=False), require_firebase=True)

    def test_checks_every_packaged_module_manifest(self):
        path = self.bundle(
            {
                "base": b"safe manifest",
                "feature": b"com.google.android.gms.permission.AD_ID",
            }
        )
        with self.assertRaisesRegex(ValueError, "feature"):
            POLICY.verify_bundle(path)

    def test_does_not_confuse_sdk_code_with_a_declared_permission(self):
        self.assertEqual(
            ["base"], POLICY.verify_bundle(self.bundle({"base": b"safe manifest"}))
        )

    def test_requires_a_base_module_manifest(self):
        with self.assertRaisesRegex(ValueError, "base"):
            POLICY.verify_bundle(self.bundle({"feature": b"safe manifest"}))


if __name__ == "__main__":
    unittest.main()
