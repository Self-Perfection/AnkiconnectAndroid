# SPDX-License-Identifier: GPL-3.0-or-later
"""Tests for clean error responses (no Java stack trace leaking out)."""

import pytest

import client


def test_unsupported_action_clean_error(anki):
    # getProfiles is not supported on AnkiDroid; it should produce a clean,
    # human-readable error string rather than a Java stack trace dump.
    with pytest.raises(client.AnkiConnectError) as exc_info:
        anki("getProfiles")

    message = str(exc_info.value)
    assert "unsupported action" in message
    assert "getProfiles" in message
    # No stack-trace artefacts.
    assert "\tat " not in message
    assert "\n\tat" not in message
    assert "Exception:" not in message
