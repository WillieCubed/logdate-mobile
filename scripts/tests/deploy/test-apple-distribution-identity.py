"""Keep Apple builds and distribution pointed at the same registered app."""

import pathlib
import plistlib
import re
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[3]
APP_ID = "studio.hypertext.apps.logdate"
TEAM_ID = "T95VDD3A4W"


class AppleDistributionIdentityTests(unittest.TestCase):
    def test_ios_signing_and_export_use_the_same_app(self):
        config = (ROOT / "iosApp/Configuration/Config.xcconfig").read_text()
        settings = dict(re.findall(r"^(\w+)\s*=\s*(\S+)", config, re.MULTILINE))
        self.assertEqual(settings["BUNDLE_ID"], APP_ID)
        self.assertEqual(settings["TEAM_ID"], TEAM_ID)
        project = (ROOT / "iosApp/iosApp.xcodeproj/project.pbxproj").read_text()
        self.assertEqual(set(re.findall(r"PRODUCT_BUNDLE_IDENTIFIER = ([^;]+);", project)), {'"${BUNDLE_ID}"'})
        self.assertEqual(set(re.findall(r"DEVELOPMENT_TEAM = ([^;]+);", project)), {TEAM_ID})
        with (ROOT / "iosApp/Configuration/ExportOptions.plist").open("rb") as source:
            export = plistlib.load(source)
        self.assertEqual(export["teamID"], TEAM_ID)
        self.assertEqual(set(export["provisioningProfiles"]), {APP_ID})

    def test_background_sync_registration_matches_the_permitted_task(self):
        with (ROOT / "iosApp/iosApp/Info.plist").open("rb") as source:
            info = plistlib.load(source)
        delegate = (ROOT / "iosApp/iosApp/AppDelegate.swift").read_text()
        task = re.search(r'syncTaskIdentifier = "([^"]+)"', delegate).group(1)
        self.assertEqual(task, APP_ID + ".sync.refresh")
        self.assertEqual(info["BGTaskSchedulerPermittedIdentifiers"], [task])
        self.assertEqual(info["CFBundleIdentifier"], "$(PRODUCT_BUNDLE_IDENTIFIER)")
        self.assertEqual(info["CFBundleURLTypes"][0]["CFBundleURLName"], APP_ID)
        self.assertTrue({"studio.hypertext.logdate", "logdate"}.issubset(info["CFBundleURLTypes"][0]["CFBundleURLSchemes"]))

    def test_compose_framework_and_mac_distribution_have_explicit_ids(self):
        gradle = (ROOT / "app/compose-main/build.gradle.kts").read_text()
        self.assertEqual(re.findall(r'bundleID = "([^"]+)"', gradle), [APP_ID])
        self.assertEqual(re.findall(r'binaryOption\("bundleId", "([^"]+)"\)', gradle), [APP_ID + ".compose"])

    def test_submission_defaults_and_profile_rendering_match_the_app(self):
        workflow = (ROOT / ".github/workflows/publish-ios-app-store.yml").read_text()
        self.assertEqual(re.findall(r"Set :provisioningProfiles:(\S+) ", workflow), [APP_ID, APP_ID])
        self.assertEqual(re.findall(r"IOS_BUNDLE_ID: (\S+)", workflow), [APP_ID])
        submission = (ROOT / "scripts/submit-ios-for-review.py").read_text()
        self.assertIn(f'env("IOS_BUNDLE_ID", default="{APP_ID}")', submission)

    def test_ci_provides_the_registered_firebase_identity(self):
        materializer = (ROOT / ".github/actions/setup-firebase-configs/action.yml").read_text()
        self.assertIn("LOGDATE_FIREBASE_IOS_APP_ID: ${{ inputs.ios-firebase-app-id }}", materializer)
        for name in ["publish-ios-app-store.yml", "screenshot-test.yml"]:
            with self.subTest(workflow=name):
                workflow = (ROOT / ".github/workflows" / name).read_text()
                self.assertEqual(
                    workflow.count("ios-google-service-info-plist-base64:"),
                    workflow.count("ios-firebase-app-id: ${{ vars.LOGDATE_FIREBASE_IOS_APP_ID }}"),
                )


if __name__ == "__main__":
    unittest.main()
