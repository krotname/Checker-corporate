package com.krotname.checker.client;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("unit")
@Tag("contract")
class DadataResponseParserTest {
    private final DadataResponseParser parser = new DadataResponseParser();

    @Test
    void shouldExtractActiveStateFromResponse() throws IOException {
        String json = """
                {"suggestions":[{"data":{"state":{"status":"ACTIVE"}}}]}
                """;
        Optional<String> state = parser.extractState(json);
        assertTrue(state.isPresent());
        assertEquals("ACTIVE", state.get());
    }

    @Test
    void shouldReturnEmptyOnMissingSuggestion() throws IOException {
        String json = """
                {"suggestions":[]}
                """;
        assertTrue(parser.extractState(json).isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"suggestions\":[\"not-an-object\"]}",
            "{\"suggestions\":[{\"data\":\"not-an-object\"}]}",
            "{\"suggestions\":[{\"data\":{\"state\":\"not-an-object\"}}]}",
            "{\"suggestions\":[{\"data\":{\"state\":{\"status\":{}}}}]}",
            "{\"suggestions\":[{\"data\":{\"state\":{\"status\":123}}}]}",
            "{\"suggestions\":[{\"data\":{\"state\":{\"status\":\"  \"}}}]}"
    })
    void shouldReturnEmptyWhenFoundEntryHasNoUsableStatus(String json) throws IOException {
        assertTrue(parser.extractState(json).isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "not-json",
            "",
            "[]",
            "\"string\"",
            "{}",
            "{\"suggestions\":\"not-an-array\"}",
            "{\"suggestions\":null}"
    })
    void shouldRejectPayloadThatIsNotDadataResponse(String json) {
        IOException error = assertThrows(IOException.class, () -> parser.extractState(json));
        assertTrue(error.getMessage().startsWith("Unexpected DaData response"), error.getMessage());
    }
}
