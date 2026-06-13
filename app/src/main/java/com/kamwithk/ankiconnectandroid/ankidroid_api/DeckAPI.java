package com.kamwithk.ankiconnectandroid.ankidroid_api;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;

import com.ichi2.anki.FlashCardsContract;

import java.util.HashMap;
import java.util.Map;

public class DeckAPI {
    private final ContentResolver resolver;

    // Only the columns we actually need; a narrow projection lets the provider skip
    // the expensive per-deck counts/options it would otherwise compute.
    private static final String[] DECK_PROJECTION = {
            FlashCardsContract.Deck.DECK_NAME,
            FlashCardsContract.Deck.DECK_ID
    };

    public DeckAPI(Context context) {
        resolver = context.getContentResolver();
    }

    /**
     * All decks as (id -> name). Queries the FlashCardsContract ContentProvider:
     * AddContentApi.getDeckList() was removed in AnkiDroid v2.24.0, and the provider
     * (Deck.CONTENT_ALL_URI) is the supported way to enumerate decks.
     */
    private Map<Long, String> getDeckList() throws Exception {
        Cursor cursor = resolver.query(FlashCardsContract.Deck.CONTENT_ALL_URI, DECK_PROJECTION, null, null, null);
        if (cursor == null) {
            throw new Exception("Couldn't get deck list");
        }
        Map<Long, String> decks = new HashMap<>();
        try (cursor) {
            int nameIdx = cursor.getColumnIndexOrThrow(FlashCardsContract.Deck.DECK_NAME);
            int idIdx = cursor.getColumnIndexOrThrow(FlashCardsContract.Deck.DECK_ID);
            while (cursor.moveToNext()) {
                decks.put(cursor.getLong(idIdx), cursor.getString(nameIdx));
            }
        }
        return decks;
    }

    public String[] deckNames() throws Exception {
        return getDeckList().values().toArray(new String[0]);
    }

    public Map<String, Long> deckNamesAndIds() throws Exception {
        Map<Long, String> temporary = getDeckList();
        Map<String, Long> decks = new HashMap<>();

        // Reverse hashmap to get entries of (Name, ID)
        for (Map.Entry<Long, String> entry : temporary.entrySet()) {
            decks.put(entry.getValue(), entry.getKey());
        }

        return decks;
    }

    public Long getDeckID(String name) throws Exception {
        for (Map.Entry<String, Long> entry : deckNamesAndIds().entrySet()) {
            if (entry.getKey().equalsIgnoreCase(name)) {
                return entry.getValue();
            }
        }

        // Can't find deck
        throw new Exception("Couldn't get deck ID");
    }
}
