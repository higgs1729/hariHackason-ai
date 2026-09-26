package com.hanamizuki.backend.integration.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * The schema OpenRouter is sent has to match the record it is parsed back into.
 *
 * <p>The Anthropic SDK derives this for us. Going through OpenRouter means
 * building it ourselves, and getting it wrong is a 400 on the request rather
 * than a worse answer — which at a demo is the difference between AI copy and
 * the rule-based fallback, with no clue in between.
 *
 * <p>Strict mode's two rules are the ones worth pinning, because neither is
 * guessable: every property must be listed in {@code required} even when it
 * can be null, and {@code additionalProperties} must be false on every object.
 */
class JsonSchemasTest {

    @SuppressWarnings("unchecked")
    private static Map<String, Object> properties(Map<String, Object> schema) {
        return (Map<String, Object>) schema.get("properties");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> property(Map<String, Object> schema, String name) {
        return (Map<String, Object>) properties(schema).get(name);
    }

    @Test
    void theShootHintSchemaMatchesItsRecord() {
        Map<String, Object> schema = JsonSchemas.of(ShootHint.class);

        assertThat(schema).containsEntry("type", "object")
                .containsEntry("additionalProperties", false);
        assertThat(properties(schema)).containsOnlyKeys("hint", "poses");
        assertThat((List<String>) schema.get("required"))
                .containsExactlyInAnyOrder("hint", "poses");

        assertThat(property(schema, "hint")).containsEntry("type", List.of("string", "null"));
        assertThat(property(schema, "poses")).containsEntry("type", List.of("array", "null"));
    }

    @Test
    void aListOfStringsDescribesItsElements() {
        Map<String, Object> poses = property(JsonSchemas.of(ShootHint.class), "poses");

        assertThat((Map<String, Object>) poses.get("items"))
                .containsEntry("type", List.of("string", "null"));
    }

    @Test
    void theAlbumSchemaMatchesItsRecord() {
        Map<String, Object> schema = JsonSchemas.of(AlbumDraft.class);

        assertThat(properties(schema))
                .containsOnlyKeys("title", "coverPhotoId", "summary", "photos");
        // A Long has to go out as integer, or a model that obeys the schema
        // hands back a string and the parse fails.
        assertThat(property(schema, "coverPhotoId"))
                .containsEntry("type", List.of("integer", "null"));
    }

    /** The nested record is expanded, not described as an opaque object. */
    @Test
    void aListOfRecordsIsExpanded() {
        Map<String, Object> photos = property(JsonSchemas.of(AlbumDraft.class), "photos");
        Map<String, Object> item = (Map<String, Object>) photos.get("items");

        assertThat(item).containsEntry("type", "object")
                .containsEntry("additionalProperties", false);
        assertThat(properties(item))
                .containsOnlyKeys("photoId", "caption", "place", "weather", "comment");
        assertThat((List<String>) item.get("required")).hasSize(5);
    }

    /**
     * Every field of both shapes is nullable on purpose: the prompt tells the
     * model to use null rather than invent a place it cannot see, and a schema
     * that forbade null would make inventing one the only legal answer.
     */
    @Test
    void everyFieldAcceptsNull() {
        for (Class<?> shape : List.of(ShootHint.class, AlbumDraft.class, PhotoInsight.class)) {
            properties(JsonSchemas.of(shape)).forEach((name, described) -> {
                Object type = ((Map<String, Object>) described).get("type");
                assertThat(type).as("%s.%s", shape.getSimpleName(), name)
                        .satisfiesAnyOf(
                                t -> assertThat((List<String>) t).contains("null"),
                                // a nested record is an object, and its own
                                // fields are checked on the next pass
                                t -> assertThat(t).isEqualTo("object"));
            });
        }
    }

    @Test
    void aNonRecordIsRefusedRatherThanGuessedAt() {
        assertThatThrownBy(() -> JsonSchemas.of(String.class))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
