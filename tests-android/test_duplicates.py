# SPDX-License-Identifier: GPL-3.0-or-later
# /// script
# requires-python = ">=3.9"
# dependencies = ["pytest>=8", "requests>=2.31"]
# ///
"""addNote duplicate handling, matching AnkiConnect.

A note whose first field duplicates an existing note (same model) must be
rejected unless options.allowDuplicate is true. This guards a bug where
AnkiconnectAndroid added duplicates unconditionally.

Run directly with uv (no pip needed): ``uv run test_duplicates.py``.
"""

import pytest

import client
from conftest import TEST_DECK, TEST_TAG


def _note(front, back, *, allow_duplicate=None, duplicate_scope=None):
    note = {
        "deckName": TEST_DECK,
        "modelName": "Basic",
        "fields": {"Front": front, "Back": back},
        "tags": [TEST_TAG],
    }
    options = {}
    if allow_duplicate is not None:
        options["allowDuplicate"] = allow_duplicate
    if duplicate_scope is not None:
        options["duplicateScope"] = duplicate_scope
    if options:
        note["options"] = options
    return note


def test_addnote_rejects_duplicate_by_default(anki, cleanup_notes):
    front = "acandroid duplicate probe"

    first = anki("addNote", note=_note(front, "back one"))
    assert first is not None

    # Same first field, no allowDuplicate -> AnkiConnect rejects it.
    with pytest.raises(client.AnkiConnectError) as exc_info:
        anki("addNote", note=_note(front, "back two"))
    assert "duplicate" in str(exc_info.value).lower()


def test_addnote_rejects_duplicate_deck_scope(anki, cleanup_notes):
    # Exactly how anki.koplugin adds notes by default: allowDuplicate=false,
    # duplicateScope="deck". This is the scope our earlier tests missed.
    front = "acandroid deck-scope probe"

    first = anki(
        "addNote",
        note=_note(front, "back one", allow_duplicate=False, duplicate_scope="deck"),
    )
    assert first is not None

    with pytest.raises(client.AnkiConnectError) as exc_info:
        anki(
            "addNote",
            note=_note(front, "back two", allow_duplicate=False, duplicate_scope="deck"),
        )
    assert "duplicate" in str(exc_info.value).lower()


def test_addnote_rejects_empty_first_field(anki):
    # An empty first (sort) field is rejected with a distinct "empty" message,
    # even though allowDuplicate would not help here.
    with pytest.raises(client.AnkiConnectError) as exc_info:
        anki("addNote", note=_note("", "back", allow_duplicate=True))
    message = str(exc_info.value).lower()
    assert "empty" in message
    assert "duplicate" not in message


def test_addnote_allows_duplicate_when_opted_in(anki, cleanup_notes):
    front = "acandroid duplicate optin probe"

    first = anki("addNote", note=_note(front, "back one"))
    assert first is not None

    second = anki("addNote", note=_note(front, "back two", allow_duplicate=True))
    assert second is not None
    assert int(second) != int(first)


if __name__ == "__main__":
    import sys

    sys.exit(pytest.main([__file__, "-v"]))
