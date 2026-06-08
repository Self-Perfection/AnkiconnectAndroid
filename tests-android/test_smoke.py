# SPDX-License-Identifier: GPL-3.0-or-later
"""Smoke tests against a running AnkiconnectAndroid server.

These exercise read-only actions that should always be available once the
AnkiDroid API permission has been granted.
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
