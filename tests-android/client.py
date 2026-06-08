# SPDX-License-Identifier: GPL-3.0-or-later
"""Thin AnkiConnect HTTP client used by the test suite.

Talks to a running AnkiconnectAndroid server (typically on a device via
Termux). The base URL is taken from the ANKI_CONNECT_URL environment
variable and defaults to http://localhost:8765.

An API key can be supplied via ANKI_CONNECT_KEY. This is mainly useful for
validating the suite against desktop AnkiConnect (the "gold standard"), which
is commonly configured to require a key. AnkiconnectAndroid normally needs no
key, so the variable is left unset there.
"""

import os

import requests

DEFAULT_URL = "http://localhost:8765"

# Sentinel so callers can distinguish "no key argument passed" (fall back to
# the environment) from "explicitly send no key" (key=None).
_USE_ENV_KEY = object()


class AnkiConnectError(Exception):
    """Raised when the server returns a non-null ``error`` field."""


def base_url() -> str:
    return os.environ.get("ANKI_CONNECT_URL", DEFAULT_URL)


def api_key():
    """API key from ANKI_CONNECT_KEY, or None when unset."""
    return os.environ.get("ANKI_CONNECT_KEY")


def invoke(action: str, *, url: str = None, key=_USE_ENV_KEY, timeout: float = 10.0, **params):
    """POST an AnkiConnect request and return its ``result``.

    Sends ``{"action": action, "version": 6, "params": params}`` (plus an
    optional ``key``) as JSON, parses the ``{"result", "error"}`` envelope and
    raises :class:`AnkiConnectError` if ``error`` is non-null.

    By default the key is taken from ANKI_CONNECT_KEY (omitted entirely if that
    is unset). Pass ``key=`` explicitly to override per call.
    """
    if key is _USE_ENV_KEY:
        key = api_key()

    payload = {"action": action, "version": 6, "params": params}
    if key is not None:
        payload["key"] = key

    response = requests.post(url or base_url(), json=payload, timeout=timeout)
    response.raise_for_status()
    data = response.json()

    if data.get("error") is not None:
        raise AnkiConnectError(data["error"])

    return data.get("result")
