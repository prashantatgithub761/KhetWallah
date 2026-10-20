#!/usr/bin/env python3
"""A local-only, in-memory inbox for the Spring email-test profile.

Run from the project root:
    python3 docs/test-email-server.py
Then start the application with SPRING_PROFILES_ACTIVE=email-test and open:
    Application: http://localhost:8080
    Test inbox:  http://127.0.0.1:8025

No mail leaves this machine. The most recent 100 messages and 1,000
idempotency keys are kept in memory only. Restarting clears both.
OTP codes are visible in the inbox, never printed or written to disk.
Do not expose this development server through a public tunnel or proxy.
"""

from collections import OrderedDict, deque
from datetime import datetime, timezone
from hashlib import sha256
from html import escape
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import json
import re
import threading
import uuid


ADDRESS = ("127.0.0.1", 8025)
ALLOWED_HOSTS = frozenset({"127.0.0.1:8025", "localhost:8025"})
MAX_BODY_BYTES = 20 * 1024
MAX_MESSAGES = 100
MAX_IDEMPOTENCY_KEYS = 1_000
MESSAGES = deque(maxlen=MAX_MESSAGES)
IDEMPOTENCY_KEYS = OrderedDict()
STORE_LOCK = threading.Lock()


class InvalidRequest(Exception):
    def __init__(self, status, message):
        self.status = status
        self.message = message


def valid_message(payload):
    """Accept only the plain-text envelope used by the Java test sender."""
    if not isinstance(payload, dict):
        return False
    if set(payload) != {"from", "to", "subject", "text"}:
        return False
    if not all(isinstance(payload.get(field), str) for field in ("from", "subject", "text")):
        return False
    if not payload["from"].strip() or len(payload["from"]) > 320:
        return False
    if not payload["subject"].strip() or len(payload["subject"]) > 200:
        return False
    if any(char in payload[field] for field in ("from", "subject") for char in "\r\n"):
        return False
    recipients = payload["to"]
    return (
        isinstance(recipients, list)
        and 1 <= len(recipients) <= 10
        and all(
            isinstance(recipient, str)
            and 1 <= len(recipient) <= 254
            and "@" in recipient
            and "\r" not in recipient
            and "\n" not in recipient
            for recipient in recipients
        )
        and bool(payload["text"].strip())
    )


