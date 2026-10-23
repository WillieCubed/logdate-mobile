"""Host custody for the original single-device TalkBack harness."""

from pathlib import Path


def check(condition, message):
    if not condition:
        raise RuntimeError(message)


def bounded_evidence(value, limit):
    if len(value) <= limit:
        return repr(value)
    return repr(value[:limit]) + "...<truncated>"


class HostCustody:
    def __init__(self, nonce, avd, launches, adb, command, target, emulator):
        self.nonce, self.avd, self.launches, self.adb = nonce, avd, launches, adb
        self.original, self.target, self.emulator = command, target, emulator
        self.child = None
        self.gradle_identity = None
        self.bindings = {}
        self.ready_serial = None

    def remember(self, child):
        check(self.child is None, "Only one Gradle producer")
        self.child = child

    def require(self, serial):
        check(
            self.child is not None and self.child.poll() is None,
            "Owned Gradle child is not live",
        )
        identity = self.target["process_identity"](self.child.pid)
        check(self.nonce in identity["command"], "Gradle generation nonce missing")
        if self.gradle_identity is None:
            self.gradle_identity = identity
        check(identity == self.gradle_identity, "Gradle birth or command changed")
        lines = self.original("ps", "-axo", "pid,args").stdout.decode().splitlines()
        candidates = self.target["managed_candidates"](lines, set(), self.avd)
        owned = self.emulator["owned_managed_candidates"](
            self.launches, candidates, self.nonce
        )
        matched = []
        for pid, candidate in owned.items():
            current = self.target["process_identity"](pid)
            candidate_check = self.target["managed_candidates"](
                [str(pid) + " " + current["command"]], set(), self.avd
            )
            if candidate_check.get(pid) != candidate:
                original_row = "<missing>"
                for line in lines:
                    fields = line.strip().split(None, 1)
                    if fields and fields[0] == str(pid):
                        original_row = line
                        break
                raise RuntimeError(
                    f"Managed QEMU candidate mismatch for PID {pid} "
                    f"(birth={current['birth']!r}): expected candidate={candidate!r}, "
                    f"observed candidate={candidate_check.get(pid)!r}; "
                    f"current command={bounded_evidence(current['command'], 220)}; "
                    f"original ps row={bounded_evidence(original_row, 260)}"
                )
            listeners = self.original(
                "lsof",
                "-nP",
                "-a",
                "-p",
                str(pid),
                "-iTCP",
                "-sTCP:LISTEN",
                "-Fn",
                check=False,
            ).stdout.decode()
            ports = self.target_ports(listeners)
            if ports and serial == "emulator-" + str(next(iter(ports))):
                matched.append((pid, candidate, current))
        check(
            len(matched) == 1,
            "Serial does not identify one current owned managed generation",
        )
        pid, candidate, current = matched[0]
        binding = {"pid": pid, "candidate": candidate, "identity": current}
        if self.ready_serial is not None:
            check(
                serial == self.ready_serial and self.bindings.get(serial) == binding,
                "Managed QEMU birth or command changed",
            )
        check(
            self.child.poll() is None
            and self.target["process_identity"](self.child.pid) == self.gradle_identity,
            "Gradle changed before device use",
        )
        check(
            self.target["process_identity"](pid) == current,
            "QEMU changed before device use",
        )
        return binding

    def bind_ready(self, serial):
        check(self.ready_serial is None, "TalkBack target was already selected")
        binding = self.require(serial)
        self.ready_serial = serial
        self.bindings[serial] = binding
        self.require(serial)

    def candidates(self, lines, existing, original):
        possible = self.target["managed_candidates"](lines, existing, self.avd)
        owned = self.emulator["owned_managed_candidates"](
            self.launches, possible, self.nonce
        )
        selected = []
        for line in lines:
            fields = line.strip().split(None, 1)
            if fields and fields[0].isdigit() and int(fields[0]) in owned:
                selected.append(line)
        return original(selected, existing)

    @staticmethod
    def target_ports(listeners):
        import re

        ports = {
            int(p)
            for p in re.findall(r"^n(?:127\.0\.0\.1|\[::1\]):(\d+)$", listeners, re.M)
            if 5554 <= int(p) <= 5682 and int(p) % 2 == 0
        }
        check(len(ports) <= 1, "Console port is ambiguous")
        return ports

    def command(self, *args, **kwargs):
        if args and Path(str(args[0])).name == "adb":
            check(
                str(args[0]) == self.adb and len(args) >= 4 and args[1] == "-s",
                "Only explicitly selected managed adb is eligible",
            )
            self.require(str(args[2]))
        return self.original(*args, **kwargs)
