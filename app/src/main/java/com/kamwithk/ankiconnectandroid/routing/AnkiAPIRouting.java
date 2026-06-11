package com.kamwithk.ankiconnectandroid.routing;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.kamwithk.ankiconnectandroid.BuildConfig;
import com.kamwithk.ankiconnectandroid.ankidroid_api.BinaryFile;
import com.kamwithk.ankiconnectandroid.ankidroid_api.CardAPI;
import com.kamwithk.ankiconnectandroid.ankidroid_api.DeckAPI;
import com.kamwithk.ankiconnectandroid.ankidroid_api.IntegratedAPI;
import com.kamwithk.ankiconnectandroid.ankidroid_api.MediaAPI;
import com.kamwithk.ankiconnectandroid.ankidroid_api.ModelAPI;
import com.kamwithk.ankiconnectandroid.request_parsers.NoteRequest;
import com.kamwithk.ankiconnectandroid.request_parsers.Parser;
import com.kamwithk.ankiconnectandroid.request_parsers.MediaRequest;

import fi.iki.elonen.NanoHTTPD;

import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;

import static fi.iki.elonen.NanoHTTPD.newFixedLengthResponse;

import android.util.Log;


public class AnkiAPIRouting {
    private final IntegratedAPI integratedAPI;
    private final DeckAPI deckAPI;
    private final ModelAPI modelAPI;
    private final MediaAPI mediaAPI;

    public AnkiAPIRouting(IntegratedAPI integratedAPI) {
        this.integratedAPI = integratedAPI;
        deckAPI = integratedAPI.deckAPI;
        modelAPI = integratedAPI.modelAPI;
        mediaAPI = integratedAPI.mediaAPI;
    }

    private String findRoute(JsonObject raw_json) throws Exception {
        switch (Parser.get_action(raw_json)) {
            case "version":
                return version();
            case "buildInfo":
                return buildInfo();
            case "deckNames":
                return deckNames();
            case "deckNamesAndIds":
                return deckNamesAndIds();
            case "modelNames":
                return modelNames();
            case "modelNamesAndIds":
                return modelNamesAndIds();
            case "modelFieldNames":
                return modelFieldNames(raw_json);
            case "findNotes":
                return findNotes(raw_json);
            case "guiBrowse":
                return guiBrowse(raw_json);
            case "canAddNotes":
                return canAddNotes(raw_json);
            case "canAddNotesWithErrorDetail":
                return canAddNotesWithErrorDetail(raw_json);
            case "addNote":
                return addNote(raw_json);
            case "updateNoteFields":
                return updateNoteFields(raw_json);
            case "updateNote":
                return updateNote(raw_json);
            case "addTags":
                return addTags(raw_json);
            case "findCards":
                return findCards(raw_json);
            case "cardsInfo":
                return cardsInfo(raw_json);
            case "modelStyling":
                return modelStyling(raw_json);
            case "guiEditNote":
                return guiEditNote(raw_json);
            case "guiSelectedNotes":
                return guiSelectedNotes(raw_json);
            case "deleteMediaFile":
                throw new Exception("deleteMediaFile is not supported on AnkiDroid: the AnkiDroid media provider only supports adding files, not deleting them.");
            case "guiSelectCard":
                throw new Exception("guiSelectCard is not supported on AnkiDroid.");
            case "guiAddNoteSetData":
                throw new Exception("guiAddNoteSetData is not supported on AnkiDroid.");
            case "storeMediaFile":
                return storeMediaFile(raw_json);
            case "notesInfo":
                return notesInfo(raw_json);
            case "multi":
                JsonArray actions = Parser.getMultiActions(raw_json);
                JsonArray results = new JsonArray();

                for (JsonElement jsonElement : actions) {
                    int version = Parser.get_version(jsonElement.getAsJsonObject(), 4);
                    String routeResult = findRoute(jsonElement.getAsJsonObject());

                    JsonElement routeResultJson = JsonParser.parseString(routeResult);
                    JsonElement response = formatSuccessReply(routeResultJson, version);
                    results.add(response);
                }

                return Parser.gson.toJson(results);
            case "requestPermission":
                return requestPermission();
            case "deleteNotes":
                return deleteNotes(raw_json);
            default:
                // An unknown/unsupported action yields a clean error response instead
                // of the bogus "AnkiConnect v.6" string that fails JSON parsing.
                throw new Exception("unsupported action: " + Parser.get_action(raw_json));
        }
    }
    /* taken from anki-connect's web.py: format_success_reply */
    public JsonElement formatSuccessReply(JsonElement raw_json, int version) {
        if (version <= 4) {
            return raw_json;
        } else {
            JsonObject reply = new JsonObject();
            reply.add("result", raw_json);
            reply.add("error", null);
            return reply;
        }
    }

