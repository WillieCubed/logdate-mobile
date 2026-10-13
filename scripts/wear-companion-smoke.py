#!/usr/bin/env python3
"""Prove linked-watch cloud journeys with the deployment's disposable passkey account."""
import argparse
import importlib.util
import time
import uuid
from pathlib import Path

_spec = importlib.util.spec_from_file_location("deployment_smoke", Path(__file__).with_name("journal-merge-smoke.py"))
common = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(common)


class WearProbe(common.Probe):
    def verify_identity(self):
        super().verify_identity()
        descriptor = self.request("GET", "/server/info")["data"]
        required = {"deviceLinkingV1", "conditionalCreateV1", "momentsV1"}
        if not required.issubset(descriptor.get("protocolFeatures", [])):
            raise common.SmokeError("Deployed server is missing a Wear companion protocol")

    def as_watch(self, fixture):
        watch = WearProbe(self.base, self.state, self.transport, None)
        watch.headers = dict(self.headers)
        watch.headers["Authorization"] = "Bearer " + fixture["watchToken"]
        return watch

    def prepare(self, fixture):
        code = self.request("POST", "/auth/devices/link")["code"]
        linked = self.request("POST", "/auth/devices/link/redeem", {
            "code": code, "deviceName": "Disposable Wear deployment proof", "platform": "wear_os"})
        if linked.get("accountId") != self.state["accountId"]:
            raise common.SmokeError("Linked watch owner differs from disposable phone owner")
        fixture.update(sessionId=linked["sessionId"], watchToken=linked["tokens"]["accessToken"],
                       watchRefreshToken=linked["tokens"]["refreshToken"])
        watch = self.as_watch(fixture)
        watch.request("POST", "/auth/devices/link", statuses=(403,))
        watch.headers["If-None-Match"] = "*"
        watch.request("PUT", "/contents/" + fixture["capture"]["id"] + "?ifAbsent=true", fixture["capture"], (201,))
        watch.request("PUT", "/contents/" + fixture["capture"]["id"] + "?ifAbsent=true", fixture["capture"], (412,))
        watch.request("HEAD", "/contents/" + fixture["capture"]["id"])
        watch.request("PUT", "/moments/" + fixture["moment"]["id"] + "?ifAbsent=true", fixture["moment"], (201,))
        watch.request("PUT", "/moments/" + fixture["moment"]["id"] + "?ifAbsent=true", fixture["moment"], (412,))
        watch.headers.pop("If-None-Match")
        self.verify_records(fixture)

    def verify_records(self, fixture):
        for collection, key, payload in (("contents", "capture", "content"), ("moments", "moment", "payload")):
            expected = fixture[key]
            actual = self.request("GET", "/" + collection + "/" + expected["id"])
            if actual.get(payload) != expected[payload]:
                raise common.SmokeError("Phone did not receive unchanged encrypted watch content")
            changes = self.request("GET", "/" + collection + "?since=0&limit=100")
            if not any(item.get("id") == expected["id"] for item in changes.get("changes", [])):
                raise common.SmokeError("Watch content is missing from its durable sync index")
        devices = self.request("GET", "/auth/devices")["devices"]
        if not any(item.get("id") == fixture["sessionId"] for item in devices):
            raise common.SmokeError("Linked watch session was not retained")

    def finish(self, fixture):
        self.verify_records(fixture)
        watch = self.as_watch(fixture)
        moment = self.request("GET", "/moments/" + fixture["moment"]["id"])
        update = {"payload": "LDSE2:deployment-caption", "lastUpdated": int(time.time() * 1000),
                  "deviceId": "deployment-phone", "versionConstraint": {"type": "known", "serverVersion": moment["serverVersion"]}}
        self.request("PATCH", "/moments/" + moment["id"], update)
        for reader in (self, watch):
            changed = reader.request("GET", "/moments/" + moment["id"])
            if changed.get("payload") != update["payload"]:
                raise common.SmokeError("Caption was not durably visible to both phone and watch")
        self.request("PATCH", "/moments/" + moment["id"], update, (409,))
        self.request("DELETE", "/moments/" + moment["id"], statuses=(204,))
        watch.request("GET", "/moments/" + moment["id"], statuses=(404,))
        watch.headers["If-None-Match"] = "*"
        watch.request("PUT", "/moments/" + moment["id"] + "?ifAbsent=true", fixture["moment"], (410,))
        deleted = self.request("GET", "/moments?since=0&limit=100").get("deletions", [])
        if not any(item.get("id") == moment["id"] for item in deleted):
            raise common.SmokeError("Moment deletion is missing its sync tombstone")
        self.request("DELETE", "/auth/devices/" + fixture["sessionId"], statuses=(204,))
        remaining = self.request("GET", "/auth/devices")["devices"]
        if any(item.get("id") == fixture["sessionId"] for item in remaining):
            raise common.SmokeError("Revoked watch remained in the linked device list")
        watch.request("GET", "/contents/" + fixture["capture"]["id"], statuses=(401,))
        watch.request("POST", "/auth/token/refresh", {"refreshToken": fixture["watchRefreshToken"]}, (401,))
        self.request("GET", "/contents/" + fixture["capture"]["id"])


def fixture():
    now = int(time.time() * 1000)
    return {"capture": {"id": str(uuid.uuid4()), "type": "TEXT", "content": "LDSE2:deployment-capture",
                        "mediaUri": None, "createdAt": now, "lastUpdated": now, "deviceId": "deployment-watch"},
            "moment": {"id": str(uuid.uuid4()), "capturedAt": now, "lastUpdated": now,
                       "payload": "LDSE2:deployment-mark", "deviceId": "deployment-watch", "schema": 1}}


def run(base, state_file, phase, private_token_file=None, transport=None):
    state_file = Path(state_file)
    state = common.load_bound_state(base, state_file, phase)
    probe = WearProbe(base, state, transport or common.http_request, private_token_file)
    probe.verify_identity()
    saved = state.get("wearCompanionSmoke")
    if phase == "prepare":
        if saved is not None:
            raise common.SmokeError("Wear fixture was already prepared")
        saved = fixture()
        state["wearCompanionSmoke"] = saved
        common.write_state(state_file, state)
        try:
            probe.prepare(saved)
        finally:
            common.write_state(state_file, state)
    else:
        if saved is None or "watchToken" not in saved:
            raise common.SmokeError("Wear fixture was not prepared on the candidate")
        probe.finish(saved)
        saved.pop("watchToken")
        saved.pop("watchRefreshToken")
        saved["verified"] = True
        common.write_state(state_file, state)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base", required=True)
    parser.add_argument("--state-file", type=Path, required=True)
    parser.add_argument("--phase", choices=("prepare", "verify"), required=True)
    parser.add_argument("--private-service-token-file", type=Path)
    args = parser.parse_args()
    try:
        run(args.base, args.state_file, args.phase, args.private_service_token_file)
    except (common.SmokeError, ValueError, KeyError, TypeError, OSError) as error:
        message = str(error) if isinstance(error, common.SmokeError) else "Invalid private Wear smoke state"
        parser.exit(1, f"[FAIL] {message}\n")
    print(f"Wear companion {args.phase} proof passed")


if __name__ == "__main__":
    main()
