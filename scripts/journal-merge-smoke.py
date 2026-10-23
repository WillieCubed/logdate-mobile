#!/usr/bin/env python3
"""Verify journal merges using the deployment's disposable passkey account."""

import argparse
import base64
import http.client
import json
import os
import re
import secrets
import ssl
import stat
import sys
import tempfile
import time
import uuid
from pathlib import Path
from urllib.parse import urlencode, urlsplit

REQUEST_TIMEOUT_SECONDS = 30
RESPONSE_LIMIT = 2 * 1024 * 1024
# Bounds retries of requests that provably never reached the server: about 23 seconds.
TRANSPORT_ATTEMPTS = 6

STORAGE_MODES = ("legacy", "personalSpaceV1")
SPACE_TYPE = "app.logdate.space.personal"
SEALED_TYPE = "app.logdate.crypto.sealed"
# The smallest padded size bucket the server accepts for a sealed record (512 bytes) plus the
# 16-byte authentication tag. The server checks the envelope's shape, not that it decrypts.
SEALED_CIPHERTEXT_BYTES = 528
SEALED_NONCE_BYTES = 12


class SmokeError(RuntimeError):
    pass


def private_text(path):
    path = Path(path)
    metadata = path.lstat()
    if not stat.S_ISREG(metadata.st_mode) or stat.S_IMODE(metadata.st_mode) != 0o600:
        raise SmokeError("Smoke state and credentials must be regular 0600 files")
    return path.read_text()


def origin(value):
    parsed = urlsplit(value)
    if (parsed.scheme != "https" or not parsed.hostname or parsed.username or parsed.password
            or parsed.path or parsed.query or parsed.fragment or value != value.strip()):
        raise SmokeError("Smoke origin must be an origin-only HTTPS URL")
    return value


def write_state(path, state):
    descriptor, temporary = tempfile.mkstemp(prefix=".journal-merge-", dir=path.parent)
    try:
        with os.fdopen(descriptor, "w") as output:
            json.dump(state, output)
            output.flush()
            os.fsync(output.fileno())
        os.replace(temporary, path)
        directory = os.open(path.parent, os.O_RDONLY)
        try:
            os.fsync(directory)
        finally:
            os.close(directory)
    finally:
        if os.path.exists(temporary):
            os.unlink(temporary)


def connect(url):
    parsed = urlsplit(url)
    connection = http.client.HTTPSConnection(
        parsed.hostname, parsed.port, timeout=REQUEST_TIMEOUT_SECONDS, context=ssl.create_default_context())
    connection.connect()
    return connection


def http_request(method, url, headers, body, connect=connect, sleep=time.sleep):
    """Returns (status, payload, request ID) for responses from the LogDate server itself.

    The server stamps every response with X-Request-ID. A response without it came from the Cloud
    Run front end: a 404 means a new revision URL is not routed everywhere yet, so the request is
    retried. Connection failures before sending are retried too. Anything after the request was
    sent fails immediately, so a server that hangs or errors is reported rather than retried.
    """
    parsed = urlsplit(url)
    if parsed.scheme != "https" or not parsed.hostname:
        raise SmokeError("Smoke requests must use HTTPS")
    target = parsed.path + ("?" + parsed.query if parsed.query else "")
    described = f"{method} {parsed.path.removeprefix('/api/v1')}"
    encoded = None if body is None else json.dumps(body).encode()
    for attempt in range(1, TRANSPORT_ATTEMPTS + 1):
        try:
            connection = connect(url)
        except OSError as error:
            problem = f"could not connect ({type(error).__name__})"
        else:
            try:
                connection.request(method, target, body=encoded, headers=headers)
                response = connection.getresponse()
                raw = response.read(RESPONSE_LIMIT + 1)
            except TimeoutError:
                raise SmokeError(f"{described} did not respond within {REQUEST_TIMEOUT_SECONDS} seconds") from None
            except (OSError, http.client.HTTPException) as error:
                raise SmokeError(f"{described} lost its connection before a response ({type(error).__name__})") from None
            finally:
                connection.close()
            if 300 <= response.status < 400:
                raise SmokeError(f"{described} attempted a redirect")
            if len(raw) > RESPONSE_LIMIT:
                raise SmokeError(f"{described} response exceeded its size limit")
            request_id = response.getheader("X-Request-ID")
            if request_id is not None:
                return response.status, decode(described, response.status, raw), request_id
            problem = f"returned HTTP {response.status} from the Cloud Run front end, not the server"
            if response.status != 404:
                raise SmokeError(f"{described} {problem}")
        if attempt == TRANSPORT_ATTEMPTS:
            raise SmokeError(f"{described} {problem} (after {attempt} attempts)")
        print(f"Retrying {described}: {problem} (attempt {attempt} of {TRANSPORT_ATTEMPTS})", file=sys.stderr)
        sleep(min(2 ** (attempt - 1), 8))


