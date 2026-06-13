# Testing approach

Behavioral, black-box tests for AnkiconnectAndroid. They talk to a **real
device** running the app + AnkiDroid over HTTP — no emulator, no mocking. The
suite verifies the *protocol behaviour*, not internal classes.

User-facing how-to (env vars, uv, layout) lives in `README.md`. This file is
the *why* and the conventions for writing new tests.

## Principles

1. **Gold standard = desktop AnkiConnect.** Every test must also pass against a
   real desktop AnkiConnect. It is the oracle for correct behaviour; if a test
   fails there, the test is wrong, not the server. Run it both ways before
   trusting a test (see README "Validating against desktop AnkiConnect").

2. **Reproduce anki-connect's cases faithfully, don't paraphrase.**
   anki-connect's own `tests/` (e.g. `test_notes.py`) encode the spec,
   including regression tests named `test_bugNNN` — a map of already-paid-for
   bugs. Keep each case's *data and assertions* (field values, options, scopes)
   verbatim; rewrite only the setup/teardown, since their fixtures rely on
   actions we don't implement (`createDeck`, `createModel`, `findCards`, ...) —
   use a pre-existing deck + the `Basic` model and tag-based cleanup instead.
   Paraphrasing loses cases: it is how we first missed `duplicateScope: "deck"`,
   which their `test_bug164` covers.

3. **Cover port-specific blind spots with property/metamorphic tests.** Some
   bugs are impossible in desktop AnkiConnect and so absent from its tests, but
   possible here because of the port (Gson JSON parsing, the AnkiDroid
   ContentProvider, case sensitivity). Encode the invariant instead of guessing
   the bug:
   - permuting the order of keys in `fields` must not change the result
     (caught the duplicate-key-on-wrong-field bug);
   - deck/model name case must not change the result;
   - sending optional blocks (`options`) or omitting them must agree.

4. **Build cases from what real clients actually send.** Both duplicate bugs
   were triggered by anki.koplugin's real requests (its default
   `duplicateScope: "deck"`, its unordered `fields`). Mirror real client
   payloads (`test_koplugin.py`); capturing an actual request beats an
   idealised one.

5. **Pin the whole response schema, not anki-connect's chosen subset
   (`test_schema_parity.py`).** anki-connect's own tests assert a hand-picked
   few fields per response, so a faithful port can silently drop the rest and
   still pass — `notesInfo` shipped "complete" without `cards`/`mod` because
   their `test_notesInfo` checks neither. For every action returning a
   structured object, record the desktop oracle's full key set and assert our
   response contains it, minus an **explicit, documented** unsupported list
   (each key with a reason it has no AnkiDroid source). Dropping a field means
   moving it to that list, not deleting an assertion. When you add a new
   object-returning action, add its key set there in the same change.

## Conventions

- Each test is **independent and self-cleaning**: it creates its own state and
  removes it. Notes are tagged `acandroid_test`; the `cleanup_notes` fixture
  deletes them afterwards. Never rely on test execution order.
- If the server is unreachable, the `anki` fixture **skips** (not fails).
- **Prefer tests that pass on every server** over ones that skip on the gold
  standard. Assert the everywhere-true common denominator: e.g. for an
  unsupported action, use a clearly bogus name (rejected by desktop *and*
  AnkiDroid) and assert the shared `"unsupported action"` substring, rather
  than an action that only one of them rejects. Skip on the gold standard only
  when a behaviour is genuinely impossible to assert there.
- Files are grouped by topic (`test_<area>.py`); functions are `test_*`.
  pytest discovers them all — see README for the single run-all command.

## Two marker axes

Markers fall on two independent axes; a test may carry one from each.

- **Depth** — `smoke` / `core` / `edge`. How fundamental the check is.
- **Verification mode** — how the test is verified. Default (unmarked) is
  headless and fully automated: assert the *real* result via the API. Two
  opt-in categories are skipped unless their flag is passed (see `conftest.py`):
  - `gui` (`--run-gui`): the action's real effect can't be read back over HTTP
    (it opens a screen), so assert only the **contract** — a result of the
    right shape, no error — never the on-screen effect. Mark an action `gui`
    only when its effect is genuinely unobservable via the API; e.g. `guiBrowse`
    (fires an intent, returns `[]`) is `gui`, but `guiSelectedNotes` → `[]`
    returns a readable result and is a normal automated test.
  - `manual` (`--run-manual`): needs a human to confirm (a screen looked right,
    KOReader actually added a card). Keep these runnable but out of the default.
- Make `gui`/`manual` opt-in via the conftest flags, **not** `addopts -m "not
  gui"`: a `-m` on the command line replaces the addopts `-m`, silently
  re-including them. Flag-based skipping composes with `-m` layer selection.
