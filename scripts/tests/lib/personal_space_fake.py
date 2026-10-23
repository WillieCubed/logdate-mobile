"""A fake LogDate server for an account in personalSpaceV1 storage mode, for the deployment smoke tests.

It answers the account routes the smoke proofs use and the app.logdate.space XRPC methods with the
real server's rules: sealed envelopes only, compare-and-swap writes, tombstones in the change feed.
Every other /api/v1 route is recorded in legacy_calls and refused, as the real server refuses the
legacy sync routes to a space account.
"""

import copy
import hashlib
import json
from urllib.parse import parse_qs, urlsplit

WATCH_TOKEN = "watch-token"
WATCH_SESSION = "watch-session"
WATCH_REFRESH = "watch-refresh"
ACCOUNT_ROUTES = ("/server/info", "/auth/me", "/auth/devices", "/auth/token/refresh")


class PersonalSpaceServer:
    def __init__(self, account, username, origin, did="did:plc:smokefake", mode="personalSpaceV1",
                 features=("personalSpaceV1", "deviceLinkingV1")):
        self.account = account
        self.username = username
        self.origin = origin
        self.did = did
        self.mode = mode
        self.features = list(features)
        self.records = {}
        self.seq = 0
        self.calls = []
        self.legacy_calls = []
        self.revoked = False
        self.corruption = None

    @property
    def space(self):
        return f"at://{self.did}/space/app.logdate.space.personal/self"

    def request(self, method, url, headers, body):
        self.calls.append((method, url, dict(headers), copy.deepcopy(body)))
        parts = urlsplit(url)
        if parts.path.startswith("/xrpc/app.logdate.space."):
            return self.xrpc(method, parts.path.rsplit(".", 1)[-1], parse_qs(parts.query), body, headers)
        path = parts.path.removeprefix("/api/v1")
        is_watch = headers.get("Authorization") == "Bearer " + WATCH_TOKEN
        if path == "/server/info":
            return 200, {"success": True, "data": {"serverOrigin": self.origin, "protocolFeatures": self.features}}
        if path == "/auth/me":
            account = {"id": self.account, "username": self.username, "did": self.did, "storageMode": self.mode}
            return 200, {"success": True, "data": {"account": {k: v for k, v in account.items() if v is not None}}}
        if path == "/auth/devices/link" and method == "POST":
            return (403, {}) if is_watch else (200, {"code": "LINK-CODE"})
        if path == "/auth/devices/link/redeem":
            return 200, {"accountId": self.account, "sessionId": WATCH_SESSION,
                         "tokens": {"accessToken": WATCH_TOKEN, "refreshToken": WATCH_REFRESH}}
        if path == "/auth/devices":
            return 200, {"devices": [] if self.revoked else [{"id": WATCH_SESSION}]}
        if path == "/auth/devices/" + WATCH_SESSION and method == "DELETE":
            self.revoked = True
            return 204, {}
        if path == "/auth/token/refresh":
            return (401, {}) if self.revoked and self.corruption != "refresh-ignored" else (200, {"success": True})
        self.legacy_calls.append((method, path))
        return 403, {"error": {"code": "SYNC_STORAGE_MODE_MISMATCH"}}

    def xrpc(self, method, name, query, body, headers):
        if headers.get("Authorization") == "Bearer " + WATCH_TOKEN and self.revoked and self.corruption != "revocation-ignored":
            return 401, {"error": "AuthenticationRequired", "message": "Session revoked"}
        body = body or {}
        space = body.get("space") or query.get("space", [None])[0]
        if space != self.space:
            return 400, {"error": "RecordNotFound", "message": "Not found"}
        rkey = body.get("rkey") or query.get("rkey", [None])[0]
        live = self.records.get(rkey)
        live = live if live and not live["deleted"] else None
        if name == "putRecord":
            return self.put(rkey, live, body)
        if name == "getRecord":
            stored = self.records.get(rkey)
            if stored is None or (stored["deleted"] and self.corruption != "deleted-readable"):
                return 400, {"error": "RecordNotFound", "message": "Not found"}
            value = {k: v for k, v in stored["record"].items() if k != "$type"}
            if self.corruption == "value":
                value["keyId"] = "other-key"
            return 200, {"cid": stored["cid"], "seq": stored["seq"], "value": value}
        if name == "deleteRecord":
            if live is not None and body.get("swapCid") not in (None, live["cid"]):
                return 409, {"error": "InvalidSwap", "message": "The record changed"}
            if live is not None:
                self.seq += 1
                live.update(deleted=True, seq=self.seq)
            return 200, {"seq": self.seq}
        if name == "listRepoOps":
            ops = [{"cid": r["cid"], "deleted": r["deleted"], "rkey": key, "seq": r["seq"]}
                   for key, r in sorted(self.records.items(), key=lambda item: item[1]["seq"])
                   if not (r["deleted"] and self.corruption == "tombstone")]
            return 200, {"cursor": str(self.seq), "hasMore": False, "ops": ops}
        raise AssertionError(f"Unexpected space method {name}")

    def put(self, rkey, live, body):
        problem = sealed_problem(body.get("record"))
        if problem:
            return 400, {"error": "InvalidRecord", "message": problem}
        swap = body.get("swapCid")
        if self.corruption != "swap-ignored" and (
                (body.get("createOnly") and live is not None) or (swap is not None and (live or {}).get("cid") != swap)):
            return 409, {"error": "InvalidSwap", "message": "The record changed"}
        self.seq += 1
        digest = hashlib.sha256(json.dumps(body["record"], sort_keys=True).encode()).hexdigest()
        self.records[rkey] = {"cid": "bafy" + digest[:40], "record": body["record"], "seq": self.seq, "deleted": False}
        return 200, {"cid": self.records[rkey]["cid"], "seq": self.seq}


def sealed_problem(record):
    """The reason the server would refuse [record] as a sealed envelope, or None."""
    if not isinstance(record, dict) or record.get("$type") != "app.logdate.crypto.sealed":
        return "Only app.logdate.crypto.sealed records can be stored in a space"
    if record.get("version") != 1 or not 0 < len(record.get("keyId", "")) <= 128:
        return "Unsupported envelope version or key ID"
    if len(record["nonce"]["$bytes"]) * 3 // 4 != 12:
        return "nonce must be 12 bytes"
    if len(record["ciphertext"]["$bytes"]) * 3 // 4 != 528:
        return "ciphertext isn't padded to a size bucket"
    return None
