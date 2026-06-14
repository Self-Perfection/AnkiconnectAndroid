// SPDX-License-Identifier: GPL-3.0-or-later
package com.kamwithk.ankiconnectandroid.ankidroid_api;

import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;

import java.util.ArrayList;
import java.util.List;

/**
 * Collapses the AnkiDroid ContentProvider query boilerplate
 * (query → null-check → {@code try (cursor) { while (moveToNext()) … }} → close) into one place.
 * A {@link RowMapper} reads a single row; the helper owns the cursor's lifecycle.
 *
 * <p>A null cursor (provider failure / unsupported URI) is treated as "no rows": {@link #queryList}
 * returns an empty list and {@link #queryFirst} returns the supplied fallback. Callers that must
 * treat a null cursor as an error (e.g. {@code DeckAPI.getDeckList}, {@code ModelAPI.modelStyling})
 * keep their own query instead.</p>
 */
public final class CursorUtil {
    private CursorUtil() {}

    /** Maps the cursor's current row to a value. Must not advance or close the cursor. */
    public interface RowMapper<T> {
        T map(Cursor cursor);
    }

    /** Maps every row of the query to a list (empty when the cursor is null or has no rows). */
    public static <T> List<T> queryList(ContentResolver resolver, Uri uri, String[] projection,
                                        String selection, RowMapper<T> mapper) {
        List<T> result = new ArrayList<>();
        Cursor cursor = resolver.query(uri, projection, selection, null, null);
        if (cursor == null) {
            return result;
        }
        try (cursor) {
            while (cursor.moveToNext()) {
                result.add(mapper.map(cursor));
            }
        }
        return result;
    }

    /** Maps the first row, or returns {@code orElse} when the cursor is null or empty. */
    public static <T> T queryFirst(ContentResolver resolver, Uri uri, String[] projection,
                                   String selection, RowMapper<T> mapper, T orElse) {
        Cursor cursor = resolver.query(uri, projection, selection, null, null);
        if (cursor == null) {
            return orElse;
        }
        try (cursor) {
            if (cursor.moveToNext()) {
                return mapper.map(cursor);
            }
        }
        return orElse;
    }
}
