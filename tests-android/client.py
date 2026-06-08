# SPDX-License-Identifier: GPL-3.0-or-later
"""Thin AnkiConnect HTTP client used by the test suite.

Talks to a running AnkiconnectAndroid server (typically on a device via
Termux). The base URL is taken from the ANKI_CONNECT_URL environment
variable and defaults to http://localhost:8765.
"""

import os

import requests

DEFAULT_URL = "http://localhost:8765"


class AnkiConnectError(Exception):
    """Raised when the server returns a non-null ``error`` field."""


def base_url() -> str:
    return os.environ.get("ANKI_CONNECT_URL", DEFAULT_URL)


def invoke(action: str, *, url: str = None, key: str = None, timeout: float = 10.0, **params):
    """POST an AnkiConnect request and return its ``result``.

    Sends ``{"action": action, "version": 6, "params": params}`` (plus an
    optional ``key``) as JSON, parses the ``{"result", "error"}`` envelope and
    raises :class:`AnkiConnectError` if ``error`` is non-null.
    """
    payload = {"action": action, "version": 6, "params": params}
    if key is not None:
        payload["key"] = key

    response = requests.post(url or base_url(), json=payload, timeout=timeout)
    response.raise_for_status()
    data = response.json()

    if data.get("error") is not None:
        raise AnkiConnectError(data["error"])

    return data.get("result")