    public NanoHTTPD.Response findRouteHandleError(JsonObject raw_json) {
        try {
            int version = Parser.get_version(raw_json, 4);
            String response = formatSuccessReply(JsonParser.parseString(findRoute(raw_json)), version).toString();
            Log.d("AnkiConnectAndroid", "response json: " + response);
            return returnResponse(response);
        } catch (Exception e) {
            // Log the full stack trace for diagnostics, but only return a
            // human-readable message to the client. Leaking the stack trace into
            // the "error" field both breaks clients and exposes internals.
            Log.e("AnkiConnectAndroid", "Error handling request", e);

            Map<String, String> response = new HashMap<>();
            response.put("result", null);

            String message = e.getMessage();
            if (message == null || message.isEmpty()) {
                message = e.getClass().getSimpleName();
            }
            response.put("error", message);

            return newFixedLengthResponse(NanoHTTPD.Response.Status.OK, "text/json", Parser.gson.toJson(response));
        }
    }

    private NanoHTTPD.Response returnResponse(String response) {
        return newFixedLengthResponse(NanoHTTPD.Response.Status.OK, "text/json", response);
    }

    private String version() {
        return "6";
    }

    // Non-standard action: reports the git commit and version this APK was built
    // from, so a running build can be matched to its source (see CLAUDE.md "Build
    // identity"). Desktop AnkiConnect has no such action; clients must not rely on it.
    private String buildInfo() {
        JsonObject info = new JsonObject();
        info.addProperty("gitSha", BuildConfig.GIT_SHA);
        info.addProperty("versionName", BuildConfig.VERSION_NAME);
        info.addProperty("versionCode", BuildConfig.VERSION_CODE);
        return Parser.gson.toJson(info);
    }

    private String default_version() {
        return "AnkiConnect v.6";
    }

    private String deckNames() throws Exception {
        return Parser.gson.toJson(deckAPI.deckNames());
    }

    private String deckNamesAndIds() throws Exception {
        return Parser.gson.toJson(deckAPI.deckNamesAndIds());
    }

    private String modelNames() throws Exception {
        return Parser.gson.toJson(modelAPI.modelNames());
    }

    private String modelNamesAndIds() throws Exception {
        return Parser.gson.toJson(modelAPI.modelNamesAndIds(0));
    }

    private String modelFieldNames(JsonObject raw_json) throws Exception {
        String model_name = Parser.getModelNameFromParam(raw_json);
        if (model_name != null && !model_name.equals("")) {
            Long model_id = modelAPI.getModelID(model_name, 0);

            return Parser.gson.toJson(modelAPI.modelFieldNames(model_id));
        } else {
            Map<String, String> response = new HashMap<>();
            response.put("result", null);
            response.put("error", "model was not found: ");

            return Parser.gson.toJson(response);
        }
    }

    private String findNotes(JsonObject raw_json) {
        return Parser.gson.toJson(integratedAPI.noteAPI.findNotes(Parser.getNoteQuery(raw_json)));
    }

    private String guiBrowse(JsonObject raw_json) {
        String query = Parser.getNoteQuery(raw_json);
        return Parser.gson.toJson(integratedAPI.guiBrowse(query));
    }

