#!/usr/bin/env python3
"""Rebuild and run local email testing using the existing private IntelliJ settings.

Run from the KhetWallah folder: python3 docs/run-email-test.py
Use --check to inspect readiness without building or stopping any process.
No database changes are performed and no credential values are printed.
"""
from pathlib import Path
import argparse
import json
import os
import shutil
import signal
import socket
import subprocess
import sys
import time
import urllib.request
import urllib.parse
import xml.etree.ElementTree as ET

PROJECT = Path(__file__).resolve().parent.parent
ALLOWED_ENV = {
    "DB_USERNAME", "DB_PASSWORD", "DATABASE_URL", "ADMIN_EMAIL", "ADMIN_PASSWORD",
    "UPLOAD_DIR", "APP_PUBLIC_URL",
}


def settings():
    result = os.environ.copy()
    workspace = PROJECT / ".idea" / "workspace.xml"
    if workspace.exists():
        for config in ET.parse(workspace).findall(".//configuration"):
            if config.get("name") == "KhetWallahApplication":
                for entry in config.findall(".//env"):
                    if entry.get("name") in ALLOWED_ENV:
                        result[entry.get("name")] = entry.get("value", "")
                break
    if not result.get("DB_USERNAME") or "DB_PASSWORD" not in result:
        raise RuntimeError("Save DB_USERNAME and DB_PASSWORD in the IntelliJ KhetWallahApplication configuration first, or set them in this Terminal.")
    jdbc_url = result.get("DATABASE_URL", "jdbc:mysql://localhost:3306/KhetWallah")
    database_host = urllib.parse.urlsplit(jdbc_url.removeprefix("jdbc:")).hostname
    if database_host not in {"localhost", "127.0.0.1", "::1"}:
        raise RuntimeError("This local runner requires a local MySQL database; it will not start against a remote database.")
    result["SPRING_PROFILES_ACTIVE"] = "email-test"
    result["PORT"] = "8080"
    return result


def listening(port):
    try:
        with socket.create_connection(("127.0.0.1", port), timeout=1):
            return True
    except OSError:
        return False


def existing_khetwallah():
    if not listening(8080):
        return None
    try:
        with urllib.request.urlopen("http://localhost:8080/api/status", timeout=3) as response:
            if json.load(response).get("application") != "KhetWallah":
                raise ValueError("Unexpected application")
    except Exception:
        raise RuntimeError("Port 8080 is occupied by an app I could not identify as KhetWallah. Stop it manually before continuing.") from None
    command = shutil.which("lsof") or "/usr/sbin/lsof"
    result = subprocess.run([command, "-nP", "-iTCP:8080", "-sTCP:LISTEN", "-Fpcu"], capture_output=True, text=True)
    records = []
    for line in result.stdout.splitlines():
        if line.startswith("p"):
            records.append({"pid": int(line[1:])})
        elif records and line.startswith("c"):
            records[-1]["command"] = line[1:]
        elif records and line.startswith("u"):
            records[-1]["uid"] = int(line[1:])
    # Only the identified KhetWallah Java process owned by this user may be stopped.
    if len(records) != 1 or records[0].get("command", "").lower() != "java" or records[0].get("uid") != os.getuid():
        raise RuntimeError("Could not safely identify your KhetWallah Java process. Stop the application in IntelliJ and run this command again.")
    return records[0]["pid"]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()
    environment = settings()
    old_pid = existing_khetwallah()
    print("Existing database settings found; their values will remain private.", flush=True)
    print("Old KhetWallah is running on 8080." if old_pid else "Port 8080 is available.", flush=True)
    print("Test inbox is already running." if listening(8025) else "Test inbox will be started.", flush=True)
    if args.check:
        print("Readiness check complete. No process was changed.")
        return 0

    wrapper = PROJECT / "mvnw"
    print("Building the updated application before stopping the old one…", flush=True)
    subprocess.run([str(wrapper), "-q", "-DskipTests", "package"], cwd=PROJECT, env=environment, check=True)
    namespace = {"m": "http://maven.apache.org/POM/4.0.0"}
    pom = ET.parse(PROJECT / "pom.xml")
    artifact = pom.findtext("m:artifactId", namespaces=namespace)
    version = pom.findtext("m:version", namespaces=namespace)
    jar = PROJECT / "target" / f"{artifact}-{version}.jar"
    if not jar.exists():
        raise RuntimeError("The application JAR was not created. Check the Maven output above.")

    # Check again after the build: a user might have stopped/restarted the app meanwhile.
    old_pid = existing_khetwallah()
    if old_pid:
        print("Stopping the old KhetWallah process gracefully…", flush=True)
        os.kill(old_pid, signal.SIGTERM)
        deadline = time.monotonic() + 20
        while listening(8080) and time.monotonic() < deadline:
            time.sleep(0.25)
        if listening(8080):
            raise RuntimeError("The old app is still stopping. Wait a few seconds, then run this command again.")

    inbox = None
    app = None
    try:
        if not listening(8025):
            inbox = subprocess.Popen([sys.executable, str(PROJECT / "docs" / "test-email-server.py")], cwd=PROJECT)
            deadline = time.monotonic() + 5
            while not listening(8025) and time.monotonic() < deadline:
                if inbox.poll() is not None:
                    raise RuntimeError("The test inbox failed to start.")
                time.sleep(0.1)
            if not listening(8025):
                raise RuntimeError("The test inbox did not become ready.")
        print("Starting email-test mode. Keep this Terminal open; Ctrl+C stops this runner.", flush=True)
        print("App: http://localhost:8080/register.html", flush=True)
        print("Inbox: http://127.0.0.1:8025/", flush=True)
        app = subprocess.Popen(["java", "-jar", str(jar)], cwd=PROJECT, env=environment)
        return app.wait()
    finally:
        for child in [app, inbox]:
            if child is not None and child.poll() is None:
                child.terminate()
                try:
                    child.wait(timeout=15)
                except subprocess.TimeoutExpired:
                    print("A child process is still shutting down; stop it manually if needed.", file=sys.stderr)


if __name__ == "__main__":
    try:
        sys.exit(main())
    except KeyboardInterrupt:
        print("\nLocal test runner stopped.")
        sys.exit(0)
    except subprocess.CalledProcessError:
        print("Build failed. The old running app has been left alone.", file=sys.stderr)
        sys.exit(1)
    except (OSError, RuntimeError, ET.ParseError) as error:
        print(f"Could not start local testing: {error}", file=sys.stderr)
        sys.exit(1)
