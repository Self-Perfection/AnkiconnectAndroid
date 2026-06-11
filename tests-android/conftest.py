# SPDX-License-Identifier: GPL-3.0-or-later
"""Shared pytest fixtures for the AnkiconnectAndroid test suite.

These tests hit a real device running the app. If the server is
unreachable, tests that depend on it are skipped with a clear message
rather than erroring out.
"""

import os

import pytest
import requests

import client

# --- opt-in test categories (verification-mode axis) -----------------------
# Orthogonal to the smoke/core/edge layers. `gui` and `manual` tests are
# DESELECTED by default and only run when explicitly opted into, so a bare
# `uv run pytest` never opens AnkiDroid screens nor needs a human watching.
#
# Done via a collection hook + flags (not addopts `-m "not gui ..."`): a `-m`
# expression on the command line replaces any `-m` from addopts, which would
# silently re-include gui/manual. Flag-based skipping instead composes with
# `-m core/edge` layer selection.
_OPT_IN_MARKERS = {
    "gui": ("--run-gui", "opens an AnkiDroid screen; only the API contract is asserted"),
    "manual": ("--run-manual", "needs a human to confirm the effect"),
}


def pytest_addoption(parser):
    for marker, (flag, help_text) in _OPT_IN_MARKERS.items():
        parser.addoption(flag, action="store_true", default=False,
                         help=f"run tests marked '{marker}' ({help_text})")


def pytest_collection_modifyitems(config, items):
    for marker, (flag, help_text) in _OPT_IN_MARKERS.items():
        if config.getoption(flag):
            continue
        skip = pytest.mark.skip(reason=f"marked '{marker}'; pass {flag} to run ({help_text})")
        for item in items:
            if marker in item.keywords:
                item.add_marker(skip)


# --- build identity ---------------------------------------------------------
# The server reports the git commit it was built from via the non-standard
# `buildInfo` action (see repo CLAUDE.md "Build identity"). We print it in the
# report header so every run records which build it tested, and — if
# ANKI_EXPECT_GIT_SHA is set — abort early when the device runs a different
# commit, so you can't silently test a stale APK.
EXPECT_GIT_SHA = os.environ.get("ANKI_EXPECT_GIT_SHA")


def _fetch_build_info():
    try:
        return client.invoke("buildInfo", url=client.base_url())
    except Exception:
        return None


def pytest_report_header(config):
    info = _fetch_build_info()
    if info is None:
        return "AnkiconnectAndroid build: unknown (server unreachable, or APK too old for buildInfo)"
    return ("AnkiconnectAndroid build: {versionName} "
            "(git {gitSha}, code {versionCode})".format(
                versionName=info.get("versionName"),
                gitSha=info.get("gitSha"),
                versionCode=info.get("versionCode")))


@pytest.fixture(scope="session", autouse=True)
def _verify_build(base_url):
    """Abort the session if the device's build doesn't match ANKI_EXPECT_GIT_SHA."""
    if not EXPECT_GIT_SHA:
        return
    try:
        info = client.invoke("buildInfo", url=base_url)
    except (requests.ConnectionError, requests.Timeout):
        return  # unreachable is handled as a skip by the `anki` fixture
    except Exception:
        pytest.exit(
            f"Build check: device does not support 'buildInfo' but ANKI_EXPECT_GIT_SHA="
            f"{EXPECT_GIT_SHA} was requested. The installed APK predates build-identity; "
            "install the matching build.", returncode=3)
    actual = info.get("gitSha")
    if actual != EXPECT_GIT_SHA:
        pytest.exit(
            f"Build mismatch: device runs git {actual}, expected {EXPECT_GIT_SHA}. "
            "Install the matching APK before running the suite.", returncode=3)


# Notes created by the suite are tagged with this so they can be cleaned up.
TEST_TAG = "acandroid_test"

# Deck the suite adds notes to. Override with ANKI_TEST_DECK; defaults to the
# always-present "Default" deck.
TEST_DECK = os.environ.get("ANKI_TEST_DECK", "Default")


@pytest.fixture(scope="session")
def base_url() -> str:
    return client.base_url()


@pytest.fixture(scope="session")
def deck_name() -> str:
    """Deck the tests add notes to (ANKI_TEST_DECK, default 'Default')."""
    return TEST_DECK


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
