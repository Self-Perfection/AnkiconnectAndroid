# SPDX-License-Identifier: GPL-3.0-or-later
"""GUI actions.

Most trigger a GUI action whose real effect the HTTP API cannot read back, so
they assert only the *contract* (a result of the documented shape, no error or
stack trace) — not that the right thing actually appeared on screen. Those
carry the ``gui`` marker: on a real device they briefly open an AnkiDroid
screen (offscreen/harmless on the desktop oracle), so they are DESELECTED by
default; opt in with ``--run-gui``.

A few "gui*" actions return a *readable* result over HTTP (``guiSelectedNotes``
-> a list; ``guiAddNoteSetData`` -> an error when no Add dialog is open) and so
are ordinary, fully-automated tests — NOT marked ``gui``.

Run: ``uv run pytest --run-gui test_gui.py``
"""

import pytest

import client
from conftest import TEST_DECK, TEST_TAG

# Depth only. The screen-opening tests add `gui` per-function; the readable
# ones (guiSelectedNotes / guiAddNoteSetData) stay automated.
pytestmark = pytest.mark.core


def _make_note(front):
    return {
        "deckName": TEST_DECK,
        "modelName": "Basic",
        "fields": {"Front": front, "Back": "back"},
        "tags": [TEST_TAG],
    }


@pytest.mark.gui
def test_guiBrowse_returns_a_list(anki):
    # Contract both servers honour: guiBrowse opens the card browser and returns
    # a JSON list of card ids. AnkiconnectAndroid fires an intent and always
    # returns [] (it does not read the results back, by design); desktop returns
    # the matching card ids. So the everywhere-true assertion is "a list, no
    # error" — not its contents.
    result = anki("guiBrowse", query="deck:Default")
    assert isinstance(result, list)


# --- guiEditNote (anki-connect test_graphical: guiEditNote opens the editor) -
# Opens the note editor for a real note id; the on-screen effect is
# unobservable over HTTP, so this asserts only the contract (no error, null
# result). AnkiconnectAndroid fires an intent like guiBrowse; desktop opens the
# Edit dialog. `gui` because it pops a screen on a device.


@pytest.mark.gui
def test_guiEditNote_contract(anki, cleanup_notes):
    note_id = int(anki("addNote", note=_make_note("acandroid guiedit front")))
    # Both servers: opens the editor and returns null (no readable effect).
    result = anki("guiEditNote", note=note_id)
    assert result is None


# --- guiSelectCard ---------------------------------------------------------
# Desktop semantics: select a single card in the open Browser, returning True
# (or False if no browser). The desktop oracle returns True even with no
# browser open. On AnkiDroid there is no Browser table to drive, so the port
# degrades to a not-supported stub. The everywhere-true assertion is only the
# envelope shape: no stack trace, and a boolean result OR a clean error.


@pytest.mark.gui
def test_guiSelectCard_contract(anki, cleanup_notes):
    note_id = int(anki("addNote", note=_make_note("acandroid guiselectcard")))
    card_ids = anki("findCards", query=f"nid:{note_id}")
    assert card_ids
    try:
        result = anki("guiSelectCard", card=int(card_ids[0]))
    except client.AnkiConnectError as exc:
        # Acceptable on AnkiDroid (no Browser): a clean, human-readable error.
        msg = str(exc)
        assert msg and "\n\tat " not in msg  # no Java stack trace leaked
    else:
        # Desktop: a boolean. (Returns True even with no browser on the oracle.)
        assert isinstance(result, bool)


# --- guiSelectedNotes ------------------------------------------------------
# Returns a READABLE result over HTTP (the selected note ids, or [] when no
# Browser is open), so this is a normal automated test, NOT `gui`. With no
# Browser open both the desktop oracle and the AnkiDroid degrade return [].


def test_guiSelectedNotes_returns_list(anki):
    # Source: tests/test_graphical.py::TestSelectedNotes (there it drives an
    # open Browser; we assert only the no-Browser baseline, which is [] and is
    # the everywhere-true contract).
    result = anki("guiSelectedNotes")
    assert isinstance(result, list)


# --- guiAddNoteSetData -----------------------------------------------------
# Desktop semantics: populate the Add Note dialog if it is open. With no dialog
# open, behaviour varies by server: newer anki-connect returns a result dict
# carrying its own {"error": "...", "code": 1}; older anki-connect (the current
# oracle) and the AnkiDroid port (no Add dialog, not supported) reject it with a
# non-null error envelope. The everywhere-true contract is "no silent success
# and no Java stack trace" — either an error envelope, or a result dict that
# reports the failure in-band.


def test_guiAddNoteSetData_no_dialog_is_not_silent_success(anki):
    note = {"fields": {"Front": "acandroid guiadddata", "Back": "x"}}
    try:
        result = anki("guiAddNoteSetData", note=note)
    except client.AnkiConnectError as exc:
        # Error-envelope form (this oracle: "unsupported action"; Android:
        # "not supported"). Must be a clean, human-readable string.
        msg = str(exc)
        assert msg and "\n\tat " not in msg  # no Java stack trace leaked
    else:
        # In-band form: a dict reporting the failure (no open dialog).
        assert isinstance(result, dict)
        assert "error" in result
