package com.kamwithk.ankiconnectandroid.ankidroid_api;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.widget.Toast;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.io.IOException;
import java.util.*;

import static com.ichi2.anki.api.AddContentApi.READ_WRITE_PERMISSION;

import com.kamwithk.ankiconnectandroid.request_parsers.MediaRequest;
import com.ichi2.anki.FlashCardsContract;
import com.ichi2.anki.api.AddContentApi;
import com.kamwithk.ankiconnectandroid.request_parsers.NoteRequest;

public class IntegratedAPI {
    private Context context;
    public final DeckAPI deckAPI;
    public final ModelAPI modelAPI;
    public final NoteAPI noteAPI;
    public final MediaAPI mediaAPI;
    private final AddContentApi api; // TODO: Combine all API classes???

    //From anki-connect repo
    private static final String CAN_ADD_ERROR_REASON = "cannot create note because it is a duplicate";
    public IntegratedAPI(Context context) {
        this.context = context;

        deckAPI = new DeckAPI(context);
        modelAPI = new ModelAPI(context);
        noteAPI = new NoteAPI(context);
        mediaAPI = new MediaAPI(context);

        api = new AddContentApi(context);
    }

    public static void authenticate(Context context) {
        int permission = ContextCompat.checkSelfPermission(context, READ_WRITE_PERMISSION);

        if (permission != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions((Activity)context, new String[]{READ_WRITE_PERMISSION}, 0);
        }
    }

    //public File getExternalFilesDir() {
    //    return context.getExternalFilesDir(null);
    //}

