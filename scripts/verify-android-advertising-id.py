#!/usr/bin/env python3
"""Reject advertising-ID collection or permissions in Android release artifacts."""

import argparse
from pathlib import Path
import xml.etree.ElementTree as ET
from zipfile import ZipFile

ANDROID = "{http://schemas.android.com/apk/res/android}"
PERMISSIONS = (
    "com.google.android.gms.permission.AD_ID",
    "android.permission.ACCESS_ADSERVICES_AD_ID",
)


def verify_manifest(path, require_firebase=False):
    root = ET.parse(path).getroot()
    for node in root:
        if (
            node.tag.startswith("uses-permission")
            and node.get(ANDROID + "name") in PERMISSIONS
        ):
            raise ValueError(
                f"Advertising-ID permission remains in {path}: {node.get(ANDROID + 'name')}"
            )
    application = root.find("application")
    if application is None:
        raise ValueError(f"Application is missing from {path}")
    metadata = {
        node.get(ANDROID + "name"): node.get(ANDROID + "value")
        for node in application.findall("meta-data")
    }
    if metadata.get("google_analytics_adid_collection_enabled") != "false":
        raise ValueError(
            f"Advertising-ID collection must be explicitly disabled in {path}"
        )
    for name in (
        "firebase_analytics_collection_enabled",
        "firebase_crashlytics_collection_enabled",
        "firebase_data_collection_default_enabled",
    ):
        if metadata.get(name) == "false":
            raise ValueError(
                f"Analytics or Crashlytics collection was disabled by {name}"
            )
    if metadata.get("firebase_analytics_collection_deactivated") == "true":
        raise ValueError("Analytics collection must not be deactivated")
    if require_firebase:
        names = {
            node.get(ANDROID + "name", "") for node in application.iter("meta-data")
        }
        for suffix in (
            "analytics.connector.internal.AnalyticsConnectorRegistrar",
            "crashlytics.CrashlyticsRegistrar",
        ):
            if not any(name.endswith(suffix) for name in names):
                raise ValueError(f"Firebase component is missing: {suffix}")


def verify_bundle(path):
    with ZipFile(path) as bundle:
        manifests = sorted(
            name
            for name in bundle.namelist()
            if name.endswith("/manifest/AndroidManifest.xml")
        )
        if "base/manifest/AndroidManifest.xml" not in manifests:
            raise ValueError("The base module manifest is missing")
        for name in manifests:
            data = bundle.read(name)
            # AAB manifest protobuf attributes preserve permission names as UTF-8 strings.
            for permission in PERMISSIONS:
                if permission.encode("utf-8") in data:
                    raise ValueError(
                        f"Advertising-ID permission remains in {name}: {permission}"
                    )
        return [name.split("/", 1)[0] for name in manifests]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--bundle", type=Path, required=True)
    parser.add_argument("--manifest-dir", type=Path, required=True)
    parser.add_argument("--require-firebase", action="store_true")
    args = parser.parse_args()
    manifests = sorted(args.manifest_dir.rglob("AndroidManifest.xml"))
    if not manifests:
        parser.error("Merged release manifest is missing")
    try:
        for manifest in manifests:
            verify_manifest(manifest, args.require_firebase)
        modules = verify_bundle(args.bundle)
    except (ValueError, OSError, ET.ParseError) as error:
        parser.exit(1, f"Advertising-ID release policy failed: {error}\n")
    print(
        f"Verified {args.bundle.name}: advertising-ID collection disabled; no advertising-ID permissions in {', '.join(modules)}"
    )


if __name__ == "__main__":
    main()
