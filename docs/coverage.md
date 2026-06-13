<!-- SPDX-License-Identifier: GPL-3.0-or-later -->
# AnkiConnect API coverage in AnkiconnectAndroid

Which of desktop [AnkiConnect](https://git.sr.ht/~foosoft/anki-connect#supported-actions)'s
**122 actions** this port implements, and how faithfully.

**Source of truth for "implemented":** the dispatcher
`app/src/main/java/com/kamwithk/ankiconnectandroid/routing/AnkiAPIRouting.java`
(the `switch` on the action name). Per-field behaviour for `notesInfo`/`cardsInfo`
is pinned by `tests-android/test_schema_parity.py` against the desktop oracle.
Keep this table in sync when you add or change an action.

**Status: 26 of 122 actions return a usable result** (25 full ✅ + 1 degraded ⚠️).
3 more return an explicit *not supported* error 🚫 instead of silent garbage.

| Mark | Meaning |
|:----:|---------|
| ✅ | Implemented; behaves like desktop AnkiConnect (any caveat noted). |
| ⚠️ | Implemented but partial or degraded — see the note. |
| 🚫 | Cannot be done via the AnkiDroid API; returns a clean *not supported* error (no Java stack trace). |
| ❌ | Not implemented yet. |

> **Runtime matters.** The AnkiDroid `FlashCardsContract` provider runs in the
> *installed AnkiDroid's* process, so some behaviour depends on the device's
> AnkiDroid version, not on this app's build. The compile-time dependency is
> AnkiDroid **v2.24.0**. Notably, the real-card-id path (see `cardsInfo`/`findCards`)
> needs the device to expose the top-level `cards` URI (AnkiDroid ≥ v2.24.0); on
> older AnkiDroid the port feature-detects this and falls back to a degraded mode.

---

## Cards

| Action | Desktop | Android | Note |
|--------|:-------:|:-------:|------|
| `answerCards` | ✅ | ❌ | Feasible via `ReviewInfo` (EASE→answerCard). |
| `areDue` | ✅ | ❌ | Now feasible: scheduler columns are readable via the `cards` URI. |
| `areSuspended` | ✅ | ❌ | Now feasible: card `queue` column. |
| `cardReviews` | ✅ | ❌ | |
| `cardsInfo` | ✅ | ✅ | Real cid + scheduler fields (`type/queue/due/interval/factor/reps/lapses/left`) and `css` via the `cards` URI. Desktop extras with no provider source — `mod`, `flags`, `nextReviews` — are omitted. On AnkiDroid < v2.24.0: synthetic cid, no scheduler fields. |
| `cardsModTime` | ✅ | ❌ | No card mod-time column in the contract. |
| `cardsToNotes` | ✅ | ❌ | |
| `changeDeck` | ✅ | ✅ | Moves cards via `notes/<nid>/cards/<ord>` (`DECK_ID`); get-or-creates the target deck. Not onto filtered/dynamic decks. Used by anki_notes_creator. |
| `findCards` | ✅ | ✅ | Real cids via `col.findCards(<query>)` on the `cards` URI. On AnkiDroid < v2.24.0: falls back to `findNotes` → expand each note to synthetic cids `(noteId<<7)|ord`. |
| `forgetCards` | ✅ | ❌ | |
| `getEaseFactors` | ✅ | ❌ | Now feasible: `factor` column. |
| `getIntervals` | ✅ | ❌ | Now feasible: `interval` column. |
| `getReviewsOfCards` | ✅ | ❌ | |
| `relearnCards` | ✅ | ❌ | |
| `setDueDate` | ✅ | ❌ | |
| `setEaseFactors` | ✅ | ❌ | |
| `setSpecificValueOfCard` | ✅ | ❌ | |
| `suspend` | ✅ | ❌ | Feasible via `ReviewInfo` (SUSPEND→buryOrSuspendCard). |
| `suspended` | ✅ | ❌ | Now feasible: card `queue` column. |
| `unsuspend` | ✅ | ❌ | Feasible via `ReviewInfo`. |

## Notes

| Action | Desktop | Android | Note |
|--------|:-------:|:-------:|------|
| `addNote` | ✅ | ✅ | Duplicate check honours `duplicateScope: "deck"` (koplugin default). |
| `addNotes` | ✅ | ❌ | |
| `addTags` | ✅ | ✅ | |
| `canAddNote` | ✅ | ❌ | Desktop has no such singular action; see `canAddNotes`. |
| `canAddNoteWithErrorDetail` | ✅ | ❌ | |
| `canAddNotes` | ✅ | ✅ | |
| `canAddNotesWithErrorDetail` | ✅ | ✅ | |
| `deleteNotes` | ✅ | ✅ | Bulk delete unsupported by provider → done one note at a time. |
| `findNotes` | ✅ | ✅ | |
| `getNoteTags` | ✅ | ❌ | |
| `notesInfo` | ✅ | ✅ | Returns `noteId, modelName, tags, fields, cards, mod`. `cards` are real cids when the `cards` URI is available (else synthetic). `profile` omitted (AnkiDroid is single-collection). |
| `notesModTime` | ✅ | ❌ | Feasible: `Note.MOD` column. |
| `removeEmptyNotes` | ✅ | ❌ | |
| `removeTags` | ✅ | ❌ | |
| `replaceTags` | ✅ | ❌ | |
| `replaceTagsInAllNotes` | ✅ | ❌ | |
| `updateNote` | ✅ | ✅ | |
| `updateNoteFields` | ✅ | ✅ | |
| `updateNoteModel` | ✅ | ❌ | |
| `updateNoteTags` | ✅ | ❌ | |

## Decks

| Action | Desktop | Android | Note |
|--------|:-------:|:-------:|------|
| `cloneDeckConfigId` | ✅ | ❌ | |
| `createDeck` | ✅ | ✅ | Get-or-create via `Deck.CONTENT_ALL_URI` insert; returns the deck id. Anki auto-creates parent decks for `A::B` names. |
| `deckNameFromId` | ✅ | ❌ | |
| `deckNames` | ✅ | ✅ | Via `Deck.CONTENT_ALL_URI`. |
| `deckNamesAndIds` | ✅ | ✅ | |
| `deleteDecks` | ✅ | ❌ | |
| `getDeckConfig` | ✅ | ❌ | |
| `getDeckStats` | ✅ | ❌ | |
| `getDecks` | ✅ | ❌ | |
| `removeDeckConfigId` | ✅ | ❌ | |
| `saveDeckConfig` | ✅ | ❌ | |
| `setDeckConfigId` | ✅ | ❌ | |

## Models

| Action | Desktop | Android | Note |
|--------|:-------:|:-------:|------|
| `createModel` | ✅ | ❌ | Possible via `AddContentApi`, not yet wired. |
| `findAndReplaceInModels` | ✅ | ❌ | |
| `findModelsById` | ✅ | ❌ | |
| `findModelsByName` | ✅ | ❌ | |
| `modelFieldAdd` | ✅ | ❌ | |
| `modelFieldDescriptions` | ✅ | ❌ | |
| `modelFieldFonts` | ✅ | ❌ | |
| `modelFieldNames` | ✅ | ✅ | |
| `modelFieldRemove` | ✅ | ❌ | |
| `modelFieldRename` | ✅ | ❌ | |
| `modelFieldReposition` | ✅ | ❌ | |
| `modelFieldSetDescription` | ✅ | ❌ | |
| `modelFieldSetFont` | ✅ | ❌ | |
| `modelFieldSetFontSize` | ✅ | ❌ | |
| `modelFieldsOnTemplates` | ✅ | ❌ | |
| `modelNameFromId` | ✅ | ❌ | |
| `modelNames` | ✅ | ✅ | |
| `modelNamesAndIds` | ✅ | ✅ | |
| `modelStyling` | ✅ | ✅ | `Model.CSS` (read-only on AnkiDroid). |
| `modelTemplateAdd` | ✅ | ❌ | |
| `modelTemplateRemove` | ✅ | ❌ | |
| `modelTemplateRename` | ✅ | ❌ | |
| `modelTemplateReposition` | ✅ | ❌ | |
| `modelTemplates` | ✅ | ❌ | |
| `updateModelStyling` | ✅ | ❌ | Model is read-only via the provider. |
| `updateModelTemplates` | ✅ | ❌ | |

## Media

| Action | Desktop | Android | Note |
|--------|:-------:|:-------:|------|
| `deleteMediaFile` | ✅ | 🚫 | AnkiMedia provider is insert-only; `delete()` doesn't handle the media URI (confirmed in v2.24.0 / main). |
| `getMediaDirPath` | ✅ | ❌ | |
| `getMediaFilesNames` | ✅ | ❌ | |
| `retrieveMediaFile` | ✅ | ❌ | No read-back API. |
| `storeMediaFile` | ✅ | ✅ | Provider uniquifies the name; **clients must use the returned filename**, not the one they sent. |

## Tags

| Action | Desktop | Android | Note |
|--------|:-------:|:-------:|------|
| `clearUnusedTags` | ✅ | ❌ | |
| `getTags` | ✅ | ❌ | |

## GUI

| Action | Desktop | Android | Note |
|--------|:-------:|:-------:|------|
| `guiAddCards` | ✅ | ❌ | |
| `guiAddNoteSetData` | ✅ | 🚫 | Desktop semantics (pre-fill the add dialog) with no AnkiDroid analogue. |
| `guiAnswerCard` | ✅ | ❌ | |
| `guiBrowse` | ✅ | ✅ | Fires the AnkiDroid browser intent for the query; returns `[]` (no selection read-back). |
| `guiCheckDatabase` | ✅ | ❌ | |
| `guiCurrentCard` | ✅ | ❌ | |
| `guiDeckBrowser` | ✅ | ❌ | |
| `guiDeckOverview` | ✅ | ❌ | |
| `guiDeckReview` | ✅ | ❌ | |
| `guiEditNote` | ✅ | ✅ | No deep link to the editor; opens the browser on `nid:<id>` (like `guiBrowse`). Returns null. |
| `guiExitAnki` | ✅ | ❌ | |
| `guiImportFile` | ✅ | ❌ | |
| `guiPlayAudio` | ✅ | ❌ | |
| `guiReviewActive` | ✅ | ❌ | |
| `guiSelectCard` | ✅ | 🚫 | No browser-selection concept on Android. |
| `guiSelectedNotes` | ✅ | ⚠️ | No browser selection on Android → always `[]` (as desktop does with no browser open). |
| `guiSelectNote` | ✅ | ❌ | |
| `guiShowAnswer` | ✅ | ❌ | |
| `guiShowQuestion` | ✅ | ❌ | |
| `guiStartCardTimer` | ✅ | ❌ | |
| `guiUndo` | ✅ | ❌ | |

## Stats & Reviews

| Action | Desktop | Android | Note |
|--------|:-------:|:-------:|------|
| `getCollectionStatsHTML` | ✅ | ❌ | |
| `getLatestReviewID` | ✅ | ❌ | |
| `getNumCardsReviewedByDay` | ✅ | ❌ | |
| `getNumCardsReviewedToday` | ✅ | ❌ | |
| `insertReviews` | ✅ | ❌ | |

## Profiles & Collection

| Action | Desktop | Android | Note |
|--------|:-------:|:-------:|------|
| `exportPackage` | ✅ | ❌ | |
| `getActiveProfile` | ✅ | ❌ | AnkiDroid is single-profile. |
| `getProfiles` | ✅ | ❌ | |
| `importPackage` | ✅ | ❌ | |
| `loadProfile` | ✅ | ❌ | |
| `reloadCollection` | ✅ | ❌ | |
| `sync` | ✅ | ❌ | |

## Misc

| Action | Desktop | Android | Note |
|--------|:-------:|:-------:|------|
| `apiReflect` | ✅ | ❌ | |
| `multi` | ✅ | ✅ | Per-action error isolation like desktop: a failing sub-action becomes a `{result:null,error}` slot, the batch continues. |
| `requestPermission` | ✅ | ✅ | Returns granted so clients (koplugin) go online. |
| `version` | ✅ | ✅ | Reports 6. |

### Non-standard (AnkiconnectAndroid only)

| Action | Note |
|--------|------|
| `buildInfo` | Reports `{gitSha, versionName, versionCode}` so an installed APK can be matched to its source. Desktop AnkiConnect has no such action; clients must not depend on it. |
