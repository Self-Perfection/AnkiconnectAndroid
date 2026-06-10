# SPDX-License-Identifier: GPL-3.0-or-later
"""Tests for clean error responses (no Java stack trace leaking out).

Run with uv (no pip needed): ``uv run pytest test_errors.py``.
"""

import pytest

import client

pytestmark = pytest.mark.edge


def test_unsupported_action_clean_error(anki):
    # getProfiles is not supported on AnkiDroid; it should produce a clean,
    # human-readable error string rather than a Java stack trace dump.
    #
    # On a server where getProfiles IS supported (e.g. desktop AnkiConnect, used
    # as the gold standard to validate this suite) there is no error to inspect,
    # so skip: graceful degradation is an AnkiDroid-specific property.
    try:
        anki("getProfiles")
    except client.AnkiConnectError as exc:
        message = str(exc)
    else:
        pytest.skip("getProfiles is supported on this server; the clean-error "
                    "behaviour under test is AnkiDroid-specific")

    assert "unsupported action" in message
    assert "getProfiles" in message
    # No stack-trace artefacts.
    assert "\tat " not in message
    assert "\n\tat" not in message
    assert "Exception:" not in message
