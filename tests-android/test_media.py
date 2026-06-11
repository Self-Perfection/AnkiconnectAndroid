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
    #
    # Desktop returns the requested name verbatim ("_acandroid_test.txt").
    # AnkiDroid's media provider uniquifies it by appending a numeric suffix to
    # the stem ("_acandroid_test_<digits>.txt") and there is no API to force an
    # exact name (see MediaAPI.storeMediaFile's TODO). So the everywhere-true
    # contract is weaker than equality: the stem is preserved as a prefix and
    # the extension is kept. Clients MUST reference the returned name, not the
    # one they sent — important for media-storing clients (anki_notes_creator).
    result = anki("storeMediaFile", filename=FILENAME, data=BASE64_DATA)
    assert isinstance(result, str)
    stem, _, ext = FILENAME.rpartition(".")
    assert result.startswith(stem)
    assert result.endswith("." + ext)


# --- deleteMediaFile -------------------------------------------------------
# Source: tests/test_media.py::test_deleteMediaFile (the desktop case stores,
# deletes, then verifies the file is gone via retrieveMediaFile/getMediaFiles).
#
# This is a hard desktop/AnkiDroid divergence (plan 2.3): AnkiDroid's media
# provider (FlashCardsContract.AnkiMedia) is insert-only, so deleteMediaFile
# CANNOT work there and is implemented as a "not supported" stub. Desktop, by
# contrast, deletes the file and returns null without error. No single test
# can pass on both, so the two expectations live in separate tests:
#
#   * test_deleteMediaFile_desktop_contract -- the oracle's behaviour (returns
#     null, no error); SKIPS on AnkiDroid, which rejects the delete.
#   * test_deleteMediaFile_not_supported_on_android -- the port's expectation
#     (a clean "not supported" error); SKIPS on desktop, which accepts it.
#
# The two are symmetric: on each server exactly one runs and the other skips, so
# deleteMediaFile is covered by default on BOTH (no opt-in). The error contract
# is returned synchronously over HTTP, so it is fully automated -- NOT ``manual``
# (which is reserved for effects a human must eyeball). We can't retrieve media
# back over the Android API, so neither test asserts the on-disk effect; they
# assert only the envelope each server returns.

# A name unlikely to collide with real media; deletion of a non-existent file
# is a no-op on desktop (trash_files tolerates it), so this needs no setup.
NONEXISTENT = "_acandroid_delete_probe.txt"


def test_deleteMediaFile_desktop_contract(anki):
    # Desktop returns null and no error even for a file that isn't there. The
    # AnkiDroid media provider is insert-only, so the port rejects it with a
    # clean "not supported" error instead; on that server this test SKIPS
    # (the rejection is asserted by test_deleteMediaFile_not_supported_on_android).
    import client
    try:
        result = anki("deleteMediaFile", filename=NONEXISTENT)
    except client.AnkiConnectError as exc:
        msg = str(exc).lower()
        if "not supported" in msg or "unsupported" in msg:
            pytest.skip("server rejects deleteMediaFile (AnkiDroid); "
                        "desktop-null contract is desktop-only")
        raise
    assert result is None


def test_deleteMediaFile_not_supported_on_android(anki):
    # Android-only expectation: insert-only media provider -> clean
    # "not supported" error (no Java stack trace). Fully automated -- the error
    # is read back over HTTP. To stay green on the gold standard it SKIPS when
    # the server accepts the delete (the desktop oracle); on AnkiDroid the delete
    # is rejected and the assertion runs.
    import client
    try:
        anki("deleteMediaFile", filename=NONEXISTENT)
    except client.AnkiConnectError as exc:
        msg = str(exc).lower()
        assert "not supported" in msg or "unsupported" in msg
        assert "\n\tat " not in str(exc)  # clean message, no Java stack trace
    else:
        pytest.skip("server accepts deleteMediaFile (desktop oracle); "
                    "the not-supported contract is AnkiDroid-only")
