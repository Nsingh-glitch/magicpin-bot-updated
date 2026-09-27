package com.magicpin.bot.service;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class GeminiMessageEnricherTest {

    @Test
    void disabledReturnsEmptyWithoutCallingGemini() {
        RestClient client = RestClient.builder().build();
        GeminiMessageEnricher enricher = new GeminiMessageEnricher(client, false, "", "model");

        assertTrue(enricher.enrich("Deterministic message.", Map.of()).isEmpty());
    }

    @Test
    void validResponseReturnsOnlyGroundedMessage() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        RestClient client = builder.build();
        GeminiMessageEnricher enricher = new GeminiMessageEnricher(client, true, "test-key", "gemini-test");
        server.expect(requestTo("https://generativelanguage.googleapis.com/v1beta/models/gemini-test:generateContent"))
            .andRespond(withSuccess("{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"Performance is down 18%.\"}]}}]}", MediaType.APPLICATION_JSON));

        assertEquals("Performance is down 18%.", enricher.enrich(
            "Performance is down 18%.",
            Map.of("drop_percent", 18)
        ).orElseThrow());
        server.verify();
    }

    @Test
    void httpFailureReturnsEmpty() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        RestClient client = builder.build();
        GeminiMessageEnricher enricher = new GeminiMessageEnricher(client, true, "test-key", "gemini-test");
        server.expect(requestTo("https://generativelanguage.googleapis.com/v1beta/models/gemini-test:generateContent"))
            .andRespond(withServerError());

        assertTrue(enricher.enrich("Deterministic message.", Map.of()).isEmpty());
        server.verify();
    }

    @Test
    void emptyMalformedOrUnsupportedResponseReturnsEmpty() {
        assertTrue(enrichWithResponse("{\"candidates\":[]}", "Deterministic message.", Map.of()).isEmpty());
        assertTrue(enrichWithResponse("not-json", "Deterministic message.", Map.of()).isEmpty());
        assertTrue(enrichWithResponse("{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"Performance is down 25%.\"}]}}]}",
                "Performance is down 18%.", Map.of("drop_percent", 18)).isEmpty());
        assertTrue(enrichWithResponse("{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"Campaign is now live.\"}]}}]}",
                "A performance signal is available.", Map.of()).isEmpty());
    }

    private java.util.Optional<String> enrichWithResponse(String response, String deterministicMessage, Map<String, Object> facts) {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        RestClient client = builder.build();
        GeminiMessageEnricher enricher = new GeminiMessageEnricher(client, true, "test-key", "gemini-test");
        server.expect(requestTo("https://generativelanguage.googleapis.com/v1beta/models/gemini-test:generateContent"))
            .andRespond(withSuccess(response, MediaType.APPLICATION_JSON));
        java.util.Optional<String> result = enricher.enrich(deterministicMessage, facts);
        server.verify();
        return result;
    }
}