    public void addSampleCard() {
        Map<String, String> data = new HashMap<>();
        data.put("Back", "sunrise");
        data.put("Front", "日の出");

        try {
            addNote(data, "Temporary", "Basic", null);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public ArrayList<Boolean> canAddNotes(ArrayList<NoteRequest> notesToTest) throws Exception {
        final String[] NOTE_PROJECTION = {FlashCardsContract.Note._ID, FlashCardsContract.Note.CSUM};

        if(notesToTest.isEmpty()) {
            return new ArrayList<>();
        }

        ArrayList<Long> checksums = new ArrayList<>(notesToTest.size());
        ArrayList<Boolean> canAddNote = new ArrayList<>(notesToTest.size());
        NoteRequest.NoteOptions noteOptions = notesToTest.get(0).getOptions();

        // If duplicate scope is "deck" or "deck root", we need to get extra information to figure out if DID matches.
        // If duplicate scope is "deck root" we need to include children, noteOptions.getDeckName() will not be null
        HashSet<Long> deckIds = new HashSet<>();
        Map<String, Long> deckNamesToIds = deckAPI.deckNamesAndIds();
        String deckName = noteOptions.getDeckName();
        if(deckName == null) {
            // Deck, not root
            deckName = notesToTest.get(0).getDeckName();
            deckIds.add(deckNamesToIds.get(deckName));
        }
        else {
            for (String name : deckNamesToIds.keySet()) {
                if (name.contains(deckName)) {
                    deckIds.add(deckNamesToIds.get(name));
                }
            }
        }

        for (NoteRequest note : notesToTest) {
            String key = note.getFieldValue();
            checksums.add(Utility.getFieldChecksum(key));
        }

        // If duplicates are allowed, just need to see if they are valid notes (checksum != 0)
        if (noteOptions.isAllowDuplicate()) {
            for (long checksum: checksums) {
                canAddNote.add(checksum != 0);
            }
            return canAddNote;
        }

        // Grabbing the note options and model for the first note and assuming the rest are the same.
        // This is true for yomitan but might not be for other applications.
        String modelName = notesToTest.get(0).getModelName();

        Map<String, Long> modelNameToId = modelAPI.modelNamesAndIds(0);
        Long modelId = modelNameToId.get(modelName);

        String selectionQuery = "";
        if (!noteOptions.isCheckAllModels()) {
            selectionQuery = String.format(
                    Locale.US,
                    "%s=%d and ",
                    FlashCardsContract.Note.MID,
                    modelId
            );
        }
        selectionQuery = selectionQuery + String.format(
                Locale.US,
                "%s in (%s)",
                FlashCardsContract.Note.CSUM,
                TextUtils.join(",", checksums)
        );

        final Cursor cursor = context.getContentResolver().query(
                FlashCardsContract.Note.CONTENT_URI_V2,
                NOTE_PROJECTION,
                selectionQuery,
                null,
                null
        );

        if (cursor == null || cursor.getCount() == 0) {
            for (int i = 0; i < notesToTest.size(); i++) {
                canAddNote.add(true);
            }
        }
        else {
            LinkedHashSet<Long> queryChecksums = findChecksumsInQuery(
                    cursor,
                    noteOptions.getDuplicateScope().equals("deck"), deckIds);

            for (int i = 0; i < checksums.size(); i++) {
                boolean isChecksumFound = !queryChecksums.contains(checksums.get(i));
                canAddNote.add(isChecksumFound);
            }
        }

        return canAddNote;
    }

    private LinkedHashSet<Long> findChecksumsInQuery(Cursor cursor, boolean isDuplicateScopeDeck, Set<Long> deckIds) {
        LinkedHashSet<Long> queryChecksums = new LinkedHashSet<>();

        try (cursor) {
            while (cursor.moveToNext()) {
                // Build list of CSUM (queryChecksums)
                // If an entry in queryChecksums is in checksums, then we have a duplicate
                // If scope is "deck", these duplicates need to be checked again for the deck
                int idIdx = cursor.getColumnIndexOrThrow(FlashCardsContract.Note._ID);
                int csumIdx = cursor.getColumnIndexOrThrow(FlashCardsContract.Note.CSUM);

                long queryNid = cursor.getLong(idIdx);
                long queryCsum = cursor.getLong(csumIdx);

                // If duplicate scope is "deck", need an additional query
                if (!isDuplicateScopeDeck || isNoteInDeck(queryNid, deckIds)) {
                    queryChecksums.add(queryCsum);
                }
            }
        }

        return queryChecksums;
    }

    private boolean isNoteInDeck(long noteId, Set<Long> deckIds) {
        // Need to search for all cards with the same note ID, and see if they exist in one of the decks.
        final String[] CARD_PROJECTION = {FlashCardsContract.Card.DECK_ID};

        Uri noteUri = Uri.withAppendedPath(FlashCardsContract.Note.CONTENT_URI, Long.toString(noteId));
        Uri cardUri = Uri.withAppendedPath(noteUri, "cards");
        Cursor cardCursor = context.getContentResolver().query(
                cardUri,
                CARD_PROJECTION,
                null,
                null,
                null
        );

        if(cardCursor != null) {
            try (cardCursor) {
                while(cardCursor.moveToNext()) {
                    int didIdx = cardCursor.getColumnIndexOrThrow(FlashCardsContract.Card.DECK_ID);
                    long did = cardCursor.getLong(didIdx);

                    if (deckIds.contains(did)) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    /**
     * Note IDs whose first-field checksum matches {@code checksum} within the
     * same model. Uses the same CSUM query as canAddNotes (proven to work on
     * device), but returns note IDs so the caller can filter by deck.
     */
    private Set<Long> noteIdsWithChecksum(long modelId, long checksum) {
        Set<Long> ids = new HashSet<>();
        String selection = String.format(
                Locale.US,
                "%s=%d and %s=%d",
                FlashCardsContract.Note.MID, modelId,
                FlashCardsContract.Note.CSUM, checksum
        );
        Cursor cursor = context.getContentResolver().query(
                FlashCardsContract.Note.CONTENT_URI_V2,
                new String[]{FlashCardsContract.Note._ID},
                selection,
                null,
                null
        );
        if (cursor == null) {
            return ids;
        }
        try (cursor) {
            int idIdx = cursor.getColumnIndexOrThrow(FlashCardsContract.Note._ID);
            while (cursor.moveToNext()) {
                ids.add(cursor.getLong(idIdx));
            }
        }
        return ids;
    }

    /**
     * Validate a note the way AnkiConnect does before inserting, throwing on
     * failure:
     * <ul>
     *   <li>"cannot create note because it is empty" when the first (sort)
     *       field is empty, regardless of allowDuplicate;</li>
     *   <li>"cannot create note because it is a duplicate" when a note with the
     *       same first field and model already exists in scope, unless
     *       allowDuplicate is set.</li>
     * </ul>
     *
     * The duplicate key is the model's <em>first field</em> ({@code flds[0]},
     * which is what AnkiDroid checksums), looked up from {@code fields} by the
     * model's field order — not by JSON key order, which clients like
     * anki.koplugin do not send in any particular order.
     */
    public void validateCanAdd(Map<String, String> fields, String modelName,
                               String deckName, NoteRequest.NoteOptions options) throws Exception {
        Long modelId = modelAPI.modelNamesAndIds(0).get(modelName);
        if (modelId == null) {
            // Unknown model: let the add proceed and fail later if it must.
            return;
        }
        String[] fieldNames = api.getFieldList(modelId);
        if (fieldNames == null || fieldNames.length == 0) {
            return;
        }

        String firstField = fields.getOrDefault(fieldNames[0], "");
        if (Utility.isFieldEmpty(firstField)) {
            throw new Exception("cannot create note because it is empty");
        }
        if (options.isAllowDuplicate()) {
            return;
        }

        Set<Long> duplicateIds =
                noteIdsWithChecksum(modelId, Utility.getFieldChecksum(firstField));
        if (duplicateIds.isEmpty()) {
            return;
        }

        if (!"deck".equals(options.getDuplicateScope())) {
            // Collection-wide scope: any same-model match is a duplicate.
            throw new Exception("cannot create note because it is a duplicate");
        }

        // Deck scope: a match counts only if it is in the target deck. Use a
        // findNotes("deck:...") query rather than per-note card lookups.
        String deckQuery = "deck:" + NoteAPI.escapeQueryStr(deckName);
        for (Long idInDeck : noteAPI.findNotes(deckQuery)) {
            if (duplicateIds.contains(idInDeck)) {
                throw new Exception("cannot create note because it is a duplicate");
            }
        }
    }

    public static class CanAddWithError {
        private final boolean canAdd;
        private final String error;

        public CanAddWithError(boolean canAdd, String error) {
            this.canAdd = canAdd;
            this.error = error;
        }

        public boolean isCanAdd() {
            return canAdd;
        }

        public String getError() {
            return error;
        }
    }

    public List<CanAddWithError> canAddNotesWithErrorDetail(ArrayList<NoteRequest> notesToTest) throws Exception {
        List<CanAddWithError> canAddWithErrorList = new ArrayList<>();
        List<Boolean> canAddList = canAddNotes(notesToTest);

        for (boolean canAdd : canAddList) {
            CanAddWithError canAddWithError;
            if (canAdd) {
                canAddWithError = new CanAddWithError(true, null);
            }
            else {
                canAddWithError = new CanAddWithError(false, CAN_ADD_ERROR_REASON);
            }
            canAddWithErrorList.add(canAddWithError);
        }

        return canAddWithErrorList;
    }

    /**
     * Add flashcards to AnkiDroid through instant add API
     * @param data Map of (field name, field value) pairs
     * @return The id of the note added
     */
    public Long addNote(final Map<String, String> data, String deck_name, String model_name, Set<String> tags) throws Exception {
        Long deck_id = deckAPI.getDeckID(deck_name);
        Long model_id = modelAPI.getModelID(model_name, data.size());
        Long note_id = noteAPI.addNote(data, deck_id, model_id, tags);

        if (note_id != null) {
            // Show the first field's text so the user can tell what was added.
            String firstField = firstFieldText(model_id, data);
            final String message = firstField.isEmpty() ? "Note added" : "Note added: " + firstField;
            new Handler(Looper.getMainLooper()).post(() -> Toast.makeText(context, message, Toast.LENGTH_SHORT).show());
            return note_id;
        } else {
            new Handler(Looper.getMainLooper()).post(() -> Toast.makeText(context, "Failed to add note", Toast.LENGTH_SHORT).show());
            throw new Exception("Couldn't add note");
        }
    }

    /**
     * Plain text of the model's first field for the given data, for display.
     * Empty string if the model or its fields can't be resolved.
     */
    private String firstFieldText(Long modelId, Map<String, String> data) {
        if (modelId == null) {
            return "";
        }
        String[] fieldNames = api.getFieldList(modelId);
        if (fieldNames == null || fieldNames.length == 0) {
            return "";
        }
        return Utility.stripToText(data.getOrDefault(fieldNames[0], ""));
    }

    /**
     * Adds the media to the collection, and updates noteValues
     *
     * @param noteValues Map from field name to field value
     * @param mediaRequests
     * @throws Exception
     */
    public void addMedia(Map<String, String> noteValues, List<MediaRequest> mediaRequests) throws Exception {
        for (MediaRequest media : mediaRequests) {
            // mediaAPI.storeMediaFile() doesn't store as the passed in filename, need to use the returned one
            Optional<byte[]> data = media.getData();
            Optional<String> url = media.getUrl();
            String stored_filename;
            if (data.isPresent()) {
                stored_filename = mediaAPI.storeMediaFile(media.getFilename(), data.get());
            } else if (url.isPresent()) {
                stored_filename = mediaAPI.downloadAndStoreBinaryFile(media.getFilename(), url.get());
            } else {
                throw new Exception("You must provide a \"data\" or \"url\" field. Note that \"path\" is currently not supported on AnkiConnectAndroid.");
            }

            String enclosed_filename = "";
            switch (media.getMediaType()) {
                case AUDIO:
                case VIDEO:
                    enclosed_filename = "[sound:" + stored_filename + "]";
                    break;
                case PICTURE:
                    enclosed_filename = "<img src=\"" + stored_filename + "\">";
                    break;
            }

            for (String field : media.getFields()) {
                String existingValue = noteValues.get(field);

                if (existingValue == null) {
                    noteValues.put(field, enclosed_filename);
                } else {
                    noteValues.put(field, existingValue + enclosed_filename);
                }
            }
        }
    }

    public void updateNoteFields(long note_id, Map<String, String> newFields, ArrayList<MediaRequest> mediaRequests) throws Exception {
        /*
         * updateNoteFields request looks like:
         * id: int,
         * fields: {
         *     field_name: string
         * },
         * audio | video | picture: [
         *     {
         *         data: base64 string,
         *         filename: string,
         *         fields: string[]
         *         + more fields that are currently unsupported
         *      }
         * ]
         *
         * Fields is an incomplete list of fields, and the Anki API expects the the passed in field
         * list to be complete. So, need to get the existing fields and only update them if present
         * in the request. Also need to reverse map each media file back to the field it will be
         * included in and append it enclosed in either <img> or [sound: ]
         */

        String[] modelFieldNames = modelAPI.modelFieldNames(noteAPI.getNoteModelId(note_id));
        String[] originalFields = noteAPI.getNoteFields(note_id);

        // updated fields
        HashMap<String, String> cardFields = new HashMap<>();

        // Get old fields and update values as needed
        for (int i = 0; i < modelFieldNames.length; i++) {
            String fieldName = modelFieldNames[i];

            String newValue = newFields.get(modelFieldNames[i]);
            if (newValue != null) {
                // Update field to new value
                cardFields.put(fieldName, newValue);
                // Ankidroids `getFields` won't return empty fields that are at the end of the array
                // so `originalFields` may potentially contain less fields than `modelFieldNames`
            } else if (originalFields.length >= i + 1) {
                cardFields.put(fieldName, originalFields[i]);
            } else {
                cardFields.put(fieldName, "");
            }
        }

        addMedia(cardFields, mediaRequests);
        noteAPI.updateNoteFields(note_id, cardFields);
    }

    public String storeMediaFile(BinaryFile binaryFile) throws IOException {
        return mediaAPI.storeMediaFile(binaryFile.getFilename(), binaryFile.getData());
    }

    /**
     * Delete the given notes (and their cards).
     *
     * The AnkiDroid ContentProvider supports note deletion only on the
     * single-note URI ({@code notes/<id>}); the bulk URIs throw
     * {@link UnsupportedOperationException}. Therefore we loop over the ids and
     * delete them one at a time.
     *
     * Note: actual runtime support depends on the AnkiDroid version installed on
     * the device. Per the provider source (CardContentProvider) it is supported.
     */
    public void deleteNotes(List<Long> noteIds) {
        for (Long noteId : noteIds) {
            Uri noteUri = Uri.withAppendedPath(
                    FlashCardsContract.Note.CONTENT_URI, String.valueOf(noteId));
            context.getContentResolver().delete(noteUri, null, null);
        }
    }

    public ArrayList<Long> guiBrowse(String query) {
        // https://github.com/ankidroid/Anki-Android/pull/11899
        Uri webpage = Uri.parse("anki://x-callback-url/browser?search=" + query);
        Intent webIntent = new Intent(Intent.ACTION_VIEW, webpage);
        webIntent.setPackage("com.ichi2.anki");
        // FLAG_ACTIVITY_NEW_TASK is needed in order to display the intent from a different app
        // FLAG_ACTIVITY_CLEAR_TOP and Intent.FLAG_ACTIVITY_TASK_ON_HOME is needed in order to not
        // cause a long chain of activities within Ankidroid
        // (i.e. browser <- word <- browser <- word <- browser <- word)
        // FLAG_ACTIVITY_CLEAR_TOP also allows the browser window to refresh with the new word
        // if AnkiDroid was already on the card browser activity.
        // see: https://stackoverflow.com/a/23874622
        webIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_TASK_ON_HOME);
        context.startActivity(webIntent);

        // The result doesn't seem to be used by Yomichan at all, so it can be safely ignored.
        // If we want to get the results, calling the findNotes() method will likely cause
        // unwanted delay.
        return new ArrayList<>();
    }
}

