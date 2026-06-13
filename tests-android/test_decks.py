# SPDX-License-Identifier: GPL-3.0-or-later
"""Deck actions ported from anki-connect's tests/test_decks.py.

anki-connect asserts deckNamesAndIds == {"Default": 1} on a fresh profile.
A shared device has more decks, so this only asserts that the always-present
"Default" deck is there and maps to an int id. Also covers createDeck.

Run with uv (no pip needed): ``uv run pytest test_decks.py``.
"""

import pytest

pytestmark = pytest.mark.core


def test_deckNamesAndIds(anki):
    # Source: tests/test_decks.py::test_deckNamesAndIds
    # (there: == {"Default": 1}; relaxed to subset for a non-fresh profile).
    result = anki("deckNamesAndIds")
    assert isinstance(result, dict)
    assert "Default" in result
    assert isinstance(result["Default"], int)


# A fixed name so repeated runs reuse the same deck (get-or-create) instead of
# accumulating; deleteDecks isn't available on AnkiDroid to clean it up.
CREATEDECK_NAME = "acandroid_createdeck_probe"


def test_createDeck_returns_id_and_is_idempotent(anki):
    # Source: tests/test_decks.py::test_createDeck. Desktop returns col.decks.id(deck),
    # which is get-or-create -> a second call yields the SAME id (no error).
    id1 = anki("createDeck", deck=CREATEDECK_NAME)
    assert isinstance(id1, int)

    id2 = anki("createDeck", deck=CREATEDECK_NAME)
    assert int(id2) == int(id1)

    assert CREATEDECK_NAME in anki("deckNamesAndIds")
