// SPDX-License-Identifier: GPL-3.0-or-later
package com.kamwithk.ankiconnectandroid.ankidroid_api;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.text.TextUtils;

import com.ichi2.anki.FlashCardsContract;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Card-related queries against the AnkiDroid FlashCardsContract ContentProvider.
 *
 * <p>Two code paths, chosen at runtime by {@link #supportsCardsUri()}:</p>
 *
 * <ul>
 *   <li><b>Real-cid path</b> (AnkiDroid ≥ v2.24.0, the compile-time dependency): the provider
 *   exposes the top-level {@code cards} URI ({@link FlashCardsContract.Card#CONTENT_URI}) backed by
 *   {@code col.findCards(<browser query>)} and a real {@code Card._ID} (the true Anki card id), plus
 *   scheduler columns (type/queue/due/interval/factor/reps/lapses/left). findCards/cardsInfo report
 *   the real cid and the scheduler fields, exactly like desktop AnkiConnect.</li>
 *
 *   <li><b>Synthetic fallback</b> (older AnkiDroid, no {@code cards} URI): a card is only reachable
 *   via {@code notes/<noteId>/cards} as the pair (NOTE_ID, CARD_ORD). We synthesise a stable,
 *   reversible card id by packing them together: {@code cardId = (noteId << ORD_BITS) | ord}
 *   (see {@link #cardIdToNoteId(long)} / {@link #cardIdToOrd(long)}). This id will NOT match the
 *   real Anki card id, and scheduler fields are absent (the old contract doesn't expose them).</li>
 * </ul>
 *
 * <p>The path is decided once and cached, so a single device reports a consistent id scheme across
 * findCards, cardsInfo and notesInfo's {@code cards}.</p>
 */
public class CardAPI {
    /** Number of low bits reserved for the card ordinal. 7 bits => up to 128 cards per note. */
    private static final int ORD_BITS = 7;
    private static final long ORD_MASK = (1L << ORD_BITS) - 1;

    private final ContentResolver resolver;
    private final NoteAPI noteAPI;

    /** Cached result of the {@code cards}-URI capability probe; null until first probed. */
    private Boolean cardsUriSupported = null;

    private static final String[] CARD_ORD_PROJECTION = {FlashCardsContract.Card.CARD_ORD};

    // Synthetic-path projection: only the columns the old notes/<id>/cards URI exposes.
    private static final String[] SYNTHETIC_CARD_INFO_PROJECTION = {
            FlashCardsContract.Card.NOTE_ID,
            FlashCardsContract.Card.CARD_ORD,
            FlashCardsContract.Card.CARD_NAME,
            FlashCardsContract.Card.DECK_ID,
            FlashCardsContract.Card.QUESTION,
            FlashCardsContract.Card.ANSWER
    };

    // Real-path projection: card identity + scheduler columns. Every column here is supported by
    // the v2.24.0 provider's addCardToCursor; we deliberately omit FSRS/custom columns so the query
    // doesn't fail on slightly-older runtimes that have the cards URI but not those columns.
    private static final String[] REAL_CARD_INFO_PROJECTION = {
            FlashCardsContract.Card._ID,
            FlashCardsContract.Card.NOTE_ID,
            FlashCardsContract.Card.CARD_ORD,
            FlashCardsContract.Card.CARD_NAME,
            FlashCardsContract.Card.DECK_ID,
            FlashCardsContract.Card.QUESTION,
            FlashCardsContract.Card.ANSWER,
            FlashCardsContract.Card.TYPE,
            FlashCardsContract.Card.RAW_QUEUE,
            FlashCardsContract.Card.RAW_DUE,
            FlashCardsContract.Card.INTERVAL,
            FlashCardsContract.Card.RAW_SM2_FACTOR,
            FlashCardsContract.Card.REPS,
            FlashCardsContract.Card.LAPSES,
            FlashCardsContract.Card.RAW_LEFT
    };

    public CardAPI(Context context, NoteAPI noteAPI) {
        this.resolver = context.getContentResolver();
        this.noteAPI = noteAPI;
    }

    public static long makeCardId(long noteId, int ord) {
        return (noteId << ORD_BITS) | (ord & ORD_MASK);
    }

    public static long cardIdToNoteId(long cardId) {
        return cardId >> ORD_BITS;
    }

    public static int cardIdToOrd(long cardId) {
        return (int) (cardId & ORD_MASK);
    }

    /**
     * Whether the installed AnkiDroid exposes the top-level {@code cards} URI (real cids). Probed
     * once with a syntactically-valid query that matches nothing: old AnkiDroid lacks the URI and
     * the provider throws {@code "uri ... is not supported"}; a supported provider returns an empty
     * cursor. Cached for the life of this instance.
     */
    private boolean supportsCardsUri() {
        if (cardsUriSupported != null) {
            return cardsUriSupported;
        }
        try (Cursor cursor = resolver.query(
                FlashCardsContract.Card.CONTENT_URI,
                new String[]{FlashCardsContract.Card._ID},
                "tag:__acandroid_cards_uri_probe__",
                null,
                null)) {
            // Reaching here (even with a null/empty cursor) means the URI is recognised.
            cardsUriSupported = true;
        } catch (Exception e) {
            cardsUriSupported = false;
        }
        return cardsUriSupported;
    }

    private Uri cardsUriForNote(long noteId) {
        Uri noteUri = Uri.withAppendedPath(FlashCardsContract.Note.CONTENT_URI, Long.toString(noteId));
        return Uri.withAppendedPath(noteUri, "cards");
    }

    /**
     * Moves each card to {@code deckId}. The provider only accepts a deck change on the
     * {@code notes/<noteId>/cards/<ord>} URI (there is no update path on the {@code cards/#} URI), so
     * we resolve each card id to its (noteId, ord): from the real card row when the cards URI is
     * available, or by unpacking the synthetic id otherwise. Unknown card ids are skipped.
     * Filtered (dynamic) decks are rejected by AnkiDroid.
     */
    public void changeDeck(List<Long> cardIds, long deckId) {
        for (long cardId : cardIds) {
            long noteId;
            int ord;
            if (supportsCardsUri()) {
                CardInfo info = cardInfo(cardId);
                if (info == null) {
                    continue;
                }
                noteId = info.note;
                ord = info.ord;
            } else {
                noteId = cardIdToNoteId(cardId);
                ord = cardIdToOrd(cardId);
            }
            Uri cardOrdUri = Uri.withAppendedPath(cardsUriForNote(noteId), Integer.toString(ord));
            ContentValues values = new ContentValues();
            values.put(FlashCardsContract.Card.DECK_ID, deckId);
            resolver.update(cardOrdUri, values, null, null);
        }
    }

    /**
     * Card ids of every card belonging to a note (real cids when supported, synthetic otherwise).
     */
    public List<Long> cardIdsForNote(long noteId) {
        if (supportsCardsUri()) {
            return findCards("nid:" + noteId);
        }
        return CursorUtil.queryList(resolver, cardsUriForNote(noteId), CARD_ORD_PROJECTION, null,
                c -> makeCardId(noteId, c.getInt(c.getColumnIndexOrThrow(FlashCardsContract.Card.CARD_ORD))));
    }

    /**
     * findCards: real path runs the browser query directly against the {@code cards} URI and returns
     * real cids. Fallback resolves the note query and expands each note to its (synthetic) card ids.
     */
    public List<Long> findCards(String query) {
        if (supportsCardsUri()) {
            return CursorUtil.queryList(resolver, FlashCardsContract.Card.CONTENT_URI,
                    new String[]{FlashCardsContract.Card._ID}, query,
                    c -> c.getLong(c.getColumnIndexOrThrow(FlashCardsContract.Card._ID)));
        }

        List<Long> cardIds = new ArrayList<>();
        for (long noteId : noteAPI.findNotes(query)) {
            cardIds.addAll(cardIdsForNote(noteId));
        }
        return cardIds;
    }

    /**
     * Subset of card fields we expose. The scheduler fields (type/queue/due/interval/factor/reps/
     * lapses/left) are boxed and {@code null} on the synthetic fallback path, where the old contract
     * cannot supply them; the router omits null fields from the JSON rather than fabricating zeros.
     */
    public static class CardInfo {
        public final long cardId;
        public final int ord;
        public final int fieldOrder;
        public final String cardName;
        public final String question;
        public final String answer;
        public final long note;
        public final long deckId;

        public final Long type;
        public final Long queue;
        public final Long due;
        public final Long interval;
        public final Long factor;
        public final Long reps;
        public final Long lapses;
        public final Long left;

        public CardInfo(long cardId, int ord, String cardName, String question, String answer,
                        long note, long deckId,
                        Long type, Long queue, Long due, Long interval, Long factor,
                        Long reps, Long lapses, Long left) {
            this.cardId = cardId;
            this.ord = ord;
            this.fieldOrder = ord;
            this.cardName = cardName;
            this.question = question;
            this.answer = answer;
            this.note = note;
            this.deckId = deckId;
            this.type = type;
            this.queue = queue;
            this.due = due;
            this.interval = interval;
            this.factor = factor;
            this.reps = reps;
            this.lapses = lapses;
            this.left = left;
        }
    }

    /** Reads the current row of a real-path cursor (REAL_CARD_INFO_PROJECTION) into a CardInfo. */
    private CardInfo realCardInfoFromCursor(Cursor cursor) {
        long cardId = cursor.getLong(cursor.getColumnIndexOrThrow(FlashCardsContract.Card._ID));
        long note = cursor.getLong(cursor.getColumnIndexOrThrow(FlashCardsContract.Card.NOTE_ID));
        int ord = cursor.getInt(cursor.getColumnIndexOrThrow(FlashCardsContract.Card.CARD_ORD));
        String cardName = cursor.getString(cursor.getColumnIndexOrThrow(FlashCardsContract.Card.CARD_NAME));
        long deckId = cursor.getLong(cursor.getColumnIndexOrThrow(FlashCardsContract.Card.DECK_ID));
        String question = cursor.getString(cursor.getColumnIndexOrThrow(FlashCardsContract.Card.QUESTION));
        String answer = cursor.getString(cursor.getColumnIndexOrThrow(FlashCardsContract.Card.ANSWER));
        long type = cursor.getLong(cursor.getColumnIndexOrThrow(FlashCardsContract.Card.TYPE));
        long queue = cursor.getLong(cursor.getColumnIndexOrThrow(FlashCardsContract.Card.RAW_QUEUE));
        long due = cursor.getLong(cursor.getColumnIndexOrThrow(FlashCardsContract.Card.RAW_DUE));
        long interval = cursor.getLong(cursor.getColumnIndexOrThrow(FlashCardsContract.Card.INTERVAL));
        long factor = cursor.getLong(cursor.getColumnIndexOrThrow(FlashCardsContract.Card.RAW_SM2_FACTOR));
        long reps = cursor.getLong(cursor.getColumnIndexOrThrow(FlashCardsContract.Card.REPS));
        long lapses = cursor.getLong(cursor.getColumnIndexOrThrow(FlashCardsContract.Card.LAPSES));
        long left = cursor.getLong(cursor.getColumnIndexOrThrow(FlashCardsContract.Card.RAW_LEFT));
        return new CardInfo(cardId, ord, cardName, question, answer, note, deckId,
                type, queue, due, interval, factor, reps, lapses, left);
    }

    /**
     * Returns the card identified by the (real or synthetic) card id, or {@code null} if it doesn't
     * exist.
     */
    public CardInfo cardInfo(long cardId) {
        if (supportsCardsUri()) {
            List<CardInfo> found = cardsInfo(java.util.Collections.singletonList(cardId));
            return found.isEmpty() ? null : found.get(0);
        }

        long noteId = cardIdToNoteId(cardId);
        int wantedOrd = cardIdToOrd(cardId);

        List<CardInfo> cards = CursorUtil.queryList(resolver, cardsUriForNote(noteId),
                SYNTHETIC_CARD_INFO_PROJECTION, null,
                c -> new CardInfo(
                        cardId,
                        c.getInt(c.getColumnIndexOrThrow(FlashCardsContract.Card.CARD_ORD)),
                        c.getString(c.getColumnIndexOrThrow(FlashCardsContract.Card.CARD_NAME)),
                        c.getString(c.getColumnIndexOrThrow(FlashCardsContract.Card.QUESTION)),
                        c.getString(c.getColumnIndexOrThrow(FlashCardsContract.Card.ANSWER)),
                        c.getLong(c.getColumnIndexOrThrow(FlashCardsContract.Card.NOTE_ID)),
                        c.getLong(c.getColumnIndexOrThrow(FlashCardsContract.Card.DECK_ID)),
                        null, null, null, null, null, null, null, null));
        for (CardInfo info : cards) {
            if (info.ord == wantedOrd) {
                return info;
            }
        }
        return null;
    }

    /**
     * cardsInfo: one entry per requested card id, preserving request order. Missing cards yield
     * {@code null} so the router can emit {@code {}} (matching desktop AnkiConnect for an unknown
     * card id). Real path batches all ids into a single {@code cid:...} query; fallback resolves
     * each synthetic id individually.
     */
    public List<CardInfo> cardsInfo(List<Long> cardIds) {
        if (!supportsCardsUri()) {
            List<CardInfo> result = new ArrayList<>(cardIds.size());
            for (long cardId : cardIds) {
                result.add(cardInfo(cardId));
            }
            return result;
        }

        if (cardIds.isEmpty()) {
            return new ArrayList<>();
        }

        // One query for all ids; cid: matches existing cards only, so missing ids are simply absent.
        Map<Long, CardInfo> byId = new HashMap<>();
        List<CardInfo> rows = CursorUtil.queryList(resolver, FlashCardsContract.Card.CONTENT_URI,
                REAL_CARD_INFO_PROJECTION, "cid:" + TextUtils.join(",", cardIds),
                this::realCardInfoFromCursor);
        for (CardInfo info : rows) {
            byId.put(info.cardId, info);
        }

        List<CardInfo> result = new ArrayList<>(cardIds.size());
        for (long cardId : cardIds) {
            result.add(byId.get(cardId)); // null when the id matched nothing
        }
        return result;
    }
}
