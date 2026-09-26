package com.hanamizuki.backend.integration.ai;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import tools.jackson.databind.ObjectMapper;

/**
 * One structured call to OpenRouter.
 *
 * <p>OpenRouter speaks the OpenAI chat shape, not Anthropic's, so the SDK
 * cannot simply be pointed at it — this is the adapter. It exists because
 * there was no {@code ANTHROPIC_API_KEY} and the AI paths had never once run.
 *
 * <p>Worth knowing about the model list: {@code models} plus
 * {@code route: "fallback"} makes OpenRouter try the next entry when one is
 * unavailable. The default list crosses vendors on purpose. If Anthropic is
 * having an afternoon during the demo, the album still gets written by
 * somebody, and the rule-based fallback stays what it always was — the last
 * resort rather than the first thing anyone sees.
 */
@Component
public class OpenRouterClient {

    private static final Logger log = LoggerFactory.getLogger(OpenRouterClient.class);

    private final String apiKey;
    private final List<String> models;
    private final RestClient http;
    private final ObjectMapper json = new ObjectMapper();

    public OpenRouterClient(@Value("${app.ai.openrouter.api-key:}") String apiKey,
                            @Value("${app.ai.openrouter.base-url}") String baseUrl,
                            @Value("${app.ai.openrouter.models}") List<String> models,
                            @Value("${app.ai.timeout-seconds}") long timeoutSeconds) {
        this.apiKey = apiKey;
        this.models = models;

        // JDK client rather than SimpleClientHttpRequestFactory: that one
        // streams the body with chunked transfer encoding, and while a request
        // without a Content-Length is legal, it is one more thing for a proxy
        // between here and the venue wifi to have an opinion about. This sends
        // a length.
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build());
        factory.setReadTimeout(Duration.ofSeconds(timeoutSeconds));

        this.http = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .build();
    }

    public boolean isAvailable() {
        return apiKey != null && !apiKey.isBlank();
    }

    /**
     * @param content the user message: either a plain string or the
     *                OpenAI content-part list, for requests carrying images
     * @param shape   the record the answer is parsed into; its JSON schema is
     *                derived from the declaration rather than written out
     * @throws RuntimeException on transport failure, a non-2xx, or an answer
     *                          that will not parse — every caller degrades
     */
    public <T> T complete(String system, Object content, Class<T> shape, Duration timeout) {
        if (!isAvailable()) {
            throw new IllegalStateException("OPENROUTER_API_KEY is not configured");
        }

        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("name", shape.getSimpleName());
        schema.put("strict", true);
        schema.put("schema", JsonSchemas.of(shape));

        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", system));
        messages.add(Map.of("role", "user", "content", content));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", models.get(0));
        if (models.size() > 1) {
            body.put("models", models);
            body.put("route", "fallback");
        }
        body.put("messages", messages);
        body.put("max_tokens", 4096);
        body.put("response_format", Map.of("type", "json_schema", "json_schema", schema));

        String answer = http.post()
                .uri("/chat/completions")
                .header("Authorization", "Bearer " + apiKey)
                // Attribution headers are optional; the title is here so the
                // usage page says which project spent the credits.
                .header("X-OpenRouter-Title", "hanamizuki-ai")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(String.class);

        return parse(answer, shape);
    }

    /**
     * Pulls the assistant message out and reads it as {@code shape}.
     *
     * <p>{@code strict} makes the content valid against the schema, but it is
     * still a JSON string inside a JSON envelope, and a provider that fell over
     * mid-stream can leave {@code content} empty with a {@code finish_reason}
     * explaining why. Treated as a failure so the caller falls back, rather
     * than as an empty album.
     */
    private <T> T parse(String answer, Class<T> shape) {
        var root = json.readTree(answer);
        var choices = root.get("choices");
        if (choices == null || choices.isEmpty()) {
            var error = root.get("error");
            throw new IllegalStateException("OpenRouter returned no choices"
                    + (error == null ? "" : ": " + error));
        }
        var first = choices.get(0);
        var content = first.path("message").path("content");
        if (content.isMissingNode() || content.asString().isBlank()) {
            throw new IllegalStateException("OpenRouter returned empty content, finish_reason="
                    + first.path("finish_reason").asString());
        }
        String used = root.path("model").asString();
        if (!used.isBlank() && !used.equals(models.get(0))) {
            // Not an error, but the demo should not silently be running on a
            // different model than anyone thinks.
            log.info("OpenRouter served this from {} rather than {}", used, models.get(0));
        }
        return json.readValue(content.asString(), shape);
    }

    /** Which model the caller should say wrote the copy, for {@code album.aiModel}. */
    public String primaryModel() {
        return models.get(0);
    }
}