class InboxHandler(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.0"
    server_version = "KhetWallahLocalInbox"
    sys_version = ""

    def log_message(self, format, *args):
        # Message content, OTPs and request details must never enter server logs.
        pass

    def setup(self):
        super().setup()
        self.connection.settimeout(10)

    def allowed_request(self):
        hosts = self.headers.get_all("Host", [])
        if len(hosts) != 1 or hosts[0] not in ALLOWED_HOSTS:
            return False
        origins = self.headers.get_all("Origin", [])
        # Browser requests must originate from this exact local inbox origin.
        # The Java application sends no Origin header. No CORS is enabled.
        return not origins or origins == ["http://" + hosts[0]]

    def respond(self, status, content, content_type="application/json; charset=utf-8"):
        raw = content.encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", content_type)
        self.send_header("Content-Length", str(len(raw)))
        self.send_header("Cache-Control", "no-store")
        self.send_header("Pragma", "no-cache")
        self.send_header("X-Content-Type-Options", "nosniff")
        self.send_header("Referrer-Policy", "no-referrer")
        self.send_header("Cross-Origin-Resource-Policy", "same-origin")
        self.send_header(
            "Content-Security-Policy",
            "default-src 'none'; style-src 'unsafe-inline'; frame-ancestors 'none'; "
            "base-uri 'none'; form-action 'none'",
        )
        self.send_header("Connection", "close")
        self.end_headers()
        self.close_connection = True
        self.wfile.write(raw)

    def error(self, status, message):
        self.respond(status, json.dumps({"message": message}))

    def do_OPTIONS(self):
        # Disallow browser cross-origin preflights; never return CORS headers.
        self.error(403, "Cross-origin access is not allowed")

    def read_message_body(self):
        lengths = self.headers.get_all("Content-Length", [])
        encodings = self.headers.get_all("Transfer-Encoding", [])
        if encodings:
            # Spring's JDK HTTP client can stream JSON using chunked framing.
            if lengths or len(encodings) != 1 or encodings[0].lower() != "chunked":
                raise InvalidRequest(400, "Invalid message framing")
            body = bytearray()
            while True:
                line = self.rfile.readline(128)
                if not re.fullmatch(rb"[0-9a-fA-F]{1,8}\r\n", line):
                    raise InvalidRequest(400, "Invalid chunk framing")
                size = int(line[:-2], 16)
                if size == 0:
                    if self.rfile.read(2) != b"\r\n":
                        raise InvalidRequest(400, "Message trailers are not supported")
                    if not body:
                        raise InvalidRequest(400, "A message is required")
                    return bytes(body)
                if len(body) + size > MAX_BODY_BYTES:
                    raise InvalidRequest(413, "Message exceeds the 20 KB local test limit")
                chunk = self.rfile.read(size)
                if len(chunk) != size or self.rfile.read(2) != b"\r\n":
                    raise InvalidRequest(400, "Incomplete message")
                body.extend(chunk)
        if len(lengths) != 1 or not re.fullmatch(r"[0-9]{1,10}", lengths[0]):
            raise InvalidRequest(400, "A single valid content length is required")
        length = int(lengths[0])
        if length <= 0:
            raise InvalidRequest(400, "A message is required")
        if length > MAX_BODY_BYTES:
            raise InvalidRequest(413, "Message exceeds the 20 KB local test limit")
        body = self.rfile.read(length)
        if len(body) != length:
            raise InvalidRequest(400, "Incomplete message")
        return body

    def do_POST(self):
        if not self.allowed_request():
            self.error(403, "Only the local inbox origin is allowed")
            return
        if self.path != "/emails":
            self.error(404, "Not found")
            return
        if self.headers.get_all("Authorization", []) != ["Bearer local-test"]:
            self.error(401, "Local test authorization required")
            return
        if self.headers.get_content_type() != "application/json":
            self.error(415, "JSON content required")
            return
        keys = self.headers.get_all("Idempotency-Key", [])
        if len(keys) > 1 or (keys and (
            not keys[0].strip() or len(keys[0]) > 256
            or any(ord(char) < 33 or ord(char) > 126 for char in keys[0])
        )):
            self.error(400, "Invalid idempotency key")
            return
        try:
            raw = self.read_message_body()
            payload = json.loads(raw.decode("utf-8"))
        except InvalidRequest as error:
            self.error(error.status, error.message)
            return
        except (TimeoutError, OSError):
            self.error(400, "Incomplete message")
            return
        except (UnicodeDecodeError, json.JSONDecodeError, RecursionError):
            self.error(400, "Invalid JSON message")
            return
        if not valid_message(payload):
            self.error(400, "Invalid plain-text message envelope")
            return

        try:
            fingerprint = sha256(json.dumps(
                payload, sort_keys=True, separators=(",", ":"), ensure_ascii=False
            ).encode("utf-8")).hexdigest()
        except UnicodeEncodeError:
            self.error(400, "Invalid Unicode message")
            return
        key = keys[0] if keys else None
        with STORE_LOCK:
            previous = IDEMPOTENCY_KEYS.get(key) if key else None
            if previous:
                previous_fingerprint, message_id = previous
                if fingerprint != previous_fingerprint:
                    conflict = True
                else:
                    conflict = False
            else:
                conflict = False
                message_id = str(uuid.uuid4())
                MESSAGES.append({
                    "id": message_id,
                    "created": datetime.now(timezone.utc).isoformat(timespec="seconds"),
                    **payload,
                })
                if key:
                    IDEMPOTENCY_KEYS[key] = (fingerprint, message_id)
                    while len(IDEMPOTENCY_KEYS) > MAX_IDEMPOTENCY_KEYS:
                        IDEMPOTENCY_KEYS.popitem(last=False)
        if conflict:
            self.error(409, "Idempotency key already used for a different message")
            return
        self.respond(200, json.dumps({"id": message_id}))

    def do_GET(self):
        if not self.allowed_request():
            self.error(403, "Only the local inbox origin is allowed")
            return
        if self.path != "/":
            self.error(404, "Not found")
            return
        with STORE_LOCK:
            messages = list(reversed(MESSAGES))
        cards = []
        for message in messages:
            cards.append(
                '<article><h2>' + escape(message["subject"]) + '</h2>'
                '<p><strong>To:</strong> ' + escape(", ".join(message["to"])) + '</p>'
                '<p><strong>From:</strong> ' + escape(message["from"]) + '</p>'
                '<p class="timestamp">' + escape(message["created"]) + '</p>'
                '<pre>' + escape(message["text"]) + '</pre></article>'
            )
        content = """<!doctype html>
<html lang="en"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>KhetWallah local test inbox</title>
<style>
body{max-width:880px;margin:40px auto;padding:0 20px;background:#f4f7ef;
color:#203326;font:16px/1.5 system-ui,sans-serif}
article{background:white;border:1px solid #d4dfce;border-radius:12px;
padding:22px;margin:22px 0;overflow-wrap:anywhere}
h1{font-size:28px}h2{font-size:21px;margin-top:0}a{color:#165336}
pre{white-space:pre-wrap;overflow-wrap:anywhere;font:inherit;
background:#f4f7ef;border-radius:8px;padding:16px}.timestamp{color:#536452}
</style></head><body><h1>📬 KhetWallah local test inbox</h1>
<p>Messages stay on this machine. Nothing is sent to a real mailbox.
The newest 100 messages are kept in memory; restarting this inbox clears them.</p>
<p><a href="/">Refresh inbox</a></p>"""
        content += "".join(cards) if cards else "<p>No test emails yet. Request a signup code in the app.</p>"
        content += "</body></html>"
        self.respond(200, content, "text/html; charset=utf-8")


def main():
    try:
        server = ThreadingHTTPServer(ADDRESS, InboxHandler)
    except OSError:
        raise SystemExit("Cannot start the local inbox. Check whether port 8025 is already in use.") from None
    print("Local test inbox: http://127.0.0.1:8025", flush=True)
    print("Messages remain in memory. Press Ctrl+C to stop and clear the inbox.", flush=True)
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass
    finally:
        server.server_close()
        with STORE_LOCK:
            MESSAGES.clear()
            IDEMPOTENCY_KEYS.clear()


if __name__ == "__main__":
    main()
