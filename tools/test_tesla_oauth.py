import base64
import os
import stat
import tempfile
import unittest
import urllib.parse
from pathlib import Path
from unittest.mock import patch

import tesla_oauth


class TeslaOauthTest(unittest.TestCase):
    def test_pkce_is_rfc7636_compatible(self) -> None:
        verifier, challenge = tesla_oauth.create_pkce()
        self.assertGreaterEqual(len(verifier), 43)
        self.assertNotIn("=", verifier)
        base64.urlsafe_b64decode(challenge + "==")

    def test_token_file_is_private(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "token.json"
            tesla_oauth.write_private_json(path, {"access_token": "secret"})
            self.assertEqual(stat.S_IMODE(os.stat(path).st_mode), 0o600)
            self.assertIn("secret", path.read_text(encoding="utf-8"))

    def test_manual_redirect_validates_state(self) -> None:
        self.assertEqual(
            tesla_oauth.parse_redirect_url("https://example.test/cb?code=abc&state=good", "good"),
            "abc",
        )
        with self.assertRaises(ValueError):
            tesla_oauth.parse_redirect_url("https://example.test/cb?code=abc&state=bad", "good")

    def test_token_form_includes_normalized_fleet_api_audience(self) -> None:
        encoded = tesla_oauth.build_token_form(
            "client", "secret", "http://localhost/cb", "code", "verifier", "https://fleet.example/"
        )
        form = urllib.parse.parse_qs(encoded.decode("ascii"))
        self.assertEqual(form["audience"], ["https://fleet.example"])

    def run_manual_flow(self, environment: dict[str, str]) -> tuple[str, str]:
        state = tesla_oauth.base64url(b"test-state")
        redirect = f"https://example.test/cb?code=auth-code&state={state}"
        with (
            patch.dict(os.environ, {
                "TESLA_CLIENT_ID": "test-client",
                "TESLA_CLIENT_SECRET": "test-secret",
                "TESLA_REDIRECT_URI": "https://example.test/cb",
                **environment,
            }, clear=True),
            patch("sys.argv", ["tesla_oauth.py", "--manual"]),
            patch.object(tesla_oauth, "create_pkce", return_value=("verifier", "challenge")),
            patch.object(tesla_oauth.secrets, "token_bytes", return_value=b"test-state"),
            patch.object(tesla_oauth, "open_authorization") as open_authorization,
            patch("builtins.input", return_value=redirect),
            patch("builtins.print"),
            patch.object(tesla_oauth, "exchange_code", return_value={"access_token": "test-token"}) as exchange,
            patch.object(tesla_oauth, "write_private_json"),
        ):
            self.assertEqual(tesla_oauth.main(), 0)
            self.assertEqual(exchange.call_args.args[4], "auth-code")
            self.assertEqual(exchange.call_args.args[5], "verifier")
            return open_authorization.call_args.args[0], exchange.call_args.args[0]

    def test_browser_authorization_and_token_exchange_use_documented_hosts(self) -> None:
        authorize_url, token_url = self.run_manual_flow({})
        parsed = urllib.parse.urlparse(authorize_url)
        self.assertEqual(f"{parsed.scheme}://{parsed.netloc}{parsed.path}",
                         "https://auth.tesla.com/oauth2/v3/authorize")
        query = urllib.parse.parse_qs(parsed.query)
        self.assertEqual(query["response_type"], ["code"])
        self.assertEqual(query["code_challenge_method"], ["S256"])
        self.assertEqual(token_url, "https://fleet-auth.prd.vn.cloud.tesla.com/oauth2/v3/token")

    def test_authorization_host_override_does_not_change_token_exchange_host(self) -> None:
        authorize_url, token_url = self.run_manual_flow({
            "TESLA_AUTH_BASE_URL": "https://login.example.test/oauth/",
        })
        self.assertTrue(authorize_url.startswith("https://login.example.test/oauth/authorize?"))
        self.assertEqual(token_url, "https://fleet-auth.prd.vn.cloud.tesla.com/oauth2/v3/token")

    def test_token_host_override_is_independent_of_browser_authorization(self) -> None:
        authorize_url, token_url = self.run_manual_flow({
            "TESLA_TOKEN_BASE_URL": "https://tokens.example.test/oauth/",
        })
        self.assertTrue(authorize_url.startswith("https://auth.tesla.com/oauth2/v3/authorize?"))
        self.assertEqual(token_url, "https://tokens.example.test/oauth/token")


if __name__ == "__main__":
    unittest.main()
