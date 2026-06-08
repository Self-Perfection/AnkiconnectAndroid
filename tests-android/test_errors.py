# SPDX-License-Identifier: GPL-3.0-or-later
# /// script
# requires-python = ">=3.9"
# dependencies = ["pytest>=8", "requests>=2.31"]
# ///
"""Tests for clean error responses (no Java stack trace leaking out).

Run directly with uv (no pip needed): ``uv run test_errors.py``.
"""

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


if __name__ == "__main__":
    import sys

    sys.exit(pytest.main([__file__, "-v"]))
