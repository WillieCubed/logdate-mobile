"""Submit the just-uploaded TestFlight build to App Store review.

Sequence:
  1. Mint an ES256 JWT for the App Store Connect API.
  2. Look up our app by bundle ID.
  3. Find the build matching MARKETING_VERSION + CURRENT_PROJECT_VERSION.
  4. Wait for Apple's processing pipeline to mark the build VALID.
  5. Find or create an appStoreVersion for MARKETING_VERSION on IOS.
  6. Attach the build to that appStoreVersion.
  7. POST appStoreVersionSubmissions to put the build in review.

Each HTTP call uses a freshly minted JWT with a 20-minute lifetime —
short-lived per Apple's recommendation — and bails on the first
non-2xx response so a partial submission doesn't fail silently.
"""

from __future__ import annotations

import os
import sys
import time
from typing import Any

import jwt  # PyJWT
import requests

ASC_BASE = "https://api.appstoreconnect.apple.com/v1"
JWT_LIFETIME_SECONDS = 20 * 60  # Apple's hard cap is 20 min.


def env(name: str, *, required: bool = True, default: str | None = None) -> str:
    value = os.environ.get(name, default)
    if required and not value:
        sys.exit(f"[error] {name} is required.")
    return value or ""


def make_jwt(*, key_id: str, issuer_id: str, p8_content: str) -> str:
    now = int(time.time())
    headers = {"alg": "ES256", "kid": key_id, "typ": "JWT"}
    payload = {
        "iss": issuer_id,
        "iat": now,
        "exp": now + JWT_LIFETIME_SECONDS,
        "aud": "appstoreconnect-v1",
    }
    return jwt.encode(payload, p8_content, algorithm="ES256", headers=headers)


def auth_headers(token: str) -> dict[str, str]:
    return {"Authorization": f"Bearer {token}", "Content-Type": "application/json"}


def asc_get(path: str, *, token: str, params: dict[str, Any] | None = None) -> dict[str, Any]:
    url = f"{ASC_BASE}{path}"
    response = requests.get(url, headers=auth_headers(token), params=params, timeout=30)
    if not response.ok:
        sys.exit(
            f"[error] GET {url} → HTTP {response.status_code}\n{response.text}"
        )
    return response.json()


def asc_post(path: str, *, token: str, body: dict[str, Any]) -> dict[str, Any]:
    url = f"{ASC_BASE}{path}"
    response = requests.post(url, headers=auth_headers(token), json=body, timeout=30)
    if response.status_code == 409:
        # The submission already exists — treat as success so reruns are
        # idempotent. Apple returns the existing object in the body.
        print(f"[ok] POST {url} → 409 (already exists, treating as success)")
        return response.json() if response.text else {}
    if not response.ok:
        sys.exit(
            f"[error] POST {url} → HTTP {response.status_code}\n{response.text}"
        )
    return response.json()


def asc_patch(path: str, *, token: str, body: dict[str, Any]) -> dict[str, Any]:
    url = f"{ASC_BASE}{path}"
    response = requests.patch(url, headers=auth_headers(token), json=body, timeout=30)
    if not response.ok:
        sys.exit(
            f"[error] PATCH {url} → HTTP {response.status_code}\n{response.text}"
        )
    return response.json() if response.text else {}


def find_app_id(*, token: str, bundle_id: str) -> str:
    response = asc_get("/apps", token=token, params={"filter[bundleId]": bundle_id})
    apps = response.get("data", [])
    if not apps:
        sys.exit(f"[error] No App Store Connect app found for bundle ID '{bundle_id}'.")
    return apps[0]["id"]


def find_build(
    *, token: str, app_id: str, marketing_version: str, build_number: str
) -> dict[str, Any]:
    response = asc_get(
        "/builds",
        token=token,
        params={
            "filter[app]": app_id,
            "filter[preReleaseVersion.version]": marketing_version,
            "filter[version]": build_number,
            "limit": 1,
            "sort": "-uploadedDate",
        },
    )
    data = response.get("data", [])
    if not data:
        sys.exit(
            f"[error] No build {marketing_version} ({build_number}) found for app {app_id}. "
            f"Did the upload step succeed?"
        )
    return data[0]