def decode(described, status, raw):
    try:
        return json.loads(raw) if raw else {}
    except (ValueError, UnicodeError):
        if status == 404:
            return {}
        raise SmokeError(f"{described} returned invalid JSON") from None


def load_bound_state(base, state_file, phase):
    state = json.loads(private_text(state_file))
    if phase not in ("prepare", "verify", "recover") or state.get("format") != "logdate-deploy-smoke-v2":
        raise SmokeError("Invalid deployment smoke state or phase")
    if str(uuid.UUID(state["accountId"])) != state["accountId"]:
        raise SmokeError("Invalid disposable account identity")
    expected_base = state["canonicalOrigin"] if phase == "verify" else state["preparedBaseUrl"]
    if origin(base) != origin(expected_base):
        raise SmokeError("Smoke origin does not match prepared deployment state")
    credential = json.loads(private_text(state["credentialFile"]))
    for key in ("accountId", "username", "expectedRpId", "credentialId", "userHandle"):
        if not state.get(key) or credential.get(key) != state[key]:
            raise SmokeError("Passkey identity does not match deployment state")
    if credential.get("origin") != state.get("webauthnOrigin", state["canonicalOrigin"]):
        raise SmokeError("Passkey origin does not match deployment state")
    return state


class Probe:
    label = "Journal merge smoke"

    def __init__(self, base, state, transport, private_token_file):
        self.base = base
        self.state = state
        self.transport = transport
        token = state["accessToken"]
        if not re.fullmatch(r"[A-Za-z0-9._~-]+", token):
            raise SmokeError("Malformed smoke access token")
        self.headers = {"Authorization": f"Bearer {token}", "Content-Type": "application/json"}
        self.storage_mode = None
        self.did = None
        if private_token_file:
            token = private_text(private_token_file).strip()
            if not re.fullmatch(r"[A-Za-z0-9._~-]+", token):
                raise SmokeError("Malformed private candidate token")
            self.headers["X-Serverless-Authorization"] = f"Bearer {token}"

    @property
    def space(self):
        return self.storage_mode == "personalSpaceV1"

    def request(self, method, path, body=None, statuses=(200,), prefix="/api/v1"):
        try:
            status, payload, *request_id = self.transport(method, self.base + prefix + path, self.headers, body)
        except SmokeError as error:
            raise SmokeError(f"{self.label} {error}") from None
        if status not in statuses:
            correlation = f" (request {request_id[0]})" if request_id else ""
            raise SmokeError(f"{self.label} {method} {path.split('?')[0]} returned HTTP {status}{correlation}")
        return payload

    def required_features(self):
        return ("personalSpaceV1",) if self.space else ("journalMergeV1",)

    def verify_identity(self):
        descriptor = self.request("GET", "/server/info").get("data", {})
        if descriptor.get("serverOrigin") != self.state["canonicalOrigin"]:
            raise SmokeError("Server descriptor origin does not match deployment state")
        account = self.request("GET", "/auth/me").get("data", {}).get("account", {})
        if account.get("id") != self.state["accountId"] or account.get("username") != self.state["username"]:
            raise SmokeError("Authenticated disposable account identity does not match state")
        self.read_storage_mode(account)
        for feature in self.required_features():
            if feature not in descriptor.get("protocolFeatures", []):
                raise SmokeError(f"Deployed server does not advertise {feature}")

    def read_storage_mode(self, account):
        mode = account.get("storageMode")
        if mode is None:
            raise SmokeError("/auth/me did not report a storageMode for the account")
        if mode not in STORAGE_MODES:
            shown = re.sub(r"[^A-Za-z0-9_.-]", "?", str(mode))[:40]
            raise SmokeError(f"Account storageMode '{shown}' is not covered by this smoke test "
                             "(expected legacy or personalSpaceV1)")
        self.storage_mode = mode
        if self.space:
            self.did = account.get("did")
            if not isinstance(self.did, str) or not re.fullmatch(r"did:[a-z]+:[A-Za-z0-9._:%-]+", self.did):
                raise SmokeError("/auth/me did not report a DID, so the account's personal space cannot be addressed")

    def space_uri(self):
        return f"at://{self.did}/space/{SPACE_TYPE}/self"

    def xrpc(self, method, name, body=None, query=None, statuses=(200,)):
        path = "/app.logdate.space." + name + ("?" + urlencode(query) if query else "")
        return self.request(method, path, body, statuses, prefix="/xrpc")

    def put_record(self, rkey, record, swap_cid=None, create_only=False, statuses=(200,)):
        body = {"space": self.space_uri(), "rkey": rkey, "record": record}
        if swap_cid:
            body["swapCid"] = swap_cid
        if create_only:
            body["createOnly"] = True
        return self.xrpc("POST", "putRecord", body, statuses=statuses)

    def get_record(self, rkey, statuses=(200,)):
        return self.xrpc("GET", "getRecord", query={"space": self.space_uri(), "rkey": rkey}, statuses=statuses)

    def delete_record(self, rkey, swap_cid=None):
        body = {"space": self.space_uri(), "rkey": rkey}
        if swap_cid:
            body["swapCid"] = swap_cid
        return self.xrpc("POST", "deleteRecord", body)

    def require_error(self, payload, code, action):
        if payload.get("error") != code:
            shown = re.sub(r"[^A-Za-z0-9_.-]", "?", str(payload.get("error")))[:40]
            raise SmokeError(f"{self.label} {action} answered '{shown}' instead of {code}")

    def put_refused(self, rkey, record, swap_cid=None, create_only=False):
        payload = self.put_record(rkey, record, swap_cid, create_only, statuses=(409,))
        self.require_error(payload, "InvalidSwap", "a stale or duplicate space record write")

    def require_missing(self, rkey):
        self.require_error(self.get_record(rkey, statuses=(400,)), "RecordNotFound", "a deleted space record read")

    def list_ops(self):
        page = self.xrpc("GET", "listRepoOps", query={"space": self.space_uri(), "since": 0, "limit": 200})
        if page.get("hasMore"):
            raise SmokeError("Disposable space fixture unexpectedly exceeded one page")
        return {op.get("rkey"): op for op in page.get("ops", [])}

    def require_record(self, rkey, record, cid=None):
        stored = self.get_record(rkey)
        if not same_sealed(stored.get("value"), record) or (cid is not None and stored.get("cid") != cid):
            raise SmokeError("Space record does not match what was written")
        return stored

    def prepare_space(self, fixture):
        for key in ("source", "destination"):
            fixture[key]["cid"] = self.put_record(fixture[key]["rkey"], fixture[key]["record"], create_only=True)["cid"]
        self.put_refused(fixture["destination"]["rkey"], fixture["destination"]["record"], create_only=True)
        self.merge(fixture)
        self.merge(fixture)

    def merge_space(self, fixture):
        source, destination = fixture["source"], fixture["destination"]
        if "mergedCid" in fixture:
            self.put_refused(destination["rkey"], fixture["merged"], swap_cid=destination["cid"])
            self.delete_record(source["rkey"])
            return
        merged = self.put_record(destination["rkey"], fixture["merged"], swap_cid=destination["cid"])
        fixture["mergedCid"] = merged["cid"]
        self.delete_record(source["rkey"], swap_cid=source["cid"])

    def verify_space(self, fixture):
        source, destination = fixture["source"], fixture["destination"]
        self.require_record(destination["rkey"], fixture["merged"], fixture["mergedCid"])
        self.require_missing(source["rkey"])
        ops = self.list_ops()
        live, tombstone = ops.get(destination["rkey"]), ops.get(source["rkey"])
        if not live or live.get("deleted") or live.get("cid") != fixture["mergedCid"]:
            raise SmokeError("Merged destination is missing from the space's change feed")
        if not tombstone or not tombstone.get("deleted"):
            raise SmokeError("Merge source tombstone is missing from the space's change feed")

    def prepare(self, fixture):
        if self.space:
            return self.prepare_space(fixture)
        for journal in (fixture["source"], fixture["destination"]):
            self.request("PUT", "/journals/" + journal["id"], journal, (200, 201))
        self.request("POST", "/associations", {"associations": [{
            "journalId": fixture["source"]["id"], "contentId": fixture["remoteId"],
            "createdAt": fixture["source"]["createdAt"], "deviceId": "deployment-smoke",
        }]})
        self.merge(fixture)
        self.merge(fixture)

    def merge(self, fixture):
        if self.space:
            return self.merge_space(fixture)
        request = {"operationId": fixture["operationId"], "destinationId": fixture["destination"]["id"],
                   "contentIds": [fixture["submittedId"]]}
        result = self.request("POST", "/journals/" + fixture["source"]["id"] + "/merge", request)
        if result != {"operationId": fixture["operationId"], "sourceId": fixture["source"]["id"],
                      "destinationId": fixture["destination"]["id"]}:
            raise SmokeError("Merge retry did not preserve its operation and destination")

    def changes(self, path):
        result = self.request("GET", path + "?since=0&limit=100")
        if result.get("hasMore"):
            raise SmokeError("Disposable merge fixture unexpectedly exceeded one page")
        return result

    def verify(self, fixture):
        if self.space:
            return self.verify_space(fixture)
        expected = fixture["destination"]
        actual = self.request("GET", "/journals/" + expected["id"])
        for key in ("id", "title", "description", "createdAt", "lastUpdated"):
            if actual.get(key) != expected[key]:
                raise SmokeError("Merge changed destination metadata")
        self.request("GET", "/journals/" + fixture["source"]["id"], statuses=(404,))
        tombstones = self.changes("/journals").get("deletions", [])
        if not any(item.get("id") == fixture["source"]["id"]
                   and item.get("mergedIntoJournalId") == expected["id"] for item in tombstones):
            raise SmokeError("Merge source tombstone does not retain its destination")
        memberships = {(item["journalId"], item["contentId"])
                       for item in self.changes("/associations").get("changes", [])}
        required = {(expected["id"], fixture[key]) for key in ("remoteId", "submittedId")}
        if not required.issubset(memberships) or any(journal == fixture["source"]["id"] for journal, _ in memberships):
            raise SmokeError("Merge did not preserve remotely held and submitted raw memberships")


