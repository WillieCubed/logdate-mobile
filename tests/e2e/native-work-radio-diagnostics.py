"""Bounded structural evidence; never retain phone dump text or unknown field values."""

import json
import re

MAX_INPUT_BYTES = 1_048_576
MAX_VALUES = 8
MAX_COUNT = 32


def _ids(raw, pattern):
    result = []
    for value in re.findall(pattern, raw, re.MULTILINE):
        if len(value) <= 10 and int(value) < 2**31 - 1:
            result.append(int(value))
            if len(result) == MAX_VALUES:
                break
    return result


def redacted_radio_shape(raw):
    sample = raw[:MAX_INPUT_BYTES].replace(b"\r\n", b"\n")
    patterns = {
        "phoneGlobals": rb"^[ \t]*------- PhoneGlobals -------[ \t]*$",
        "gsmCdmaPhone": rb"^[ \t]*GsmCdmaPhone extends:[ \t]*$",
        "phoneSubId": rb"^[ \t]*Phone: subId=.*$",
        "phoneId": rb"^[ \t]*mPhoneId=.*$",
        "dataSettingsManager": rb"^[ \t]*DataSettingsManager-.*$",
        "userDataEnabled": rb"^[ \t]*isUserDataEnabled=.*$",
        "deviceProvisioned": rb"^[ \t]*device_provisioned=.*$",
        "serviceRecord": rb"^[ \t]*SERVICE .*$",
        "serviceMissing": rb"^[ \t]*No services match:.*$",
        "permissionDenied": rb"^[ \t]*Permission Denial:.*$",
    }
    counts = {
        name: len(re.findall(pattern, sample, re.MULTILINE))
        for name, pattern in patterns.items()
    }
    return {
        "bytes": min(len(raw), MAX_INPUT_BYTES + 1),
        "truncated": len(raw) > MAX_INPUT_BYTES,
        "countsClipped": any(count > MAX_COUNT for count in counts.values()),
        "labels": {name: min(count, MAX_COUNT) for name, count in counts.items()},
        "ids": {
            "phoneSubIds": _ids(sample, rb"^[ \t]*Phone: subId=(0|[1-9][0-9]*)[ \t]*$"),
            "phoneIds": _ids(sample, rb"^[ \t]*mPhoneId=(0|[1-9][0-9]*)[ \t]*$"),
            "dataManagerIds": _ids(
                sample, rb"^[ \t]*DataSettingsManager-(0|[1-9][0-9]*):[ \t]*$"
            ),
            "phoneGlobalsDefaultSubIds": _ids(
                sample, rb"^[ \t]*mDefaultDataSubId=(0|[1-9][0-9]*)[ \t]*$"
            ),
        },
        "booleans": {
            "userDataEnabled": [
                value == b"true"
                for value in re.findall(
                    rb"^[ \t]*isUserDataEnabled=(true|false)[ \t]*$",
                    sample,
                    re.MULTILINE,
                )[:MAX_VALUES]
            ],
            "deviceProvisioned": [
                value == b"1"
                for value in re.findall(
                    rb"^[ \t]*device_provisioned=(0|1)[ \t]*$", sample, re.MULTILINE
                )[:MAX_VALUES]
            ],
        },
    }


def radio_read_refusal(message, raw):
    failure = RuntimeError(message)
    try:
        failure.add_note(
            "Redacted radio shape: "
            + json.dumps(redacted_radio_shape(raw), sort_keys=True)
        )
    except Exception:
        failure.add_note("Redacted radio shape unavailable")
    return failure
