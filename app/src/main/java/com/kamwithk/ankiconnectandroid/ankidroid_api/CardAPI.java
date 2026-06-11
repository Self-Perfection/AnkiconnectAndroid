// SPDX-License-Identifier: GPL-3.0-or-later
package com.kamwithk.ankiconnectandroid.ankidroid_api;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;

import com.ichi2.anki.FlashCardsContract;

import java.util.ArrayList;
import java.util.List;

/**
 * Card-related queries against the AnkiDroid FlashCardsContract ContentProvider.
 *
 * <p>AnkiDroid's contract (api-v1.1.0 / 2.17alpha14) does NOT expose a card id column: a card is
 * identified by the pair (NOTE_ID, CARD_ORD), accessible via the URI {@code notes/<noteId>/cards}.
 * Desktop AnkiConnect however addresses cards by a single integer card id. To bridge the two we
 * synthesise a stable card id by packing the note id and ordinal together:
 * {@code cardId = (noteId << ORD_BITS) | ord}. This is fully reversible
 * (see {@link #cardIdToNoteId(long)} / {@link #cardIdToOrd(long)}) and lets the same value flow
 * from findCards into cardsInfo. The synthetic id will NOT match the real Anki card id that the
 * desktop add-on would return.</p>
 */
public class CardAPI {
    /** Number of low bits reserved for the card ordinal. 7 bits => up to 128 cards per note. */
    private static final int ORD_BITS = 7;
    private static final long ORD_MASK = (1L << ORD_BITS) - 1;

    private final Context context;
    private final ContentResolver resolver;
    private final NoteAPI noteAPI;

    private static final String[] CARD_ORD_PROJECTION = {FlashCardsContract.Card.CARD_ORD};
    private static final String[] CARD_INFO_PROJECTION = {
            FlashCardsContract.Card.NOTE_ID,
            FlashCardsContract.Card.CARD_ORD,
            FlashCardsContract.Card.CARD_NAME,
            FlashCardsContract.Card.DECK_ID,
            FlashCardsContract.Card.QUESTION,
            FlashCardsContract.Card.ANSWER
    };

    public CardAPI(Context context, NoteAPI noteAPI) {
        this.context = context;
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

    private Uri cardsUriForNote(long noteId) {
        Uri noteUri = Uri.withAppendedPath(FlashCardsContract.Note.CONTENT_URI, Long.toString(noteId));
        return Uri.withAppendedPath(noteUri, "cards");
    }

    /**
     * Expands a single note into the synthetic card ids of all its cards.
     */
    public List<Long> cardIdsForNote(long noteId) {
        List<Long> cardIds = new ArrayList<>();
        Cursor cursor = resolver.query(cardsUriForNote(noteId), CARD_ORD_PROJECTION, null, null, null);
        if (cursor != null) {
            try (cursor) {
                while (cursor.moveToNext()) {
                    int ordIdx = cursor.getColumnIndexOrThrow(FlashCardsContract.Card.CARD_ORD);
                    int ord = cursor.getInt(ordIdx);
                    cardIds.add(makeCardId(noteId, ord));
                }
            }
        }
        return cardIds;
    }

    /**
     * findCards: AnkiDroid has no card search, so resolve the note query and expand each note to
     * its cards.
     */
    public List<Long> findCards(String query) {
        List<Long> cardIds = new ArrayList<>();
        for (long noteId : noteAPI.findNotes(query)) {
            cardIds.addAll(cardIdsForNote(noteId));
        }
        return cardIds;
    }

    /**
     * Holds the subset of card fields the AnkiDroid contract can supply. Scheduler fields
     * (due, interval, factor, queue, type, reps, lapses, ...) are intentionally absent: they are
     * not part of FlashCardsContract.Card on the supported AnkiDroid version, so they are not
     * fabricated here.
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

        public CardInfo(long cardId, int ord, String cardName, String question, String answer,
                        long note, long deckId) {
            this.cardId = cardId;
            this.ord = ord;
            this.fieldOrder = ord;
            this.cardName = cardName;
            this.question = question;
            this.answer = answer;
            this.note = note;
            this.deckId = deckId;
        }
    }

    /**
     * Returns the card identified by the synthetic card id, or {@code null} if it doesn't exist.
     */
    public CardInfo cardInfo(long cardId) {
        long noteId = cardIdToNoteId(cardId);
        int wantedOrd = cardIdToOrd(cardId);

        Cursor cursor = resolver.query(cardsUriForNote(noteId), CARD_INFO_PROJECTION, null, null, null);
        if (cursor == null) {
            return null;
        }
        try (cursor) {
            while (cursor.moveToNext()) {
                int ordIdx = cursor.getColumnIndexOrThrow(FlashCardsContract.Card.CARD_ORD);
                int ord = cursor.getInt(ordIdx);
                if (ord != wantedOrd) {
                    continue;
                }
                int noteIdIdx = cursor.getColumnIndexOrThrow(FlashCardsContract.Card.NOTE_ID);
                int nameIdx = cursor.getColumnIndexOrThrow(FlashCardsContract.Card.CARD_NAME);
                int deckIdIdx = cursor.getColumnIndexOrThrow(FlashCardsContract.Card.DECK_ID);
                int questionIdx = cursor.getColumnIndexOrThrow(FlashCardsContract.Card.QUESTION);
                int answerIdx = cursor.getColumnIndexOrThrow(FlashCardsContract.Card.ANSWER);

                return new CardInfo(
                        cardId,
                        ord,
                        cursor.getString(nameIdx),
                        cursor.getString(questionIdx),
                        cursor.getString(answerIdx),
                        cursor.getLong(noteIdIdx),
                        cursor.getLong(deckIdIdx)
                );
            }
        }
        return null;
    }

    /**
     * cardsInfo: returns one entry per requested card id. Missing cards yield {@code null} so the
     * router can emit an empty object {@code {}} (matching desktop AnkiConnect's behaviour for an
     * unknown card id).
     */
    public List<CardInfo> cardsInfo(List<Long> cardIds) {
        List<CardInfo> result = new ArrayList<>(cardIds.size());
        for (long cardId : cardIds) {
            result.add(cardInfo(cardId));
        }
        return result;
    }
}
