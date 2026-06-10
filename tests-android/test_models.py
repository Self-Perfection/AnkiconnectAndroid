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
