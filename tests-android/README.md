# AnkiconnectAndroid test suite

Behavioral tests for AnkiconnectAndroid. They talk to a **real device**
running the app and AnkiDroid over HTTP — there is no emulator and no mocking.

## Running with uv (recommended — no pip)

Each `test_*.py` carries its dependencies inline (PEP 723), so [uv](https://docs.astral.sh/uv/)
resolves `pytest`/`requests` automatically in an ephemeral environment. No
`pip install`, no venv:

```sh
uv run test_koplugin.py        # one file
uv run test_smoke.py
uv run test_errors.py
```

In Termux: `pkg install uv` (or the official installer), then the same
`uv run <file>` commands.

## Running with pytest directly

If you already have `pytest` and `requests` installed:

```sh
pip install pytest requests
pytest                          # whole suite
```

## Pointing at the device

```sh
ANKI_CONNECT_URL=http://<phone-ip>:8765 uv run test_koplugin.py
```

`ANKI_CONNECT_URL` defaults to `http://localhost:8765` when unset (e.g. when
running inside Termux on the device itself).

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
ANKI_CONNECT_URL=http://localhost:8765 ANKI_CONNECT_KEY=<your-key> uv run test_koplugin.py
```

Two intentional differences from AnkiDroid:

- `requestPermission` only guarantees `permission`; `requireApiKey`/`version`
  are checked only when present, because desktop does not always return them.
- `test_errors.py` **skips** when `getProfiles` works (desktop supports it):
  the clean-error-for-unsupported-action behaviour is AnkiDroid-specific.

## Layout

| File               | Purpose                                                               |
| ------------------ | --------------------------------------------------------------------- |
| `client.py`        | Thin client: `invoke(action, **params)` over the AnkiConnect JSON API |
| `conftest.py`      | Fixtures: base URL, an `anki` invoke fixture, test-note cleanup        |
| `test_smoke.py`    | Smoke tests: `version`, `deckNames`, `modelNames`                     |
| `test_errors.py`   | Unsupported action returns a clean error (no Java stack trace)        |
| `test_koplugin.py` | koplugin scenario: requestPermission → addNote → notesInfo → deleteNotes |

`client.py` and `conftest.py` are imported (not run directly), so they carry
no inline dependency block; they run inside the environment of whichever
`test_*.py` you launch.

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
