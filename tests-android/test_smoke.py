# SPDX-License-Identifier: GPL-3.0-or-later
# /// script
# requires-python = ">=3.9"
# dependencies = ["pytest>=8", "requests>=2.31"]
# ///
"""Smoke tests against a running AnkiconnectAndroid server.

These exercise read-only actions that should always be available once the
AnkiDroid API permission has been granted.

Run directly with uv (no pip needed): ``uv run test_smoke.py``.
"""


def test_version(anki):
    result = anki("version")
    # AnkiConnect reports protocol version 6, possibly as a string or int.
    assert str(result) == "6"


def test_deck_names_is_list(anki):
    result = anki("deckNames")
    assert isinstance(result, list)


def test_model_names_is_list(anki):
    result = anki("modelNames")
    assert isinstance(result, list)


if __name__ == "__main__":
    import sys

    import pytest

    sys.exit(pytest.main([__file__, "-v"]))
