package com.hanamizuki.backend.integration.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import com.sun.net.httpserver.HttpServer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * The OpenRouter adapter, against a stub server rather than the real thing.
 *
 * <p>Two things are worth pinning here and neither needs the network: the body
 * that goes out has the shape OpenRouter documents, and an answer that is not
 * a usable one is raised rather than returned. The second matters most — every
 * caller of this class degrades to canned text on an exception, so a failure
 * that returns an empty object instead of throwing turns into a blank screen
 * with no fallback and no error.
 */
class OpenRouterClientTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    private HttpServer server;
    private final AtomicReference<String> lastBody = new AtomicReference<>();
    private final AtomicReference<String> reply = new AtomicReference<>();
    private final AtomicReference<Integer> status = new AtomicReference<>(200);

    @BeforeEach
    void startStub() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/v1/chat/completions", exchange -> {
            lastBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] out = reply.get().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status.get(), out.length);
            exchange.getResponseBody().write(out);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stopStub() {
        server.stop(0);
    }

    private OpenRouterClient client() {
        String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/api/v1";
        return new OpenRouterClient("test-key", baseUrl,
                List.of("anthropic/claude-sonnet-5", "google/gemini-3.8-flash"), 5);
    }

    /** Wraps a structured answer the way a real response carries it. */
    private static String envelope(String contentJson) {
        return """
                {"model":"anthropic/claude-sonnet-5","choices":[
                  {"finish_reason":"stop","message":{"role":"assistant","content":%s}}]}
                """.formatted(JSON.writeValueAsString(contentJson));
    }

    @Test
    void aStructuredAnswerComesBackAsTheRecord() {
        reply.set(envelope("""
                {"hint":"肩を寄せて撮ろう","poses":["ピース","背中合わせ"]}"""));

        ShootHint hint = client().complete("system", "user", ShootHint.class, Duration.ofSeconds(5));

        assertThat(hint.hint()).isEqualTo("肩を寄せて撮ろう");
        assertThat(hint.poses()).containsExactly("ピース", "背中合わせ");
    }

    @Test
    void theRequestCarriesTheSchemaAndTheFallbackChain() {
        reply.set(envelope("""
                {"hint":"よし","poses":["ピース"]}"""));
        client().complete("システム", "ユーザー", ShootHint.class, Duration.ofSeconds(5));

        JsonNode body = JSON.readTree(lastBody.get());
        assertThat(body.path("model").asString()).isEqualTo("anthropic/claude-sonnet-5");
        assertThat(body.path("route").asString()).isEqualTo("fallback");
        assertThat(body.path("models")).hasSize(2);

        JsonNode schema = body.path("response_format").path("json_schema");
        assertThat(body.path("response_format").path("type").asString()).isEqualTo("json_schema");
        assertThat(schema.path("strict").asBoolean()).isTrue();
        assertThat(schema.path("schema").path("properties").propertyNames())
                .containsExactlyInAnyOrder("hint", "poses");

        // The Japanese prompt has to survive the round trip as itself.
        assertThat(body.path("messages").get(0).path("content").asString()).isEqualTo("システム");
        assertThat(body.path("messages").get(1).path("content").asString()).isEqualTo("ユーザー");
    }

    @Test
    void imageContentPartsArePassedThrough() {
        reply.set(envelope("""
                {"title":"放課後","coverPhotoId":7,"summary":"みんなで。","photos":[]}"""));

        List<Map<String, Object>> content = List.of(
                Map.of("type", "image_url", "image_url", Map.of("url", "data:image/jpeg;base64,AAAA")),
                Map.of("type", "text", "text", "写真一覧"));
        AlbumDraft draft = client().complete("s", content, AlbumDraft.class, Duration.ofSeconds(5));

        assertThat(draft.title()).isEqualTo("放課後");
        assertThat(draft.coverPhotoId()).isEqualTo(7L);

        JsonNode parts = JSON.readTree(lastBody.get()).path("messages").get(1).path("content");
        assertThat(parts).hasSize(2);
        assertThat(parts.get(0).path("image_url").path("url").asString())
                .startsWith("data:image/jpeg;base64,");
    }

    /**
     * A provider that ran out of tokens answers 200 with an empty content and
     * a finish_reason. Returning an empty record here would produce an album
     * with no words and no sign that anything went wrong.
     */
    @Test
    void anEmptyAnswerIsRaisedNotReturned() {
        reply.set("""
                {"model":"anthropic/claude-sonnet-5","choices":[
                  {"finish_reason":"length","message":{"role":"assistant","content":""}}]}""");

        assertThatThrownBy(() -> client()
                .complete("s", "u", ShootHint.class, Duration.ofSeconds(5)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("length");
    }

    @Test
    void anErrorEnvelopeIsRaised() {
        reply.set("""
                {"error":{"code":402,"message":"Insufficient credits"}}""");

        assertThatThrownBy(() -> client()
                .complete("s", "u", ShootHint.class, Duration.ofSeconds(5)))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void anHttpFailureIsRaised() {
        status.set(429);
        reply.set("""
                {"error":{"message":"rate limited"}}""");

        assertThatThrownBy(() -> client()
                .complete("s", "u", ShootHint.class, Duration.ofSeconds(5)))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void contentThatIsNotJsonIsRaised() {
        reply.set(envelope("sorry, I cannot do that"));

        assertThatThrownBy(() -> client()
                .complete("s", "u", ShootHint.class, Duration.ofSeconds(5)))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void noKeyMeansNotAvailableAndNoCall() {
        OpenRouterClient keyless = new OpenRouterClient("  ", "http://127.0.0.1:1",
                List.of("anthropic/claude-sonnet-5"), 5);

        assertThat(keyless.isAvailable()).isFalse();
        assertThatThrownBy(() -> keyless
                .complete("s", "u", ShootHint.class, Duration.ofSeconds(5)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("OPENROUTER_API_KEY");
    }
}
