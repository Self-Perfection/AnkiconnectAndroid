# SPDX-License-Identifier: GPL-3.0-or-later
"""Model read actions ported from anki-connect's tests/test_models.py.

anki-connect's cases create a ``test_model``; AnkiconnectAndroid implements no
createModel, so these assert against the built-in ``Basic`` model instead
(fields ``Front``/``Back``), keeping the shape of anki-connect's assertions.

Run with uv (no pip needed): ``uv run pytest test_models.py``.
"""

import pytest

pytestmark = pytest.mark.core


def test_modelNamesAndIds(anki):
    # Source: tests/test_models.py::test_modelNamesAndIds
    # (there it asserts test_model maps to an int; Basic is always present).
    result = anki("modelNamesAndIds")
    assert isinstance(result, dict)
    assert "Basic" in result
    assert isinstance(result["Basic"], int)


def test_modelFieldNames(anki):
    # Source: tests/test_models.py::test_modelFieldNames
    # (there: test_model -> ["field1", "field2"]; Basic -> ["Front", "Back"]).
    result = anki("modelFieldNames", modelName="Basic")
    assert result == ["Front", "Back"]


# --- modelStyling (anki-connect TestStyling::test_modelStyling) ------------
# Read-only on AnkiDroid (Model.CSS is queryable; updateModelStyling is not
# implemented), so only the read is portable.


def test_modelStyling(anki):
    # Source: tests/test_models.py::TestStyling::test_modelStyling
    # (there test_model's css is "* {}"; Basic ships a real stylesheet). The
    # everywhere-true contract is the shape: {"css": <str>} with the model's
    # actual CSS, which for Basic targets the ``.card`` selector.
    result = anki("modelStyling", modelName="Basic")
    assert isinstance(result, dict)
    assert isinstance(result["css"], str)
    assert ".card" in result["css"]


def test_modelStyling_missing_model_errors(anki):
    # Desktop raises "model was not found: X"; over HTTP that is a non-null
    # error envelope. Assert the clean, shared message substring.
    import client
    with pytest.raises(client.AnkiConnectError, match="was not found"):
        anki("modelStyling", modelName="NoSuchModel_acandroid_xyz")
