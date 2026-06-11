# AnkiconnectAndroid — working notes for agents

Ports the **AnkiConnect HTTP JSON API** onto the **AnkiDroid API**, so clients
written for desktop AnkiConnect (KOReader `anki.koplugin`, the `anki_notes_creator`
browser extension, yomitan, …) work against AnkiDroid over HTTP.

Priorities: (1) `anki.koplugin`, (2) `anki_notes_creator`, (3) everything the
AnkiDroid API can expose; the physically-impossible rest = honest *not supported*
errors. Full plan: `../AnkiconnectAndroid-improvement-plan.md`; coverage matrix:
`../anki-connect-android-api-coverage.md`.

## Architecture

HTTP server (NanoHTTPD). Dispatcher: `routing/AnkiAPIRouting.findRoute()` — a big
`switch` on the action. **A new action = a `case` + a private handler method +
(as needed) a `Parser` method + a domain method in `ankidroid_api/`** (`IntegratedAPI`,
`NoteAPI`, `DeckAPI`, `ModelAPI`, `MediaAPI`, `CardAPI`). Responses go through
`formatSuccessReply` (version ≤4 vs ≥5 envelope). Errors go through
`findRouteHandleError`, which returns a clean `{result:null, error:<message>}` —
**never leak a Java stack trace into `error`** (full trace goes to `Log.e`). For
unsupported actions throw with a clear message, e.g. `"X is not supported on AnkiDroid"`.

Two channels to AnkiDroid:
- **`AddContentApi`** — high-level *adding only* (notes, decks, models). No update/delete.
- **`FlashCardsContract` + `ContentResolver`** — everything else (query/update notes,
  cards, models, decks, media, scheduling). **This is the primary channel** and is
  already used throughout (`Note.CONTENT_URI_V2`, `Note._ID/CSUM/MID`, `Card.*`,
  `Model.CSS`, `AnkiMedia.*`).

## Source of truth for the AnkiDroid API — do NOT guess

The `FlashCardsContract` **Javadoc is incomplete and the published `api-v1.1.0`
contract is stale**. The truth is the **provider implementation**,
`AnkiDroid/src/main/java/com/ichi2/anki/provider/CardContentProvider.kt`, plus the
current `api/src/main/java/com/ichi2/anki/FlashCardsContract.kt`, in the AnkiDroid
repo (github.com/ankidroid/Anki-Android). Read those before asserting what an action
can do — the contract has changed a lot (e.g. current `main` has a top-level `cards`
/ `cards/#` URI, real `Card._ID`, and scheduler columns that older versions lack).

Three surfaces that diverge — keep them distinct:
1. **Compile-time** = the API jar in `app/build.gradle` (`com.github.ankidroid:Anki-Android:<tag>`).
   Currently `2.17alpha14` (≈2023, **being bumped — Phase 0.4**). Determines which
   symbols Java can reference.
2. **Runtime** = the AnkiDroid version **installed on the device**. The provider runs
   in *its* process, so actual behaviour depends on it. (Get it:
   `adb shell dumpsys package com.ichi2.anki | grep versionName`.)
3. **Upstream `main` / latest release** (v2.24/v2.25) — where the API is heading.

A local sparse vendor of the AnkiDroid sources (pinned to the dependency tag) is the
intended greppable reference; until it exists, fetch the two files above from the
AnkiDroid repo at the right ref.

## Build & dev workflow

- **No local Android SDK** (disk is the scarce resource). Do **not** install it. Java
  is written by static analysis against the existing idioms + the AnkiDroid sources;
  **CI compiles it.** Verify changes via GitHub Actions, not a local build.
- CI (`.github/workflows/build.yml`) builds a **signed release APK** on every push
  (keystore from repo secrets). Watch with `gh run watch`.
- **`gh` defaults to the upstream parent `KamWithK/AnkiconnectAndroid`** in this fork,
  so `gh run list`/`workflow list` come back empty. Always pass
  `--repo Self-Perfection/AnkiconnectAndroid` (or `gh repo set-default` it).
- **Never commit the keystore** (`*.jks`/`*.keystore` are gitignored; it lives outside
  the repo).

## Branches

Fork of `KamWithK/AnkiconnectAndroid` (upstream). **`master` = clean mirror of upstream**
— never commit there; catch it up with `git fetch upstream && git merge --ff-only
upstream/master && git push origin master` (git is configured: master pulls from
upstream, pushes to origin). **Our work lives on the long-lived `integration` branch**;
feature branches merge into it, not into master.

## Tests

`tests-android/` — black-box pytest against a **real device** over HTTP (see
`tests-android/CLAUDE.md` and `README.md`). Gold standard = **desktop AnkiConnect**
run in podman as an oracle (`localhost:8765`); every test must also pass there. Two
marker axes: depth (`smoke`/`core`/`edge`) and verification mode (`gui`/`manual`,
opt-in via `--run-gui`/`--run-manual`).

## Conventions

- Project language and commit messages are **English**.
- New files get an SPDX header: `// SPDX-License-Identifier: GPL-3.0-or-later`
  (the project is GPL-3.0-or-later; stay GPL, keep `LICENSE`/copyrights).
- End commit messages with: `Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>`.
