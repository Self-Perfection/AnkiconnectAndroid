# SPDX-License-Identifier: GPL-3.0-or-later
"""Note actions ported from anki-connect's tests/test_notes.py.

Covers canAddNotes, canAddNotesWithErrorDetail, notesInfo, findNotes and
updateNoteFields. anki-connect's own setup uses createModel/createDeck/
findCards, which AnkiconnectAndroid does not implement, so the setup is
rewritten here: notes are added to TEST_DECK with the built-in ``Basic`` model
(fields ``Front``/``Back``) and tagged TEST_TAG so ``cleanup_notes`` removes
them. The case data and assertions follow anki-connect faithfully, adapted to
the Basic model's field names.

Run with uv (no pip needed): ``uv run pytest test_notes.py``.
"""

import pytest

import client
from conftest import TEST_DECK, TEST_TAG

pytestmark = pytest.mark.core


def make_note(*, front="front1", back="back1", allow_duplicates=False):
    """A Basic note in TEST_DECK, tagged for cleanup.

    Mirrors anki-connect's tests/test_notes.py ``make_note`` (Basic model,
    Front/Back), but pins the deck/tag to this suite's conventions.
    """
    note = {
        "deckName": TEST_DECK,
        "modelName": "Basic",
        "fields": {"Front": front, "Back": back},
        "tags": [TEST_TAG],
    }
    if allow_duplicates:
        note["options"] = {"allowDuplicate": True}
    return note


# --- canAddNotes (anki-connect TestCanAddNotes) ----------------------------


def test_canAddNotes(anki, cleanup_notes):
    # Source: tests/test_notes.py::TestCanAddNotes::test_canAddNotes
    notes = [make_note(front="acandroid canadd foo"),
             make_note(front="acandroid canadd bar")]
    result = anki("canAddNotes", notes=notes)
    assert result == [True, True]


def test_canAddNotes_will_not_add_duplicates_unless_options_say_aye(anki, cleanup_notes):
    # Source: tests/test_notes.py::TestCanAddNotes::
    #         test_canAddNotes_will_not_add_duplicates_if_options_do_not_say_aye
    foo = "acandroid canadd dup foo"
    bar = "acandroid canadd dup bar"
    baz = "acandroid canadd dup baz"

    # Actually add foo and bar so the following foo is a real duplicate.
    assert anki("addNote", note=make_note(front=foo)) is not None
    assert anki("addNote", note=make_note(front=bar)) is not None

    notes = [
        make_note(front=foo),                              # duplicate -> False
        make_note(front=baz),                              # new -> True
        make_note(front=foo, allow_duplicates=True),       # allowed -> True
    ]
    result = anki("canAddNotes", notes=notes)
    assert result == [False, True, True]


# --- canAddNotesWithErrorDetail --------------------------------------------
# No anki-connect test exists for the bulk action; this asserts the documented
# response shape: a list of {"canAdd": bool, "error": str} dicts (desktop omits
# "error" when canAdd is True, per canAddNoteWithErrorDetail in plugin).


def test_canAddNotesWithErrorDetail_happy_path(anki, cleanup_notes):
    notes = [make_note(front="acandroid canadderr foo"),
             make_note(front="acandroid canadderr bar")]
    result = anki("canAddNotesWithErrorDetail", notes=notes)
    assert isinstance(result, list)
    assert len(result) == 2
    for entry in result:
        assert entry["canAdd"] is True


def test_canAddNotesWithErrorDetail_reports_duplicate(anki, cleanup_notes):
    front = "acandroid canadderr dup"
    assert anki("addNote", note=make_note(front=front)) is not None

    notes = [
        make_note(front=front),                          # duplicate
        make_note(front="acandroid canadderr fresh"),    # new
    ]
    result = anki("canAddNotesWithErrorDetail", notes=notes)
    assert len(result) == 2

    assert result[0]["canAdd"] is False
    assert "error" in result[0]
    assert "duplicate" in str(result[0]["error"]).lower()

    assert result[1]["canAdd"] is True


# --- notesInfo (anki-connect test_notesInfo) -------------------------------


def test_notesInfo(anki, cleanup_notes):
    # Source: tests/test_notes.py::test_notesInfo, adapted to the Basic model.
    note_id = anki("addNote", note=make_note(front="acandroid info front",
                                             back="acandroid info back"))
    assert note_id is not None
    note_id = int(note_id)

    result = anki("notesInfo", notes=[note_id])
    assert len(result) == 1
    info = result[0]
    assert int(info["noteId"]) == note_id
    assert info["modelName"] == "Basic"
    assert TEST_TAG in info["tags"]

    # fields is a map field-name -> {"value", "order"}.
    fields = info["fields"]
    assert fields["Front"]["value"] == "acandroid info front"
    assert fields["Back"]["value"] == "acandroid info back"
    assert isinstance(fields["Front"]["order"], int)
    assert isinstance(fields["Back"]["order"], int)
    assert fields["Front"]["order"] != fields["Back"]["order"]


# --- findNotes (anki-connect test_findNotes) -------------------------------