    private String canAddNotes(JsonObject raw_json) throws Exception {
        ArrayList<NoteRequest> notes_to_test = Parser.getNoteFront(raw_json);
        return Parser.gson.toJson(integratedAPI.canAddNotes(notes_to_test));
    }

    private String canAddNotesWithErrorDetail(JsonObject raw_json) throws Exception {
        ArrayList<NoteRequest> notes_to_test = Parser.getNoteFront(raw_json);
        return Parser.gsonNoSerialize.toJson(integratedAPI.canAddNotesWithErrorDetail(notes_to_test));
    }

    /**
     * Add a new note to Anki.
     * The note can include media files, which will be downloaded.
     * AnkiConnect desktop also supports other formats, but this method only supports downloadable media files.
     */
    private String addNote(JsonObject raw_json) throws Exception {
        Map<String, String> noteValues = Parser.getNoteValues(raw_json);

        // Validate like AnkiConnect before inserting. validateCanAdd rejects an
        // empty first field (even with allowDuplicate) and, unless
        // allowDuplicate is set, a duplicate within the duplicateScope ("deck"
        // is koplugin's default). The duplicate key uses the model's field
        // order, not JSON key order.
        NoteRequest noteRequest = Parser.getSingleNoteRequest(raw_json);
        integratedAPI.validateCanAdd(
                noteValues,
                Parser.getModelName(raw_json),
                Parser.getDeckName(raw_json),
                noteRequest.getOptions()
        );

        ArrayList<MediaRequest> mediaRequests =
                Parser.getNoteMediaRequests(raw_json);
        integratedAPI.addMedia(noteValues, mediaRequests);

        String noteId = String.valueOf(integratedAPI.addNote(
                noteValues,
                Parser.getDeckName(raw_json),
                Parser.getModelName(raw_json),
                Parser.getNoteTags(raw_json)
        ));

        return noteId;
    }

    private String updateNoteFields(JsonObject raw_json) throws Exception {
        integratedAPI.updateNoteFields(
                Parser.getUpdateNoteFieldsId(raw_json),
                Parser.getUpdateNoteFieldsFields(raw_json),
                Parser.getNoteMediaRequests(raw_json)
        );
        return "null";
    }

    private String updateNote(JsonObject raw_json) throws Exception {
        integratedAPI.updateNote(
                Parser.getNoteId(raw_json),
                Parser.getUpdateNoteFieldsOptional(raw_json),
                Parser.getNoteMediaRequests(raw_json),
                Parser.getUpdateNoteTagsOptional(raw_json)
        );
        return "null";
    }

    private String addTags(JsonObject raw_json) {
        integratedAPI.addTags(
                Parser.getNoteIds(raw_json),
                Parser.getTags(raw_json)
        );
        return "null";
    }

    private String findCards(JsonObject raw_json) {
        return Parser.gson.toJson(integratedAPI.cardAPI.findCards(Parser.getNoteQuery(raw_json)));
    }

