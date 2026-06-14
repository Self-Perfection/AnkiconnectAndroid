package com.kamwithk.ankiconnectandroid.request_parsers;

import android.util.Base64;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class Parser {
    public static Gson gson = new GsonBuilder().setPrettyPrinting().serializeNulls().create();
    public static Gson gsonNoSerialize = new GsonBuilder().setPrettyPrinting().create();

    /**
     * Thrown when a request omits a required parameter or supplies it with the wrong JSON shape.
     * Unchecked so the existing parser/handler signatures don't all need {@code throws}; the
     * router's catch-all ({@code AnkiAPIRouting.findRouteHandleError}) turns it into a clean
     * {@code {result:null, error:<message>}} reply instead of leaking a Java NPE message like
     * "...getAsString() on a null object reference". Desktop AnkiConnect reports the same class of
     * mistake clearly (e.g. "guiBrowse() got an unexpected keyword argument 'note'").
     */
    public static class MissingParamException extends RuntimeException {
        public MissingParamException(String message) {
            super(message);
        }
    }

    /**
     * The "params" object of a request. Like desktop AnkiConnect (which does
     * {@code request.get('params', {})}), a missing "params" is treated as empty rather than an
     * error — actions whose parameters are all optional then still work. A present-but-non-object
     * "params" is a clear error.
     */
    private static JsonObject params(JsonObject raw_data) {
        JsonElement params = raw_data.get("params");
        if (params == null || params.isJsonNull()) {
            return new JsonObject();
        }
        if (!params.isJsonObject()) {
            throw new MissingParamException("'params' must be an object");
        }
        return params.getAsJsonObject();
    }

    /** A required member, named in the error if absent/null. */
    private static JsonElement required(JsonObject obj, String name) {
        JsonElement value = obj.get(name);
        if (value == null || value.isJsonNull()) {
            throw new MissingParamException("missing required parameter: '" + name + "'");
        }
        return value;
    }

    /** A required member that must be a JSON object. */
    private static JsonObject requiredObject(JsonObject obj, String name) {
        JsonElement value = required(obj, name);
        if (!value.isJsonObject()) {
            throw new MissingParamException("parameter '" + name + "' must be an object");
        }
        return value.getAsJsonObject();
    }

    /** A required member that must be a JSON array. */
    private static JsonArray requiredArray(JsonObject obj, String name) {
        JsonElement value = required(obj, name);
        if (!value.isJsonArray()) {
            throw new MissingParamException("parameter '" + name + "' must be an array");
        }
        return value.getAsJsonArray();
    }

    public static JsonObject parse(String raw_data) {
        return JsonParser.parseString(raw_data).getAsJsonObject();
    }

    public static String get_action(JsonObject data) {
        // Mirror desktop's request.get('action', ''): a missing action degrades to "" (→ the
        // router's "unsupported action" path) rather than an NPE.
        JsonElement action = data.get("action");
        return action == null || action.isJsonNull() ? "" : action.getAsString();
    }

    public static int get_version(JsonObject data, int fallback) {
        if (data.has("version")) {
            return data.get("version").getAsInt();
        }
        return fallback;
    }

    public static String getDeckName(JsonObject raw_data) {
        JsonObject note = requiredObject(params(raw_data), "note");
        return required(note, "deckName").getAsString();
    }

    public static String getModelName(JsonObject raw_data) {
        JsonObject note = requiredObject(params(raw_data), "note");
        return required(note, "modelName").getAsString();
    }

    public static String getModelNameFromParam(JsonObject raw_data) {
        return required(params(raw_data), "modelName").getAsString();
    }

    // params.deck — used by changeDeck ({cards, deck}) and createDeck ({deck}).
    // changeDeck's cards reuse getCardIds.
    public static String getDeckParam(JsonObject raw_data) {
        return required(params(raw_data), "deck").getAsString();
    }

    public static Map<String, String> getNoteValues(JsonObject raw_data) {
        Type fieldType = new TypeToken<Map<String, String>>() {}.getType();
        JsonObject note = requiredObject(params(raw_data), "note");
        return gson.fromJson(required(note, "fields"), fieldType);
    }

    public static Set<String> getNoteTags(JsonObject raw_data) {
        Type fieldType = new TypeToken<Set<String>>() {}.getType();
        JsonObject note = requiredObject(params(raw_data), "note");
        // tags are optional (desktop defaults to []); fromJson(null) -> null, preserved.
        return gson.fromJson(note.get("tags"), fieldType);
    }

    public static String getNoteQuery(JsonObject raw_data) {
        return required(params(raw_data), "query").getAsString();
    }

    public static long getUpdateNoteFieldsId(JsonObject raw_data) {
        JsonObject note = requiredObject(params(raw_data), "note");
        return required(note, "id").getAsLong();
    }

    public static Map<String, String> getUpdateNoteFieldsFields(JsonObject raw_data) {
        Type fieldType = new TypeToken<Map<String, String>>() {}.getType();
        JsonObject note = requiredObject(params(raw_data), "note");
        return gson.fromJson(required(note, "fields"), fieldType);
    }

    /**
     * For each key ("audio", "video", "picture"), expect EITHER a list or singular json object!
     * According to the official Anki-Connect docs:
     * > If you choose to include [audio, video, picture keys], they should contain a single object
     * > or an array of objects
     */
    public static ArrayList<MediaRequest> getNoteMediaRequests(JsonObject raw_data) {
        Map<String, MediaRequest.MediaType> media_types = Map.of(
            "audio", MediaRequest.MediaType.AUDIO,
            "video", MediaRequest.MediaType.VIDEO,
            "picture", MediaRequest.MediaType.PICTURE
        );
        JsonObject note_json = requiredObject(params(raw_data), "note");

        ArrayList<MediaRequest> request_medias = new ArrayList<>();
        for (Map.Entry<String, MediaRequest.MediaType> entry: media_types.entrySet()) {
            JsonElement media_value = note_json.get(entry.getKey());
            if (media_value == null) {
                continue;
            }
            if (media_value.isJsonArray()) {
                for (JsonElement media_element: media_value.getAsJsonArray()) {
                    JsonObject media_object = media_element.getAsJsonObject();
                    MediaRequest requestMedia = MediaRequest.fromJson(media_object, entry.getValue());
                    request_medias.add(requestMedia);
                }
            } else if (media_value.isJsonObject()) {
                JsonObject media_object = media_value.getAsJsonObject();
                MediaRequest requestMedia = MediaRequest.fromJson(media_object, entry.getValue());
                request_medias.add(requestMedia);
            }
        }
        return request_medias;
    }

    /**
     * Parses the single "note" object of an addNote request into a NoteRequest,
     * so the duplicate/options handling used by canAddNotes can be reused.
     */
    public static NoteRequest getSingleNoteRequest(JsonObject raw_data) {
        JsonElement note = required(params(raw_data), "note");
        return NoteRequest.fromJson(note);
    }

    /**
     * Gets the first field of the note
     */
    public static ArrayList<NoteRequest> getNoteFront(JsonObject raw_data) {
        JsonArray notes = requiredArray(params(raw_data), "notes");
        ArrayList<NoteRequest> projections = new ArrayList<>();

        for (JsonElement jsonElement : notes) {
            projections.add(NoteRequest.fromJson(jsonElement));
        }

        return projections;
    }

    public static boolean[] getNoteTrues(JsonObject raw_data) {
        int num_notes = requiredArray(params(raw_data), "notes").size();
        boolean[] array = new boolean[num_notes];
        Arrays.fill(array, true);

        return array;
    }

    public static ArrayList<Long> getNoteIds(JsonObject raw_data) {
        ArrayList<Long> noteIds = new ArrayList<>();
        JsonArray jsonNoteIds = requiredArray(params(raw_data), "notes");
        for(JsonElement noteId: jsonNoteIds) {
            noteIds.add(noteId.getAsLong());
        }
        return noteIds;
    }

    /**
     * updateNote takes note: {id, fields?, tags?}. Fields/tags are optional; returns null for an
     * absent property so the caller can enforce "at least one of fields/tags".
     */
    public static Map<String, String> getUpdateNoteFieldsOptional(JsonObject raw_data) {
        JsonObject note = requiredObject(params(raw_data), "note");
        if (!note.has("fields") || note.get("fields").isJsonNull()) {
            return null;
        }
        Type fieldType = new TypeToken<Map<String, String>>() {}.getType();
        return gson.fromJson(note.get("fields"), fieldType);
    }

    public static Set<String> getUpdateNoteTagsOptional(JsonObject raw_data) {
        JsonObject note = requiredObject(params(raw_data), "note");
        if (!note.has("tags") || note.get("tags").isJsonNull()) {
            return null;
        }
        Type fieldType = new TypeToken<Set<String>>() {}.getType();
        return gson.fromJson(note.get("tags"), fieldType);
    }

    public static long getNoteId(JsonObject raw_data) {
        JsonObject note = requiredObject(params(raw_data), "note");
        return required(note, "id").getAsLong();
    }

    /**
     * addTags takes notes: [ids] (parsed by {@link #getNoteIds}) and tags: "space separated".
     */
    public static String getTags(JsonObject raw_data) {
        return required(params(raw_data), "tags").getAsString();
    }

    /**
     * cardsInfo / findCards-related: cards: [ids].
     */
    public static ArrayList<Long> getCardIds(JsonObject raw_data) {
        ArrayList<Long> cardIds = new ArrayList<>();
        JsonArray jsonCardIds = requiredArray(params(raw_data), "cards");
        for (JsonElement cardId : jsonCardIds) {
            cardIds.add(cardId.getAsLong());
        }
        return cardIds;
    }

    public static long getGuiEditNoteId(JsonObject raw_data) {
        return required(params(raw_data), "note").getAsLong();
    }

    public static String getMediaFilename(JsonObject raw_data) {
        return required(params(raw_data), "filename").getAsString();
    }

    public static byte[] getMediaData(JsonObject raw_data) {
        String encoded = required(params(raw_data), "data").getAsString();
        return Base64.decode(encoded, Base64.DEFAULT);
    }

    public static JsonArray getMultiActions(JsonObject raw_data) {
        return requiredArray(params(raw_data), "actions");
    }
}
