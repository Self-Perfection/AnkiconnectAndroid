# SPDX-License-Identifier: GPL-3.0-or-later
"""storeMediaFile ported from anki-connect's tests/test_media.py.

Only the store side is portable: AnkiconnectAndroid implements neither
retrieveMediaFile nor deleteMediaFile, so we cannot read the bytes back nor
clean the file up afterwards. Hence this keeps a minimal assertion (the action
returns the stored filename) and intentionally leaves the file in place; the
filename is unique-ish to avoid clobbering real media.

Run with uv (no pip needed): ``uv run pytest test_media.py``.
"""

import base64

import pytest

pytestmark = pytest.mark.core

# Distinct enough not to collide with a user's real media; we cannot delete it.
FILENAME = "_acandroid_test.txt"
BASE64_DATA = base64.b64encode(b"test 1").decode("ascii")


def test_storeMediaFile_returns_filename(anki):
    # Source: tests/test_media.py::test_storeMediaFile_one_file
    # (there it also retrieves to verify bytes; we can't, see module docstring).
    result = anki("storeMediaFile", filename=FILENAME, data=BASE64_DATA)
    assert result == FILENAME
