# AnkiconnectAndroid test suite

Behavioral tests for AnkiconnectAndroid. They talk to a **real device**
running the app and AnkiDroid over HTTP — there is no emulator and no mocking.

## Running in Termux (on the device)

```sh
pkg install python
pip install pytest requests
ANKI_CONNECT_URL=http://localhost:8765 pytest
```

## Running from a PC against the phone

Point the client at the phone's IP (the app must be reachable on the network):

```sh
ANKI_CONNECT_URL=http://<phone-ip>:8765 pytest
```

`ANKI_CONNECT_URL` defaults to `http://localhost:8765` when unset.

If the server is unreachable the tests are **skipped** with a clear message
rather than failing with a connection traceback.

## Layout

| File            | Purpose                                                              |
| --------------- | -------------------------------------------------------------------- |
| `client.py`     | Thin client: `invoke(action, **params)` over the AnkiConnect JSON API |
| `conftest.py`   | Fixtures: base URL, an `anki` invoke fixture, test-note cleanup       |
| `test_smoke.py` | Smoke tests: `version`, `deckNames`, `modelNames`                    |

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