    private String cardsInfo(JsonObject raw_json) throws Exception {
        ArrayList<Long> cardIds = Parser.getCardIds(raw_json);

        // Build a deck id -> name lookup so each card can report its deckName.
        Map<Long, String> deckIdToName = new HashMap<>();
        for (Map.Entry<String, Long> entry : deckAPI.deckNamesAndIds().entrySet()) {
            deckIdToName.put(entry.getValue(), entry.getKey());
        }

        List<CardAPI.CardInfo> cards = integratedAPI.cardAPI.cardsInfo(cardIds);

        // modelName and fields are note-derived (not on the card row), but desktop
        // AnkiConnect includes them in cardsInfo. Fetch them per note (deduped) via
        // notesInfo and reuse its exact JSON shape ({modelName, fields:{name:{value,
        // order}}}); serialising the whole list avoids naming the package-private
        // NoteInfo type from this package.
        ArrayList<Long> noteIds = new ArrayList<>();
        for (CardAPI.CardInfo info : cards) {
            if (info != null && !noteIds.contains(info.note)) {
                noteIds.add(info.note);
            }
        }
        Map<Long, JsonObject> noteJsonById = new HashMap<>();
        // Skip when there are no known cards: notesInfo([]) builds an empty "nid:"
        // query and returns null, which would blow up toJsonTree(...).getAsJsonArray().
        if (!noteIds.isEmpty()) {
            for (JsonElement el : Parser.gson.toJsonTree(integratedAPI.noteAPI.notesInfo(noteIds)).getAsJsonArray()) {
                JsonObject noteObj = el.getAsJsonObject();
                noteJsonById.put(noteObj.get("noteId").getAsLong(), noteObj);
            }
        }

        JsonArray result = new JsonArray();
        for (CardAPI.CardInfo info : cards) {
            if (info == null) {
                // Unknown card id: emit an empty object, matching anki-connect's NotFound handling.
                result.add(new JsonObject());
                continue;
            }
            JsonObject card = new JsonObject();
            card.addProperty("cardId", info.cardId);
            card.addProperty("fieldOrder", info.fieldOrder);
            card.addProperty("ord", info.ord);
            card.addProperty("cardName", info.cardName);
            card.addProperty("question", info.question);
            card.addProperty("answer", info.answer);
            card.addProperty("note", info.note);
            card.addProperty("deckName", deckIdToName.get(info.deckId));
            // modelName + fields come from the card's note (desktop includes both).
            JsonObject noteObj = noteJsonById.get(info.note);
            if (noteObj != null) {
                card.addProperty("modelName", noteObj.get("modelName").getAsString());
                card.add("fields", noteObj.get("fields"));
            }
            // Scheduler fields (due, interval, factor, queue, type, reps, lapses, mod, ...) are not
            // exposed by FlashCardsContract.Card on the supported AnkiDroid version, so they are
            // intentionally omitted rather than fabricated.
            result.add(card);
        }
        return Parser.gson.toJson(result);
    }

    private String modelStyling(JsonObject raw_json) throws Exception {
        String modelName = Parser.getModelNameFromParam(raw_json);
        JsonObject styling = new JsonObject();
        styling.addProperty("css", modelAPI.modelStyling(modelName));
        return Parser.gson.toJson(styling);
    }

    private String guiEditNote(JsonObject raw_json) {
        integratedAPI.guiEditNote(Parser.getGuiEditNoteId(raw_json));
        return "null";
    }

    private String guiSelectedNotes(JsonObject raw_json) {
        // No Android analogue for the desktop browser selection; degrade to an empty list.
        return Parser.gson.toJson(new ArrayList<Long>());
    }

    private String storeMediaFile(JsonObject raw_json) throws Exception {
        BinaryFile binaryFile = new BinaryFile();
        binaryFile.setFilename(Parser.getMediaFilename(raw_json));
        binaryFile.setData(Parser.getMediaData(raw_json));

        return Parser.gson.toJson(integratedAPI.storeMediaFile(binaryFile));
    }

    private String notesInfo(JsonObject raw_json) throws Exception {
        ArrayList<Long> noteIds = Parser.getNoteIds(raw_json);
        return Parser.gson.toJson(integratedAPI.noteAPI.notesInfo(noteIds));
    }

    /**
     * Mirrors desktop AnkiConnect's requestPermission. Returns a granted
     * permission so that clients (e.g. the KOReader anki.koplugin) connect
     * online instead of falling back to offline mode. No AnkiDroid call needed.
     */
    private String requestPermission() {
        JsonObject result = new JsonObject();
        result.addProperty("permission", "granted");
        result.addProperty("requireApiKey", false);
        result.addProperty("version", 6);
        return Parser.gson.toJson(result);
    }

    private String deleteNotes(JsonObject raw_json) throws Exception {
        List<Long> noteIds = Parser.getNoteIds(raw_json);
        integratedAPI.deleteNotes(noteIds);
        // Desktop AnkiConnect's deleteNotes returns null.
        return "null";
    }
}