# SPDX-License-Identifier: GPL-3.0-or-later
"""Deck read actions ported from anki-connect's tests/test_decks.py.

anki-connect asserts deckNamesAndIds == {"Default": 1} on a fresh profile.
A shared device has more decks, so this only asserts that the always-present
"Default" deck is there and maps to an int id.

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
