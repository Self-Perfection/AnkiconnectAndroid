# SPDX-License-Identifier: GPL-3.0-or-later
"""Card actions ported from anki-connect's tests/test_cards.py.

Covers findCards and cardsInfo. anki-connect's setup builds a custom
two-template model so each note yields two cards; AnkiconnectAndroid has no
createModel, so these use the built-in ``Basic`` model (one card per note) and
scope every query to TEST_TAG instead of the throwaway ``test_deck``.

Since the AnkiDroid v2.24.0 bump (Phase 3), the port reports REAL card ids and
the scheduler fields (type/queue/due/interval/factor/reps/lapses/left), read
from the provider's top-level ``cards`` URI. Desktop's extras with no AnkiDroid
source (mod, flags, nextReviews, css) stay absent. On AnkiDroid older than
v2.24.0 the port falls back to synthetic card ids with no scheduler fields, so
the scheduler-field test below requires a device with the ``cards`` URI.

Run with uv (no pip needed): ``uv run pytest test_cards.py``.
"""

import pytest

from conftest import TEST_DECK, TEST_TAG

pytestmark = pytest.mark.core


def make_note(*, front="front1", back="back1"):
    """A Basic note in TEST_DECK, tagged for cleanup."""
    return {
        "deckName": TEST_DECK,
        "modelName": "Basic",
        "fields": {"Front": front, "Back": back},
        "tags": [TEST_TAG],
    }


# --- findCards (anki-connect test_findCards) -------------------------------
# anki-connect asserts a fixed count (4 cards from 2 two-template notes). We
# can't pin a count against a shared deck, so we assert the cards we created
# are found, mirroring the deck-scoped query.


def test_findCards_by_tag(anki, cleanup_notes):
    # Source: tests/test_cards.py::test_findCards (query scoped to our notes).
    anki("addNote", note=make_note(front="acandroid findcards 1"))
    anki("addNote", note=make_note(front="acandroid findcards 2"))

    card_ids = anki("findCards", query=f"tag:{TEST_TAG}")
    assert isinstance(card_ids, list)
    # Basic model: one card per note, so at least the two we just added.
    assert len(card_ids) >= 2
    assert all(isinstance(int(c), int) for c in card_ids)


def test_findCards_matches_findNotes_for_basic(anki, cleanup_notes):
    # Metamorphic: for the single-template Basic model, each note has exactly
    # one card, so findCards and findNotes over the same query agree 1:1.
    # Holds on both paths (real cards URI, and the synthetic findNotes->expand
    # fallback).
    anki("addNote", note=make_note(front="acandroid fc match 1"))
    anki("addNote", note=make_note(front="acandroid fc match 2"))

    note_ids = anki("findNotes", query=f"tag:{TEST_TAG}")
    card_ids = anki("findCards", query=f"tag:{TEST_TAG}")
    assert len(card_ids) == len(note_ids)


def test_findCards_no_match_returns_empty(anki):
    result = anki("findCards", query="tag:acandroid_definitely_no_such_tag_xyz")
    assert result == []


# --- cardsInfo (anki-connect TestCardInfo) ---------------------------------
# Common subset both desktop and AnkiDroid expose. Desktop's note id key is
# "note" and the card ordinal key is "ord"; those are the names asserted here
# so the test passes on the oracle. Scheduler fields are intentionally NOT
# asserted (absent on AnkiDroid).


def test_cardsInfo_with_valid_ids(anki, cleanup_notes):
    # Source: tests/test_cards.py::TestCardInfo::test_with_valid_ids
    note_id = int(anki("addNote", note=make_note(front="acandroid cardsinfo front",
                                                 back="acandroid cardsinfo back")))
    card_ids = anki("findCards", query=f"nid:{note_id}")
    assert len(card_ids) >= 1

    result = anki("cardsInfo", cards=card_ids)
    assert len(result) == len(card_ids)

    info = result[0]
    # --- common subset (plan 2.2): note id, ord, deckName, question, answer,
    #     fields, modelName. ---
    assert int(info["note"]) == note_id
    assert isinstance(info["ord"], int)
    assert info["deckName"] == TEST_DECK
    assert info["modelName"] == "Basic"
    assert isinstance(info["question"], str) and info["question"]
    assert isinstance(info["answer"], str) and info["answer"]

    fields = info["fields"]
    assert fields["Front"]["value"] == "acandroid cardsinfo front"
    assert fields["Back"]["value"] == "acandroid cardsinfo back"
    assert isinstance(fields["Front"]["order"], int)


def test_cardsInfo_scheduler_fields(anki, cleanup_notes):
    # Since the v2.24.0 bump the port reads the real card row, so cardsInfo
    # carries the scheduler fields desktop returns. Requires a device exposing
    # the ``cards`` URI (AnkiDroid >= v2.24.0); pre-bump AnkiDroid uses synthetic
    # ids with no scheduler fields and will (correctly) fail this.
    note_id = int(anki("addNote", note=make_note(front="acandroid sched fields")))
    card_ids = anki("findCards", query=f"nid:{note_id}")
    info = anki("cardsInfo", cards=card_ids)[0]

    for key in ("type", "queue", "due", "interval", "factor", "reps", "lapses", "left"):
        assert key in info, f"cardsInfo missing scheduler field {key!r}"
        assert isinstance(info[key], int)


def test_findCards_returns_real_card_ids(anki, cleanup_notes):
    # notesInfo[..]["cards"] and findCards("nid:..") must report the SAME card
    # ids (both sourced from the same card rows), and cardsInfo on them resolves
    # back to the note. Guards the cross-action cid consistency the migration is
    # about. True on desktop and on the real-cid AnkiDroid path.
    note_id = int(anki("addNote", note=make_note(front="acandroid real cids")))

    from_find = sorted(int(c) for c in anki("findCards", query=f"nid:{note_id}"))
    from_notes = sorted(int(c) for c in anki("notesInfo", notes=[note_id])[0]["cards"])
    assert from_find == from_notes

    for entry in anki("cardsInfo", cards=from_find):
        assert int(entry["note"]) == note_id


def test_cardsInfo_with_incorrect_id(anki):
    # Source: tests/test_cards.py::TestCardInfo::test_with_incorrect_id
    # An unknown card id yields an empty dict entry (not an error).
    result = anki("cardsInfo", cards=[123])
    assert result == [{}]