def wait_for_build_valid(
    *,
    token: str,
    build_id: str,
    timeout_seconds: int,
    poll_interval_seconds: int,
) -> None:
    deadline = time.monotonic() + timeout_seconds
    last_state = ""
    while time.monotonic() < deadline:
        response = asc_get(f"/builds/{build_id}", token=token)
        state = response["data"]["attributes"].get("processingState", "")
        if state != last_state:
            print(f"[ok] Build {build_id} processingState={state}")
            last_state = state
        if state == "VALID":
            return
        if state in {"FAILED", "INVALID"}:
            sys.exit(f"[error] Build {build_id} processingState={state}; cannot submit.")
        time.sleep(poll_interval_seconds)
    sys.exit(
        f"[error] Build {build_id} never reached VALID within {timeout_seconds}s. "
        f"Last state: {last_state!r}."
    )


def find_or_create_app_store_version(
    *, token: str, app_id: str, marketing_version: str
) -> str:
    response = asc_get(
        f"/apps/{app_id}/appStoreVersions",
        token=token,
        params={
            "filter[platform]": "IOS",
            "filter[versionString]": marketing_version,
            "limit": 1,
        },
    )
    existing = response.get("data", [])
    if existing:
        version_id = existing[0]["id"]
        print(f"[ok] Reusing existing appStoreVersion {version_id} for {marketing_version}")
        return version_id

    created = asc_post(
        "/appStoreVersions",
        token=token,
        body={
            "data": {
                "type": "appStoreVersions",
                "attributes": {
                    "platform": "IOS",
                    "versionString": marketing_version,
                    "releaseType": "AFTER_APPROVAL",
                },
                "relationships": {
                    "app": {"data": {"type": "apps", "id": app_id}},
                },
            }
        },
    )
    version_id = created["data"]["id"]
    print(f"[ok] Created appStoreVersion {version_id} for {marketing_version}")
    return version_id


def attach_build(*, token: str, app_store_version_id: str, build_id: str) -> None:
    asc_patch(
        f"/appStoreVersions/{app_store_version_id}/relationships/build",
        token=token,
        body={"data": {"type": "builds", "id": build_id}},
    )
    print(f"[ok] Attached build {build_id} to appStoreVersion {app_store_version_id}")


def submit_for_review(*, token: str, app_store_version_id: str) -> None:
    asc_post(
        "/appStoreVersionSubmissions",
        token=token,
        body={
            "data": {
                "type": "appStoreVersionSubmissions",
                "relationships": {
                    "appStoreVersion": {
                        "data": {"type": "appStoreVersions", "id": app_store_version_id}
                    }
                },
            }
        },
    )
    print(f"[ok] Submitted appStoreVersion {app_store_version_id} for review")


def main() -> None:
    key_id = env("APP_STORE_CONNECT_API_KEY_ID")
    issuer_id = env("APP_STORE_CONNECT_API_ISSUER_ID")
    p8_content = env("APP_STORE_CONNECT_API_KEY_P8")
    bundle_id = env("IOS_BUNDLE_ID", default="studio.hypertext.LogDate")
    marketing_version = env("MARKETING_VERSION")
    build_number = env("CURRENT_PROJECT_VERSION")
    timeout_seconds = int(env("SUBMIT_TIMEOUT_SECONDS", required=False, default="1200"))
    poll_interval_seconds = int(
        env("SUBMIT_POLL_INTERVAL_SECONDS", required=False, default="30")
    )

    token = make_jwt(key_id=key_id, issuer_id=issuer_id, p8_content=p8_content)
    app_id = find_app_id(token=token, bundle_id=bundle_id)
    print(f"[ok] App ID: {app_id}")

    build = find_build(
        token=token,
        app_id=app_id,
        marketing_version=marketing_version,
        build_number=build_number,
    )
    build_id = build["id"]
    print(f"[ok] Build ID: {build_id} ({marketing_version}, {build_number})")

    wait_for_build_valid(
        token=token,
        build_id=build_id,
        timeout_seconds=timeout_seconds,
        poll_interval_seconds=poll_interval_seconds,
    )

    # Tokens expire after 20 minutes; mint a fresh one for the
    # post-processing API calls so a long processing wait can't strand us.
    token = make_jwt(key_id=key_id, issuer_id=issuer_id, p8_content=p8_content)

    app_store_version_id = find_or_create_app_store_version(
        token=token, app_id=app_id, marketing_version=marketing_version
    )
    attach_build(
        token=token,
        app_store_version_id=app_store_version_id,
        build_id=build_id,
    )
    submit_for_review(token=token, app_store_version_id=app_store_version_id)


if __name__ == "__main__":
    main()