def test_findNotes_by_deck(anki, cleanup_notes):
    # Source: tests/test_notes.py::test_findNotes (query="deck:...").
    id1 = int(anki("addNote", note=make_note(front="acandroid find deck 1")))
    id2 = int(anki("addNote", note=make_note(front="acandroid find deck 2")))

    # Scope to this suite's tag so other notes in the deck don't leak in.
    result = anki("findNotes", query=f'deck:"{TEST_DECK}" tag:{TEST_TAG}')
    assert {id1, id2} <= {int(x) for x in result}


def test_findNotes_by_tag(anki, cleanup_notes):
    note_id = int(anki("addNote", note=make_note(front="acandroid find tag")))
    result = anki("findNotes", query=f"tag:{TEST_TAG}")
    assert note_id in {int(x) for x in result}


def test_findNotes_no_match_returns_empty(anki):
    result = anki("findNotes", query="tag:acandroid_definitely_no_such_tag_xyz")
    assert result == []


# --- updateNoteFields (anki-connect TestUpdateNoteFields) ------------------


def test_updateNoteFields(anki, cleanup_notes):
    # Source: tests/test_notes.py::TestUpdateNoteFields::test_updateNoteFields
    note_id = int(anki("addNote", note=make_note(front="acandroid update front",
                                                 back="before")))

    anki("updateNoteFields",
         note={"id": note_id, "fields": {"Front": "acandroid update front",
                                         "Back": "after"}})

    info = anki("notesInfo", notes=[note_id])
    assert info[0]["fields"]["Back"]["value"] == "after"


def test_updateNoteFields_will_not_update_invalid_notes(anki):
    # Source: tests/test_notes.py::TestUpdateNoteFields::
    #         test_updateNoteFields_will_not_update_invalid_notes
    # Desktop raises NotFoundError; over the HTTP API this surfaces as a
    # non-null error envelope, i.e. client.AnkiConnectError.
    bad_note = {"id": 123, "fields": {"Front": "x", "Back": "y"}}
    with pytest.raises(client.AnkiConnectError):
        anki("updateNoteFields", note=bad_note)


# --- updateNote (anki-connect TestUpdateNote) ------------------------------
# updateNote = updateNoteFields + tag rewrite; requires fields or tags.


def test_updateNote(anki, cleanup_notes):
    # Source: tests/test_notes.py::TestUpdateNote::test_updateNote
    # (there: field1/field2 -> frontbar/backbar, tags -> ["foobar"]). Adapted
    # to the Basic model's Front/Back. Keeps TEST_TAG so cleanup still finds it.
    note_id = int(anki("addNote", note=make_note(front="acandroid updatenote front",
                                                 back="before")))

    new_fields = {"Front": "frontbar", "Back": "backbar"}
    new_tags = ["foobar", TEST_TAG]
    anki("updateNote", note={"id": note_id, "fields": new_fields, "tags": new_tags})

    info = anki("notesInfo", notes=[note_id])[0]
    assert info["fields"]["Front"]["value"] == "frontbar"
    assert info["fields"]["Back"]["value"] == "backbar"
    assert {*info["tags"]} == {"foobar", TEST_TAG}


def test_updateNote_only_tags(anki, cleanup_notes):
    # updateNote with only a "tags" property rewrites tags, leaving fields.
    note_id = int(anki("addNote", note=make_note(front="acandroid updatenote tagsonly",
                                                 back="keepme")))

    anki("updateNote", note={"id": note_id, "tags": [TEST_TAG, "extratag"]})

    info = anki("notesInfo", notes=[note_id])[0]
    assert info["fields"]["Back"]["value"] == "keepme"  # fields untouched
    assert {*info["tags"]} == {TEST_TAG, "extratag"}


def test_updateNote_requires_either_fields_or_tags(anki, cleanup_notes):
    # Source: tests/test_notes.py::TestUpdateNote::
    #         test_updateNote_requires_either_fields_or_tags
    note_id = int(anki("addNote", note=make_note(front="acandroid updatenote require")))
    with pytest.raises(client.AnkiConnectError, match="ust provide"):
        anki("updateNote", note={"id": note_id})


# --- addTags (anki-connect TestTags::test_addTags) -------------------------


def test_addTags(anki, cleanup_notes):
    # Source: tests/test_notes.py::TestTags::test_addTags
    # (there note1 starts with "tag1"; here it starts with TEST_TAG). Adds a
    # second tag and asserts both are present.
    note_id = int(anki("addNote", note=make_note(front="acandroid addtags")))

    anki("addTags", notes=[note_id], tags="tag2")

    tags = anki("notesInfo", notes=[note_id])[0]["tags"]
    assert {*tags} == {TEST_TAG, "tag2"}


def test_addTags_multiple_notes(anki, cleanup_notes):
    # addTags applies to every note id passed; assert the new tag lands on all.
    id1 = int(anki("addNote", note=make_note(front="acandroid addtags multi 1")))
    id2 = int(anki("addNote", note=make_note(front="acandroid addtags multi 2")))

    anki("addTags", notes=[id1, id2], tags="sharedtag")

    for note_id in (id1, id2):
        tags = anki("notesInfo", notes=[note_id])[0]["tags"]
        assert "sharedtag" in tags
