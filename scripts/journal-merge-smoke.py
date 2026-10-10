#!/usr/bin/env python3
"""Verify journal merges using the deployment's disposable passkey account."""

import argparse
import json
import os
import re
import stat
import tempfile
import time
import uuid
from pathlib import Path
from urllib.error import HTTPError, URLError
from urllib.parse import urlsplit
from urllib.request import HTTPRedirectHandler, Request, build_opener


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


class NoRedirect(HTTPRedirectHandler):
    def redirect_request(self, request, response, code, message, headers, new_url):
        raise SmokeError("Smoke endpoint attempted a redirect")


def http_request(method, url, headers, body):
    encoded = None if body is None else json.dumps(body).encode()
    request = Request(url, data=encoded, headers=headers, method=method)
    try:
        response = build_opener(NoRedirect()).open(request, timeout=30)
    except HTTPError as error:
        response = error
    except (URLError, TimeoutError, OSError):
        raise SmokeError("Journal merge smoke network request failed") from None
    with response:
        raw = response.read(2 * 1024 * 1024 + 1)
        if len(raw) > 2 * 1024 * 1024:
            raise SmokeError("Journal merge smoke response exceeded its size limit")
        try:
            payload = json.loads(raw) if raw else {}
        except (ValueError, UnicodeError):
            raise SmokeError("Journal merge smoke returned invalid JSON") from None
        return response.code, payload


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
    if credential.get("origin") != state["canonicalOrigin"]:
        raise SmokeError("Passkey origin does not match deployment state")
    return state


class Probe:
    def __init__(self, base, state, transport, private_token_file):
        self.base = base
        self.state = state
        self.transport = transport
        token = state["accessToken"]
        if not re.fullmatch(r"[A-Za-z0-9._~-]+", token):
            raise SmokeError("Malformed smoke access token")
        self.headers = {"Authorization": f"Bearer {token}", "Content-Type": "application/json"}
        if private_token_file:
            token = private_text(private_token_file).strip()
            if not re.fullmatch(r"[A-Za-z0-9._~-]+", token):
                raise SmokeError("Malformed private candidate token")
            self.headers["X-Serverless-Authorization"] = f"Bearer {token}"

    def request(self, method, path, body=None, statuses=(200,)):
        status, payload = self.transport(method, self.base + "/api/v1" + path, self.headers, body)
        if status not in statuses:
            raise SmokeError(f"Journal merge smoke {method} {path.split('?')[0]} returned HTTP {status}")
        return payload

    def verify_identity(self):
        descriptor = self.request("GET", "/server/info").get("data", {})
        if "journalMergeV1" not in descriptor.get("protocolFeatures", []):
            raise SmokeError("Deployed server does not advertise journalMergeV1")
        if descriptor.get("serverOrigin") != self.state["canonicalOrigin"]:
            raise SmokeError("Server descriptor origin does not match deployment state")
        account = self.request("GET", "/auth/me").get("data", {}).get("account", {})
        if account.get("id") != self.state["accountId"] or account.get("username") != self.state["username"]:
            raise SmokeError("Authenticated disposable account identity does not match state")

    def prepare(self, fixture):
        for journal in (fixture["source"], fixture["destination"]):
            self.request("PUT", "/journals/" + journal["id"], journal, (200, 201))
        self.request("POST", "/associations", {"associations": [{
            "journalId": fixture["source"]["id"], "contentId": fixture["remoteId"],
            "createdAt": fixture["source"]["createdAt"], "deviceId": "deployment-smoke",
        }]})
        self.merge(fixture)
        self.merge(fixture)

    def merge(self, fixture):
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


def new_fixture():
    timestamp = int(time.time() * 1000)
    def journal(title):
        return {"id": str(uuid.uuid4()), "title": title, "description": "Deployment merge fixture",
                "createdAt": timestamp, "lastUpdated": timestamp, "deviceId": "deployment-smoke"}
    return {"source": journal("Disposable merge source"), "destination": journal("Destination metadata stays"),
            "remoteId": str(uuid.uuid4()), "submittedId": str(uuid.uuid4()), "operationId": str(uuid.uuid4())}


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
        fixture = new_fixture()
        state["journalMergeSmoke"] = fixture
        write_state(state_file, state)
        probe.prepare(fixture)
    else:
        if fixture is None:
            raise SmokeError("Merge fixture was not prepared on the candidate")
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
