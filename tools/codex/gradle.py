#!/usr/bin/env python3
"""Run pinned Gradle with the Cloud proxy and system CA trust, without changing sources."""
import os
import json
from pathlib import Path
import subprocess
import sys
from urllib.parse import urlparse

root = Path(__file__).resolve().parents[2]
base = Path(os.environ.get("HALAQATI_TOOLS", Path.home() / ".local/share/halaqati"))
project = subprocess.check_output([sys.executable, str(root / "tools/codex/native-source.py"), "extract"], text=True).strip()
env = {**os.environ, "JAVA_HOME": str(base / "jdk-17"), "ANDROID_HOME": str(base / "android-sdk"), "ANDROID_SDK_ROOT": str(base / "android-sdk")}
options = []
for scheme in ["http", "https"]:
    proxy = os.environ.get(f"{scheme.upper()}_PROXY") or os.environ.get(f"{scheme}_proxy")
    if proxy:
        parsed = urlparse(proxy)
        if parsed.username or parsed.password:
            raise SystemExit("Authenticated proxy needs a private Gradle credential configuration; refusing to print credentials")
        options.extend([f"-D{scheme}.proxyHost={parsed.hostname}", f"-D{scheme}.proxyPort={parsed.port or 80}"])
if Path("/etc/ssl/certs/java/cacerts").exists():
    options.append("-Djavax.net.ssl.trustStore=/etc/ssl/certs/java/cacerts")
exceptions = ["localhost", "127.*", "[::1]"]
for host in (os.environ.get("NO_PROXY") or os.environ.get("no_proxy") or "").split(","):
    host = host.strip()
    if host and "/" not in host:
        exceptions.append("*" + host if host.startswith(".") else host)
options.append("-Dhttp.nonProxyHosts=" + "|".join(exceptions))
command = [str(base / "gradle-8.13/bin/gradle"), "--no-daemon", "--console=plain", "--max-workers=2", "-Dorg.gradle.jvmargs=-Xmx3g -XX:MaxMetaspaceSize=768m -Dfile.encoding=UTF-8", *options, *sys.argv[1:]]
firebase = Path(project) / "app/google-services.json"
created = False
offline = os.environ.get("HALAQATI_OFFLINE_ANDROID_TEST") == "1"
if offline:
    allowed = {":app:testDebugUnitTest", ":app:lintDebug", ":app:assembleDebug"}
    tasks = [arg for arg in sys.argv[1:] if not arg.startswith("-")]
    if not tasks or any(task not in allowed for task in tasks):
        raise SystemExit("Offline Firebase fixture is restricted to debug compilation, lint and local unit tests")
if not firebase.exists():
    raw = os.environ.get("HALAQATI_FIREBASE_GOOGLE_SERVICES_JSON")
    encoded = os.environ.get("HALAQATI_FIREBASE_GOOGLE_SERVICES_JSON_BASE64")
    if encoded:
        import base64
        raw = base64.b64decode(encoded).decode()
    if raw:
        config = json.loads(raw)
        packages = [client.get("client_info", {}).get("android_client_info", {}).get("package_name") for client in config.get("client", [])]
        if "com.mohammedsamir.halaqati" not in packages:
            raise SystemExit("Firebase configuration package does not match this application")
    elif offline:
        print("OFFLINE TEST MODE: synthetic Firebase configuration; no Firebase runtime verification", flush=True)
        raw = (root / "tools/codex/firebase-offline-test.json").read_text()
    if raw:
        firebase.write_text(raw)
        firebase.chmod(0o600)
        created = True
try:
    result = subprocess.call(command, cwd=project, env=env)
finally:
    if created:
        firebase.unlink(missing_ok=True)
raise SystemExit(result)
