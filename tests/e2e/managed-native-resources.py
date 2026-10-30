"""Bounded host metadata observation for managed native acceptance."""

import os
import json
import math
from pathlib import Path
import re
import shutil
import subprocess
import sys
import threading
import time

SAMPLE_INTERVAL_SECONDS = 5
MAX_ROWS = 1200
CLOSE_TIMEOUT_SECONDS = 2
MAX_READ_BYTES = 262144


def _parse_processes(text):
    processes = []
    matched = 0
    for line in text[:MAX_READ_BYTES].splitlines():
        fields = line.split(None, 3)
        if len(fields) != 4 or not all(
            re.fullmatch(r"[0-9]{1,18}", value) for value in fields[:3]
        ):
            continue
        pid, ppid, rss = map(int, fields[:3])
        name = Path(fields[3]).name
        if name in {"java", "javaw"}:
            kind = "java"
        elif re.fullmatch(r"qemu-system-[A-Za-z0-9_-]{1,32}", name):
            kind = "qemu-system"
        else:
            continue
        if pid == 0:
            continue
        matched += 1
        if len(processes) < 128:
            processes.append({"pid": pid, "ppid": ppid, "name": kind, "rssKiB": rss})
    return {
        "processes": processes,
        "matchedProcessCount": matched,
        "processesTruncated": matched > len(processes),
        "processListingComplete": len(text) <= MAX_READ_BYTES,
    }


def _parse_meminfo(text):
    result = {}
    for source, key in (
        ("MemAvailable", "memAvailableBytes"),
        ("SwapFree", "swapFreeBytes"),
    ):
        match = re.search(rf"(?m)^{source}:\s+([0-9]{{1,18}}) kB$", text)
        if match:
            result[key] = int(match[1]) * 1024
    return result


def _parse_pressure(text):
    result = {}
    for line in text.splitlines():
        match = re.fullmatch(
            r"(some|full) avg10=([0-9.]{1,20}) avg60=([0-9.]{1,20}) "
            r"avg300=([0-9.]{1,20}) total=([0-9]{1,18})",
            line,
        )
        if not match:
            continue
        try:
            averages = [float(match[index]) for index in (2, 3, 4)]
        except ValueError:
            continue
        if all(math.isfinite(value) and 0 <= value <= 100 for value in averages):
            result[match[1]] = {
                **dict(zip(("avg10", "avg60", "avg300"), averages)),
                "totalMicroseconds": int(match[5]),
            }
    return result


def _read_text(path):
    with Path(path).open() as source:
        text = source.read(MAX_READ_BYTES + 1)
    if len(text) > MAX_READ_BYTES:
        raise OverflowError("Host metadata exceeds its fixed read limit")
    return text


def _host_sample(output):
    sample = {"cpuCount": os.cpu_count()}
    errors = []

    def capture(label, read):
        try:
            sample.update(read())
        except Exception as failure:
            errors.append({"source": label, "type": type(failure).__name__[:64]})

    capture("load", lambda: {"loadAverage": list(os.getloadavg())})
    capture("disk", lambda: {"diskAvailableBytes": shutil.disk_usage(output).free})
    capture(
        "processes",
        lambda: _parse_processes(
            subprocess.run(
                ["ps", "-axo", "pid=,ppid=,rss=,comm="],
                stdin=subprocess.DEVNULL,
                capture_output=True,
                text=True,
                timeout=1,
                check=True,
            ).stdout
        ),
    )
    if sys.platform == "linux":
        capture("memory", lambda: _parse_meminfo(_read_text("/proc/meminfo")))

        def cpu():
            fields = _read_text("/proc/stat").splitlines()[0].split()
            if fields[0] != "cpu" or not all(value.isdigit() for value in fields[1:11]):
                raise ValueError("CPU counters unavailable")
            counters = list(map(int, fields[1:9]))
            return {"cpuTicks": {"total": sum(counters), "idle": sum(counters[3:5])}}

        capture("cpu", cpu)
        for kind in ("cpu", "memory", "io"):
            file = Path("/proc/pressure") / kind
            if file.exists():
                capture(
                    f"pressure_{kind}",
                    lambda file=file, kind=kind: {
                        f"pressure_{kind}": _parse_pressure(_read_text(file))
                    },
                )
    if errors:
        sample["errors"] = errors
    return sample


class _ResourceObserver:
    def __init__(self, output):
        self._output = Path(output)
        self._lock = threading.Lock()
        self._stop = threading.Event()
        self._phase = "created"
        self._rows = 0
        self._limit = MAX_ROWS
        flags = os.O_WRONLY | os.O_CREAT | os.O_EXCL | getattr(os, "O_NOFOLLOW", 0)
        descriptor = os.open(self._output / "host-resources.jsonl", flags, 0o600)
        self._stream = os.fdopen(descriptor, "w", buffering=1)
        self._thread = threading.Thread(
            target=self._run, name="managed-host-resources", daemon=True
        )
        try:
            self._append(
                {
                    "event": "metadata",
                    "schema": 1,
                    "sampleIntervalSeconds": SAMPLE_INTERVAL_SECONDS,
                    "maxRows": self._limit,
                    "measurementLimit": "RSS is not PSS; configured heap caps are not usage",
                }
            )
            self._thread.start()
        except BaseException:
            self._stream.close()
            raise

    def _append(self, row, terminal=False):
        with self._lock:
            if self._rows >= self._limit - (0 if terminal else 1):
                return
            self._stream.write(
                json.dumps(
                    {
                        "monotonicSeconds": time.monotonic(),
                        "phase": self._phase,
                        **row,
                    },
                    allow_nan=False,
                    separators=(",", ":"),
                )
                + "\n"
            )
            self._rows += 1

    def _run(self):
        reason = "stopped"
        try:
            while not self._stop.is_set():
                try:
                    sample = _host_sample(self._output)
                    self._append({"event": "sample", **sample})
                except Exception as failure:
                    self._append(
                        {"event": "sample_error", "type": type(failure).__name__[:64]}
                    )
                if self._rows >= self._limit - 1:
                    reason = "row_limit"
                    break
                if self._stop.wait(SAMPLE_INTERVAL_SECONDS):
                    break
        except Exception:
            # An unavailable evidence sink cannot replace the acceptance outcome.
            reason = "write_error"
        finally:
            try:
                self._append({"event": "closed", "reason": reason}, terminal=True)
            except Exception:
                pass
            try:
                self._stream.close()
            except Exception:
                pass

    def set_phase(self, phase):
        if (
            not isinstance(phase, str)
            or re.fullmatch(r"[a-z][a-z0-9_-]{0,63}", phase) is None
        ):
            raise ValueError("Observer phase must be a bounded machine label")
        with self._lock:
            self._phase = phase

    def close(self):
        self._stop.set()
        if self._thread is not threading.current_thread():
            self._thread.join(CLOSE_TIMEOUT_SECONDS)
        return not self._thread.is_alive()


def start_resource_observer(output):
    return _ResourceObserver(output)
