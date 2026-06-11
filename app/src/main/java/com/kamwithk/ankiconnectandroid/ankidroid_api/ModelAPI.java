package com.kamwithk.ankiconnectandroid.ankidroid_api;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import com.ichi2.anki.FlashCardsContract;
import com.ichi2.anki.api.AddContentApi;

import java.util.HashMap;
import java.util.Map;

public class ModelAPI {
    private final AddContentApi api;
    private final ContentResolver resolver;

    public ModelAPI(Context context) {
        api = new AddContentApi(context);
        resolver = context.getContentResolver();
    }

    public String[] modelNames() throws Exception {
        Map<Long, String> models = api.getModelList(0);

        if (models != null) {
            return models.values().toArray(new String[0]);
        } else {
            throw new Exception("Couldn't get model names");
        }
    }

    public Map<String, Long> modelNamesAndIds(Integer numFields) throws Exception {
        Map<Long, String> temporary = api.getModelList(numFields);
        Map<String, Long> models = new HashMap<>();

        if (temporary != null) {
            // Reverse hashmap to get entries of (Name, ID)
            for (Map.Entry<Long, String> entry : temporary.entrySet()) {
                models.put(entry.getValue(), entry.getKey());
            }

            return models;
        } else {
            throw new Exception("Couldn't get models names and IDs");
        }
    }

    public String[] modelFieldNames(Long model_id) {
        return api.getFieldList(model_id);
    }

    /**
     * Returns the CSS styling of a model. The Model.CSS column is queryable through the
     * FlashCardsContract ContentProvider (Model is read-only on AnkiDroid).
     */
    public String modelStyling(String modelName) throws Exception {
        Long modelId = getModelID(modelName, 0);

        Uri modelUri = Uri.withAppendedPath(FlashCardsContract.Model.CONTENT_URI, Long.toString(modelId));
        Cursor cursor = resolver.query(
                modelUri,
                new String[]{FlashCardsContract.Model.CSS},
                null,
                null,
                null
        );

        if (cursor == null) {
            throw new Exception("model was not found: " + modelName);
        }
        try (cursor) {
            if (!cursor.moveToFirst()) {
                throw new Exception("model was not found: " + modelName);
            }
            int cssIdx = cursor.getColumnIndexOrThrow(FlashCardsContract.Model.CSS);
            return cursor.getString(cssIdx);
        }
    }

    public Long getModelID(String modelName, Integer numFields) throws Exception {
        Map<String, Long> modelList = modelNamesAndIds(numFields);
        for (Map.Entry<String, Long> entry : modelList.entrySet()) {
            if (entry.getKey().equals(modelName)) {
                return entry.getValue(); // first model wins
            }
        }

        // Can't find model
        throw new Exception("Couldn't get model ID");
    }
}
