"""Install the dedicated CI debug signer only after verifying its public identity."""

import base64
import binascii
import os
from pathlib import Path
import re
import subprocess
import sys
import tempfile


def materialize(destination: Path) -> None:
    if os.environ.get("GITHUB_ACTIONS") != "true":
        raise ValueError("The CI debug signer may only be installed on GitHub Actions")
    encoded = os.environ.get("LOGDATE_CI_DEBUG_KEYSTORE_BASE64", "")
    expected = os.environ.get("LOGDATE_CI_DEBUG_CERT_SHA1", "").replace(":", "").upper()
    if not encoded or not re.fullmatch(r"[0-9A-F]{40}", expected):
        raise ValueError("CI debug keystore and expected certificate SHA-1 are required")
    try:
        keystore = base64.b64decode(encoded, validate=True)
    except (ValueError, binascii.Error) as error:
        raise ValueError("CI debug keystore must be valid base64") from error
    destination.parent.mkdir(parents=True, exist_ok=True)
    descriptor, temporary = tempfile.mkstemp(prefix=".ci-debug-", dir=destination.parent)
    try:
        with os.fdopen(descriptor, "wb") as output:
            output.write(keystore)
        result = subprocess.run([
            "keytool", "-J-Duser.language=en", "-J-Duser.country=US", "-list", "-v",
            "-keystore", temporary, "-storepass", "android", "-alias", "androiddebugkey",
        ], capture_output=True, text=True)
        actual = re.search(r"SHA1:\s*([0-9A-F:]+)", result.stdout)
        if (result.returncode or "PrivateKeyEntry" not in result.stdout or not actual
                or actual[1].replace(":", "") != expected):
            raise ValueError("CI debug keystore does not match the authorized signing identity")
        os.replace(temporary, destination)
        print("Installed verified CI debug signing identity")
    finally:
        Path(temporary).unlink(missing_ok=True)


if __name__ == "__main__":
    try:
        if len(sys.argv) != 2:
            raise ValueError("Usage: materialize-ci-debug-keystore.py OUTPUT_PATH")
        materialize(Path(sys.argv[1]))
    except (ValueError, OSError) as error:
        sys.exit(str(error))
