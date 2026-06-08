# SPDX-License-Identifier: GPL-3.0-or-later
"""Shared pytest fixtures for the AnkiconnectAndroid test suite.

These tests hit a real device running the app. If the server is
unreachable, tests that depend on it are skipped with a clear message
rather than erroring out.
"""

import pytest
import requests

import client

# Notes created by the suite are tagged with this so they can be cleaned up.
TEST_TAG = "acandroid_test"


@pytest.fixture(scope="session")
def base_url() -> str:
    return client.base_url()


@pytest.fixture(scope="session")
def anki(base_url):
    """Return an ``invoke``-like callable bound to the configured base URL.

    Skips the whole test if the server cannot be reached so that running the
    suite without a connected device produces an informative skip instead of
    a connection traceback.
    """
    try:
        client.invoke("version", url=base_url)
    except (requests.ConnectionError, requests.Timeout) as exc:
        pytest.skip(f"AnkiConnect server unreachable at {base_url}: {exc}")

    def _invoke(action, **params):
        return client.invoke(action, url=base_url, **params)

    return _invoke


@pytest.fixture
def cleanup_notes(anki):
    """Delete every note tagged ``acandroid_test`` after the test runs."""
    yield
    try:
        note_ids = anki("findNotes", query=f"tag:{TEST_TAG}")
    except Exception:
        return
    if note_ids:
        anki("deleteNotes", notes=note_ids)
