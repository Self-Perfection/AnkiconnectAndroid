# SPDX-License-Identifier: GPL-3.0-or-later
"""Tests for clean error responses (no Java stack trace leaking out).

Run with uv (no pip needed): ``uv run pytest test_errors.py``.
"""

import pytest

import client

pytestmark = pytest.mark.edge


def test_unsupported_action_clean_error(anki):
    # A nonexistent action must yield a clean {"result": null, "error": ...}
    # envelope on every server, not a Java stack-trace dump. Using a clearly
    # bogus action (unsupported everywhere) keeps this test meaningful against
    # both AnkiconnectAndroid and desktop AnkiConnect — no skip needed.
    #
    # Desktop replies "unsupported action"; AnkiconnectAndroid additionally
    # echoes the action name ("unsupported action: <name>"). The common,
    # everywhere-true assertion is the "unsupported action" substring.
    with pytest.raises(client.AnkiConnectError) as exc_info:
        anki("definitelyNotARealActionXyz")

    message = str(exc_info.value)
    assert "unsupported action" in message.lower()
    # No Java stack-trace artefacts leaking through.
    assert "\tat " not in message
    assert "\n\tat" not in message
    assert "Exception:" not in message
