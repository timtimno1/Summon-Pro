import base64
import os
import stat
import tempfile
import unittest
import urllib.parse
from pathlib import Path

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


if __name__ == "__main__":
    unittest.main()
