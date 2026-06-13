# SPDX-License-Identifier: GPL-3.0-or-later
"""multi action ported from anki-connect's tests/test_server.py.

anki-connect's test_multi_request batches ["version"] x3 and asserts the
result array of {"error", "result"} envelopes. Here we batch a couple of
implemented read actions and assert the per-request envelope shape and order.

guiBrowse lives in test_gui.py (verification mode `gui`, opt-in via --run-gui):
it is a GUI action whose real effect is not readable over HTTP, so only its
contract is asserted there.

Run with uv (no pip needed): ``uv run pytest test_server.py``.
"""

import pytest

pytestmark = pytest.mark.core


def _req(action, **params):
    return {"action": action, "params": params, "version": 6}


def test_multi_returns_envelope_per_action_in_order(anki):
    # Source: tests/test_server.py::test_multi_request (batched version calls).
    # We mix version + deckNames to also check ordering is preserved.
    result = anki("multi", actions=[_req("version"), _req("deckNames")])
    assert isinstance(result, list)
    assert len(result) == 2

    # Each entry is a full {"result", "error"} envelope.
    for entry in result:
        assert "result" in entry
        assert entry.get("error") is None

    # Order matches the request order.
    assert str(result[0]["result"]) == "6"
    assert isinstance(result[1]["result"], list)


def test_multi_all_version(anki):
    # Closest to anki-connect's exact case: ["version"] repeated.
    result = anki("multi", actions=[_req("version")] * 3)
    assert result == [{"error": None, "result": 6}] * 3


def test_multi_isolates_a_failing_subaction(anki):
    # A failing sub-action must NOT abort the batch: its slot carries the error,
    # the surrounding actions still succeed (desktop AnkiConnect behaviour). Real
    # clients (anki_notes_creator) batch best-effort actions like deleteMediaFile,
    # which are unsupported here -- without isolation that one error would poison
    # the whole multi.
    result = anki("multi", actions=[
        _req("version"),
        _req("totallyBogusAction_acandroid"),
        _req("deckNames"),
    ])
    assert len(result) == 3
    assert result[0]["result"] == 6 and result[0]["error"] is None
    # failed slot: result null, error message present (substring shared with desktop)
    assert result[1]["result"] is None
    assert "unsupported action" in result[1]["error"]
    assert result[2]["error"] is None and isinstance(result[2]["result"], list)