def sealed_record():
    def encode(size):
        return base64.b64encode(secrets.token_bytes(size)).decode().rstrip("=")
    return {"$type": SEALED_TYPE, "version": 1, "keyId": "deploy-smoke",
            "nonce": {"$bytes": encode(SEALED_NONCE_BYTES)},
            "ciphertext": {"$bytes": encode(SEALED_CIPHERTEXT_BYTES)}}


def same_sealed(actual, expected):
    """Whether two sealed records are equal; the server may leave out the default $type."""
    def body(record):
        return {key: value for key, value in record.items() if key != "$type"} if isinstance(record, dict) else None
    return body(actual) is not None and body(actual) == body(expected)


def new_fixture(storage_mode="legacy"):
    if storage_mode == "personalSpaceV1":
        suffix = uuid.uuid4().hex[:12]
        return {"storageMode": storage_mode,
                "source": {"rkey": "deploy-merge-source-" + suffix, "record": sealed_record()},
                "destination": {"rkey": "deploy-merge-destination-" + suffix, "record": sealed_record()},
                "merged": sealed_record()}
    timestamp = int(time.time() * 1000)
    def journal(title):
        return {"id": str(uuid.uuid4()), "title": title, "description": "Deployment merge fixture",
                "createdAt": timestamp, "lastUpdated": timestamp, "deviceId": "deployment-smoke"}
    return {"storageMode": storage_mode,
            "source": journal("Disposable merge source"), "destination": journal("Destination metadata stays"),
            "remoteId": str(uuid.uuid4()), "submittedId": str(uuid.uuid4()), "operationId": str(uuid.uuid4())}


