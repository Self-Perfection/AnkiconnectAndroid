# SPDX-License-Identifier: GPL-3.0-or-later
"""GUI actions — contract-only tests (verification mode ``gui``).

These trigger a GUI action whose real effect the HTTP API cannot read back, so
they assert only the *contract* (a result of the documented shape, no error or
stack trace) — not that the right thing actually appeared on screen. On a real
device they briefly open an AnkiDroid screen (offscreen/harmless on the desktop
oracle), so they are DESELECTED by default; opt in with ``--run-gui``.

Run: ``uv run pytest --run-gui test_gui.py``
"""

import pytest

# Stacks two axes: `core` (depth) + `gui` (verification mode). `-m core` alone
# still skips it (the gui opt-in is flag-based, see conftest); `-m core
# --run-gui` runs it.
pytestmark = [pytest.mark.core, pytest.mark.gui]


def test_guiBrowse_returns_a_list(anki):
    # Contract both servers honour: guiBrowse opens the card browser and returns
    # a JSON list of card ids. AnkiconnectAndroid fires an intent and always
    # returns [] (it does not read the results back, by design); desktop returns
    # the matching card ids. So the everywhere-true assertion is "a list, no
    # error" — not its contents.
    result = anki("guiBrowse", query="deck:Default")
    assert isinstance(result, list)
