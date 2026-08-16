package com.krotname.checker.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.util.Optional;

/**
 * Parses DaData response payload and extracts the first legal entity state value.
 */
public final class DadataResponseParser {
    private static final String FIELD_SUGGESTIONS = "suggestions";
    private static final String FIELD_DATA = "data";
    private static final String FIELD_STATE = "state";
    private static final String FIELD_STATUS = "status";

    /**
     * Safely traverse DaData response structure and extract the company status field.
     * Returns empty optional when the company is not found or the found entry has no status.
     * A payload that is not a DaData suggestions document at all (not JSON, not an object,
     * no {@code suggestions} array) is an integration failure and is reported as
     * {@link IOException} so that it does not look like a "company not found" domain answer.
     */
    public Optional<String> extractState(String json) throws IOException {
        JsonArray suggestions;
        try {
            JsonElement rootElement = JsonParser.parseString(json);
            if (!rootElement.isJsonObject()) {
                throw new IOException("Unexpected DaData response: root is not a JSON object");
            }
            JsonElement suggestionsElement = rootElement.getAsJsonObject().get(FIELD_SUGGESTIONS);
            if (suggestionsElement == null || !suggestionsElement.isJsonArray()) {
                throw new IOException("Unexpected DaData response: 'suggestions' array is missing");
            }
            suggestions = suggestionsElement.getAsJsonArray();
        } catch (JsonParseException e) {
            throw new IOException("Unexpected DaData response: malformed JSON", e);
        }
        if (suggestions.isEmpty()) {
            return Optional.empty();
        }
        try {

            JsonElement firstSuggestion = suggestions.get(0);
            if (!firstSuggestion.isJsonObject()) {
                return Optional.empty();
            }

            JsonElement dataElement = firstSuggestion.getAsJsonObject().get(FIELD_DATA);
            if (dataElement == null || !dataElement.isJsonObject()) {
                return Optional.empty();
            }

            JsonElement stateElement = dataElement.getAsJsonObject().get(FIELD_STATE);
            if (stateElement == null || !stateElement.isJsonObject()) {
                return Optional.empty();
            }

            JsonElement statusElement = stateElement.getAsJsonObject().get(FIELD_STATUS);
            if (statusElement == null || !statusElement.isJsonPrimitive()
                    || !statusElement.getAsJsonPrimitive().isString()) {
                return Optional.empty();
            }

            String status = statusElement.getAsString();
            return status.isBlank() ? Optional.empty() : Optional.of(status);
        } catch (RuntimeException ignored) {
            return Optional.empty();
        }
    }
}