def require_prepared_mode(fixture, probe):
    if fixture.get("storageMode") != probe.storage_mode:
        raise SmokeError(f"Account storageMode changed since prepare (prepared '{fixture.get('storageMode')}', "
                         f"now '{probe.storage_mode}')")


def run_probe(base, state_file, phase, private_token_file=None, transport=None):
    state_file = Path(state_file)
    state = load_bound_state(base, state_file, phase)
    if phase == "recover":
        state["recoveryMode"] = True
        write_state(state_file, state)
        return
    probe = Probe(base, state, transport or http_request, private_token_file)
    probe.verify_identity()
    fixture = state.get("journalMergeSmoke")
    if phase == "prepare":
        if fixture is not None:
            raise SmokeError("Merge fixture was already prepared")
        fixture = new_fixture(probe.storage_mode)
        state["journalMergeSmoke"] = fixture
        write_state(state_file, state)
        try:
            probe.prepare(fixture)
        finally:
            write_state(state_file, state)
    else:
        if fixture is None:
            raise SmokeError("Merge fixture was not prepared on the candidate")
        require_prepared_mode(fixture, probe)
        probe.merge(fixture)
    probe.verify(fixture)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base", required=True)
    parser.add_argument("--state-file", type=Path, required=True)
    parser.add_argument("--phase", choices=("prepare", "verify", "recover"), required=True)
    parser.add_argument("--private-service-token-file", type=Path)
    args = parser.parse_args()
    try:
        run_probe(args.base, args.state_file, args.phase, args.private_service_token_file)
    except (SmokeError, ValueError, KeyError, TypeError, OSError) as error:
        # Server bodies and credential values are deliberately excluded.
        message = str(error) if isinstance(error, SmokeError) else "Invalid private journal merge smoke state"
        parser.exit(1, f"[FAIL] {message}\n")
    print(f"Journal merge {args.phase} proof passed")


if __name__ == "__main__":
    main()
