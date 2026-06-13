# SPDX-License-Identifier: GPL-3.0-or-later
"""Response-schema parity against the desktop AnkiConnect oracle.

WHY THIS FILE EXISTS. Our other test files faithfully port anki-connect's own
tests (see CLAUDE.md principle #2), but those assert only a hand-picked subset of
each response's fields. Faithful porting therefore inherits anki-connect's
coverage gaps: its ``test_notesInfo`` never checks ``cards``, so the port could
-- and did -- silently omit ``cards`` (and ``mod``) while looking "complete".

These tests pin the *whole response schema* instead: the set of keys desktop
returns for a representative input, minus an explicit, documented set of keys we
cannot source from AnkiDroid. A field we drop without listing it as unsupported
makes the test fail, so "complete" stops being a guess.

``DESKTOP_KEYS`` were captured from the oracle (desktop AnkiConnect). Run against
the oracle this re-confirms they're accurate AND flags drift (desktop adding a
key we don't know about); run against a device it asserts the port returns every
desktop key except the documented gaps.

Run with uv (no pip needed): ``uv run pytest test_schema_parity.py``.
"""

import pytest

import client
from conftest import TEST_DECK, TEST_TAG

pytestmark = pytest.mark.core


def _basic_note(front):
    return {
        "deckName": TEST_DECK,
        "modelName": "Basic",
        "fields": {"Front": front, "Back": "back"},
        "tags": [TEST_TAG],
    }


# Keys desktop AnkiConnect returns for ONE element of each action's result.
# Captured from the oracle; keep in sync with it (the *_drift tests below catch
# desktop additions when this suite is run against the oracle).
DESKTOP_KEYS = {
    "notesInfo": {"noteId", "modelName", "tags", "fields", "cards", "mod", "profile"},
    "cardsInfo": {
        "cardId", "fields", "fieldOrder", "question", "answer", "modelName", "ord",
        "deckName", "css", "factor", "interval", "note", "type", "queue", "due",
        "reps", "lapses", "left", "mod", "nextReviews", "flags",
    },
}

# Keys we intentionally do NOT provide, each with the reason. Subtracted from the
# required set; everything else in DESKTOP_KEYS MUST be present. To stop providing
# a key, move it here with a reason -- don't just delete the assertion.
UNSUPPORTED_KEYS = {
    "notesInfo": {
        "profile": "AnkiDroid is single-collection; no profile concept in FlashCardsContract",
    },
    "cardsInfo": {
        "mod": "no card modification-time column in FlashCardsContract.Card",
        "flags": "no card flags column in FlashCardsContract.Card",
        "nextReviews": "desktop computes these via scheduler simulation; no provider equivalent",
    },
}


def _assert_parity(action, actual_keys):
    required = DESKTOP_KEYS[action] - set(UNSUPPORTED_KEYS[action])
    missing = required - actual_keys
    assert not missing, (
        f"{action} response is missing desktop keys {sorted(missing)}. "
        f"Implement them, or add each to UNSUPPORTED_KEYS[{action!r}] with a reason."
    )


def test_notesInfo_schema_parity(anki, cleanup_notes):
    note_id = int(anki("addNote", note=_basic_note("acandroid schema notesinfo")))
    info = anki("notesInfo", notes=[note_id])[0]
    _assert_parity("notesInfo", set(info.keys()))


def test_cardsInfo_schema_parity(anki, cleanup_notes):
    # Needs the real cards URI to carry the scheduler fields; on pre-v2.24.0
    # AnkiDroid (synthetic fallback) those are absent and this fails honestly.
    note_id = int(anki("addNote", note=_basic_note("acandroid schema cardsinfo")))
    cards = anki("findCards", query=f"nid:{note_id}")
    info = anki("cardsInfo", cards=cards)[0]
    _assert_parity("cardsInfo", set(info.keys()))


# --- drift guard: only meaningful on the desktop oracle (it returns every key).
# On a device the response legitimately lacks UNSUPPORTED_KEYS, so skip there. We
# detect "this is the oracle" by the absence of the port-only `buildInfo` action.


def _is_desktop_oracle(base_url):
    try:
        client.invoke("buildInfo", url=base_url)
        return False  # buildInfo exists -> our port, not desktop
    except Exception:
        return True


@pytest.mark.parametrize("action", sorted(DESKTOP_KEYS))
def test_desktop_keys_not_stale(anki, base_url, cleanup_notes, action):
    if not _is_desktop_oracle(base_url):
        pytest.skip("drift guard runs against the desktop oracle only")

    note_id = int(anki("addNote", note=_basic_note(f"acandroid drift {action}")))
    if action == "notesInfo":
        obj = anki("notesInfo", notes=[note_id])[0]
    else:
        obj = anki("cardsInfo", cards=anki("findCards", query=f"nid:{note_id}"))[0]

    new_keys = set(obj.keys()) - DESKTOP_KEYS[action]
    assert not new_keys, (
        f"desktop AnkiConnect grew new {action} keys {sorted(new_keys)} not in "
        f"DESKTOP_KEYS -- update DESKTOP_KEYS and decide support for each."
    )
