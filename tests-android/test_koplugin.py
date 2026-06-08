# SPDX-License-Identifier: GPL-3.0-or-later
"""End-to-end scenario mirroring how the KOReader anki.koplugin client talks
to the server: requestPermission -> addNote -> notesInfo -> deleteNotes.

These run against a real device; if the server is unreachable they skip
cleanly via the ``anki`` fixture in conftest.py.
"""

from conftest import TEST_TAG


def test_request_permission(anki):
    # koplugin's is_running() checks result.permission != "denied".
    result = anki("requestPermission")
    assert result["permission"] == "granted"
    assert result["requireApiKey"] is False
    assert str(result["version"]) == "6"


def test_koplugin_add_info_delete(anki, cleanup_notes):
    # 1. Permission check, exactly as koplugin does before going online.
    permission = anki("requestPermission")
    assert permission["permission"] == "granted"

    # 2. addNote. koplugin sends a top-level "key" field alongside the note.
    note = {
        "deckName": "Default",
        "modelName": "Basic",
        "fields": {"Front": "koplugin front", "Back": "koplugin back"},
        "tags": [TEST_TAG],
    }
    note_id = anki("addNote", note=note, key="")
    assert note_id is not None
    note_id = int(note_id)

    # 3. notesInfo for the new note: it should exist.
    info = anki("notesInfo", notes=[note_id])
    assert isinstance(info, list)
    assert len(info) == 1
    assert int(info[0]["noteId"]) == note_id

    # 4. deleteNotes, then findNotes should no longer return it.
    assert anki("deleteNotes", notes=[note_id]) is None
    remaining = anki("findNotes", query=f"nid:{note_id}")
    assert remaining == []
