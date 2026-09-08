#!/usr/bin/env python3
"""Obtain a personal Tesla Fleet API token without deploying a backend.

The script runs only on the user's computer. Register its redirect URL in the
Tesla developer console, provide credentials through environment variables,
and import the resulting short-lived access token into the Android app.
"""

from __future__ import annotations

import argparse
import base64
import hashlib
import http.server
import json
import os
import secrets
import socketserver
import stat
import sys
import urllib.error
import urllib.parse
import urllib.request
import webbrowser
from pathlib import Path

DEFAULT_AUTH_BASE = "https://fleet-auth.prd.vn.cloud.tesla.com/oauth2/v3"
DEFAULT_SCOPES = "openid offline_access vehicle_device_data vehicle_location"


def base64url(value: bytes) -> str:
    return base64.urlsafe_b64encode(value).rstrip(b"=").decode("ascii")


def create_pkce() -> tuple[str, str]:
    verifier = base64url(secrets.token_bytes(64))
    challenge = base64url(hashlib.sha256(verifier.encode("ascii")).digest())
    return verifier, challenge


def receive_code(redirect_uri: str, expected_state: str, timeout: int) -> str:
    parsed = urllib.parse.urlparse(redirect_uri)
    if parsed.scheme != "http" or parsed.hostname not in {"127.0.0.1", "localhost"}:
        raise ValueError("TESLA_REDIRECT_URI must be an http://localhost URL")

    result: dict[str, str] = {}

    class CallbackHandler(http.server.BaseHTTPRequestHandler):
        def do_GET(self) -> None:  # noqa: N802 - stdlib callback name
            query = urllib.parse.parse_qs(urllib.parse.urlparse(self.path).query)
            if query.get("state", [""])[0] != expected_state:
                result["error"] = "OAuth state mismatch"
                status = 400
            elif query.get("error"):
                result["error"] = query["error"][0]
                status = 400
            elif query.get("code"):
                result["code"] = query["code"][0]
                status = 200
            else:
                result["error"] = "Authorization response contained no code"
                status = 400
            body = b"Authorization received. You can close this window."
            self.send_response(status)
            self.send_header("Content-Type", "text/plain; charset=utf-8")
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            self.wfile.write(body)

        def log_message(self, format: str, *args: object) -> None:
            return

    with socketserver.TCPServer((parsed.hostname, parsed.port or 80), CallbackHandler) as server:
        server.timeout = timeout
        server.handle_request()
    if "code" not in result:
        raise RuntimeError(result.get("error", "Timed out waiting for Tesla authorization"))
    return result["code"]


def parse_redirect_url(redirected_url: str, expected_state: str) -> str:
    query = urllib.parse.parse_qs(urllib.parse.urlparse(redirected_url).query)
    if query.get("state", [""])[0] != expected_state:
        raise ValueError("OAuth state mismatch")
    if query.get("error"):
        raise RuntimeError(f"Tesla authorization failed: {query['error'][0]}")
    code = query.get("code", [""])[0]
    if not code:
        raise ValueError("Redirect URL contains no authorization code")
    return code


def exchange_code(
    token_url: str,
    client_id: str,
    client_secret: str,
    redirect_uri: str,
    code: str,
    verifier: str,
) -> dict[str, object]:
    form = urllib.parse.urlencode(
        {
            "grant_type": "authorization_code",
            "client_id": client_id,
            "client_secret": client_secret,
            "code": code,
            "redirect_uri": redirect_uri,
            "code_verifier": verifier,
        }
    ).encode("ascii")
    request = urllib.request.Request(
        token_url,
        data=form,
        headers={"Content-Type": "application/x-www-form-urlencoded"},
        method="POST",
    )
    try:
        with urllib.request.urlopen(request, timeout=30) as response:
            return json.load(response)
    except urllib.error.HTTPError as error:
        detail = error.read().decode("utf-8", errors="replace")
        raise RuntimeError(f"Tesla token exchange failed ({error.code}): {detail}") from error


def write_private_json(path: Path, payload: dict[str, object]) -> None:
    flags = os.O_WRONLY | os.O_CREAT | os.O_TRUNC
    descriptor = os.open(path, flags, stat.S_IRUSR | stat.S_IWUSR)
    with os.fdopen(descriptor, "w", encoding="utf-8") as output:
        json.dump(payload, output, indent=2)
        output.write("\n")
    os.chmod(path, stat.S_IRUSR | stat.S_IWUSR)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, default=Path("tesla-token.json"))
    parser.add_argument("--timeout", type=int, default=180)
    parser.add_argument(
        "--manual",
        action="store_true",
        help="paste the final redirect URL instead of starting a localhost listener",
    )
    args = parser.parse_args()

    client_id = os.environ.get("TESLA_CLIENT_ID", "")
    client_secret = os.environ.get("TESLA_CLIENT_SECRET", "")
    redirect_uri = os.environ.get("TESLA_REDIRECT_URI", "http://127.0.0.1:8765/callback")
    auth_base = os.environ.get("TESLA_AUTH_BASE_URL", DEFAULT_AUTH_BASE).rstrip("/")
    if not client_id or not client_secret:
        parser.error("TESLA_CLIENT_ID and TESLA_CLIENT_SECRET are required")

    verifier, challenge = create_pkce()
    state = base64url(secrets.token_bytes(32))
    authorize_url = auth_base + "/authorize?" + urllib.parse.urlencode(
        {
            "client_id": client_id,
            "redirect_uri": redirect_uri,
            "response_type": "code",
            "scope": os.environ.get("TESLA_SCOPES", DEFAULT_SCOPES),
            "state": state,
            "code_challenge": challenge,
            "code_challenge_method": "S256",
        }
    )
    print("Opening Tesla authorization page...", file=sys.stderr)
    if not webbrowser.open(authorize_url):
        print(authorize_url, file=sys.stderr)
    if args.manual:
        redirected_url = input("Paste the complete final redirect URL: ").strip()
        code = parse_redirect_url(redirected_url, state)
    else:
        code = receive_code(redirect_uri, state, args.timeout)
    token = exchange_code(auth_base + "/token", client_id, client_secret, redirect_uri, code, verifier)
    write_private_json(args.output, token)
    print(f"Token response saved with mode 0600: {args.output}")
    print("Import the access_token value into Summon Pro, then delete the file when finished.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
