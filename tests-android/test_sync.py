# SPDX-License-Identifier: GPL-3.0-or-later
"""sync action.

`sync` has real, irreversible side effects (it talks to AnkiWeb) and, on a
device, brings AnkiDroid to the foreground, so it is `manual` (opt-in,
needs a human to confirm a sync actually ran). It is also rate-limited by
AnkiDroid to ~1 sync / 2 minutes.

The everywhere-true contract across servers: no silent garbage / no Java stack
trace, and either a `null` result or a clean human-readable error. The port
fires AnkiDroid's fire-and-forget `com.ichi2.anki.DO_SYNC` intent and returns
`null` immediately (it cannot wait for the sync or read its outcome); the
desktop oracle returns `null` when AnkiWeb auth is configured and a clean error
otherwise.

Run: ``uv run pytest --run-manual test_sync.py``
"""

import pytest

import client

pytestmark = pytest.mark.core


@pytest.mark.manual
def test_sync_contract(anki):
    try:
        result = anki("sync")
    except client.AnkiConnectError as exc:
        # Acceptable (e.g. desktop with no AnkiWeb auth configured): a clean,
        # human-readable error, never a leaked Java stack trace.
        msg = str(exc)
        assert msg and "\n\tat " not in msg
    else:
        # AnkiDroid (fire-and-forget) and desktop-with-auth both return null.
        assert result is None
