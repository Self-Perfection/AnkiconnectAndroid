# AnkiconnectAndroid test suite

Behavioral tests for AnkiconnectAndroid. They talk to a **real device**
running the app and AnkiDroid over HTTP — there is no emulator and no mocking.

## Running with uv (recommended — no pip)

Dependencies (`pytest`, `requests`) are declared once in `pyproject.toml`, so
[uv](https://docs.astral.sh/uv/) resolves them automatically. No `pip install`,
no manual venv. Run from this `tests-android/` directory:

```sh
uv run pytest -v                 # the whole suite (one command)
uv run pytest test_smoke.py -v   # one file
uv run pytest -k duplicate       # tests matching a name
```

In Termux: `pkg install uv` (or the official installer), then the same
`uv run pytest` commands.

### Layers (run from fundamental to edge)

Tests are tagged with markers; run a layer with `-m`:

```sh
uv run pytest -m smoke -x        # is the server even up & permitted? stop on first fail
uv run pytest -m core            # main happy-path behaviour
uv run pytest -m edge            # edge cases, error handling, regressions
```

`-x` stops at the first failure, so a broken fundamental surfaces immediately.
Tests are independent and self-cleaning — there is no required run order.

### Verification mode (gui / manual are opt-in)

A second, orthogonal axis: how a test is verified. Most tests are headless and
fully automated. Two categories need opting into and are **skipped by default**,
so a bare `uv run pytest` never opens a screen nor needs a human:

```sh
uv run pytest                    # default: gui + manual skipped
uv run pytest --run-gui          # also run `gui` tests (open an AnkiDroid screen;
                                 #   only the API contract is asserted)
uv run pytest --run-manual       # also run `manual` tests (a human confirms the effect)
uv run pytest -m core --run-gui  # composes with layers: core tests, gui included
```

The opt-in is flag-based (not `-m "not gui"`) on purpose, so passing `-m core`
does not silently re-include gui/manual. `--run-gui` is handy against the
desktop oracle, where opening a browser is offscreen and harmless.

## Running with pytest directly

If you already have `pytest` and `requests` installed, just `pytest` works
(it reads `pyproject.toml`):

```sh
pip install pytest requests
pytest                           # whole suite
```

## Pointing at the device

```sh
ANKI_CONNECT_URL=http://<phone-ip>:8765 uv run pytest
```

`ANKI_CONNECT_URL` defaults to `http://localhost:8765` when unset (e.g. when
running inside Termux on the device itself).

### Deck

Tests add notes to the `Default` deck. Override with `ANKI_TEST_DECK`:

```sh
ANKI_TEST_DECK="My Test Deck" uv run pytest test_koplugin.py
```

If the server is unreachable the tests are **skipped** with a clear message
rather than failing with a connection traceback.

### API key

Set `ANKI_CONNECT_KEY` if the server requires one; the client adds it to every
request automatically. AnkiconnectAndroid normally needs no key, so leave it
unset there.

## Validating the suite against desktop AnkiConnect (gold standard)

To check that the tests themselves are correct, point them at a real desktop
AnkiConnect — a known-good server should pass:

```sh
ANKI_CONNECT_URL=http://localhost:8765 ANKI_CONNECT_KEY=<your-key> uv run pytest test_koplugin.py
```

One intentional tolerance for the desktop/AnkiDroid difference:

- `requestPermission` only guarantees `permission`; `requireApiKey`/`version`
  are checked only when present, because desktop does not always return them.

## Layout

Markers stack: the depth layer (`smoke`/`core`/`edge`) and the verification
mode (`gui`/`manual`, opt-in) are independent.

| File                 | Markers      | Purpose                                                             |
| -------------------- | ------------ | ------------------------------------------------------------------- |
| `pyproject.toml`     | —            | Declares deps + registers markers; makes `uv run pytest` work       |
| `client.py`          | —            | Thin client: `invoke(action, **params)` over the AnkiConnect JSON API |
| `conftest.py`        | —            | Fixtures + the `--run-gui`/`--run-manual` opt-in skip hook          |
| `test_smoke.py`      | `smoke`      | Smoke tests: `version`, `deckNames`, `modelNames`                   |
| `test_koplugin.py`   | `core`       | koplugin scenario: requestPermission → addNote → notesInfo → deleteNotes |
| `test_notes.py`      | `core`       | canAddNotes, canAddNotesWithErrorDetail, notesInfo, findNotes, updateNoteFields |
| `test_models.py`     | `core`       | modelNamesAndIds, modelFieldNames read assertions (Basic model)     |
| `test_decks.py`      | `core`       | deckNamesAndIds read assertion (Default present, int id)            |
| `test_media.py`      | `core`       | storeMediaFile returns the filename (store-only; no retrieve/delete) |
| `test_server.py`     | `core`       | `multi` batches actions; asserts per-request envelope shape + order |
| `test_gui.py`        | `core`,`gui` | GUI actions (guiBrowse): contract-only, opt in with `--run-gui`     |
| `test_duplicates.py` | `edge`       | addNote rejects duplicates unless `options.allowDuplicate` is set   |
| `test_errors.py`     | `edge`       | Unsupported action returns a clean error (no Java stack trace)      |

Notes created by tests are tagged `acandroid_test`; the `cleanup_notes`
fixture removes them via `deleteNotes` after each test.

## Prerequisites on the device

- AnkiDroid installed and its API permission granted to AnkiconnectAndroid.
- A deck and a model to add notes against (currently created manually, e.g.
  the default `Basic` model).

## License

GPL-3.0-or-later, matching the rest of this project.

Some behavioral test ideas are derived from
[anki-connect](https://github.com/FooSoft/anki-connect), which is licensed
GPL-3.0-or-later.
