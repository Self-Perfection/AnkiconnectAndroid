package com.kamwithk.ankiconnectandroid.routing;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.kamwithk.ankiconnectandroid.ankidroid_api.BinaryFile;
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
        // Reject duplicates (and empty notes) unless the request opts in via
        // options.allowDuplicate, matching AnkiConnect. canAddNotes already
        // honours allowDuplicate and the duplicate scope.
        NoteRequest noteRequest = Parser.getSingleNoteRequest(raw_json);
        ArrayList<NoteRequest> singleNote = new ArrayList<>();
        singleNote.add(noteRequest);
        ArrayList<Boolean> canAdd = integratedAPI.canAddNotes(singleNote);
        if (canAdd.isEmpty() || !canAdd.get(0)) {
            throw new Exception("cannot create note because it is a duplicate");
        }

        Map<String, String> noteValues = Parser.getNoteValues(raw_json);

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