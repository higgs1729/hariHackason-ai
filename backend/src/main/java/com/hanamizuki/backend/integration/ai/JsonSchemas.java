package com.hanamizuki.backend.integration.ai;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds an OpenAI-style JSON schema from a record.
 *
 * <p>The Anthropic SDK does this for us from {@code .outputConfig(Foo.class)}.
 * OpenRouter speaks the OpenAI shape, where the schema is a literal object in
 * the request body — so going through it would mean hand-writing the schema
 * for {@link AlbumDraft} and {@link ShootHint} and keeping it in step with the
 * records by memory. Two ways to describe the same thing is one too many;
 * this derives it instead, same as the SDK does.
 *
 * <p>Strict mode has rules worth stating, because they are not obvious and a
 * violation is a 400 rather than a degraded answer:
 *
 * <ul>
 *   <li>every property must appear in {@code required} — optionality is
 *       expressed by unioning the type with {@code "null"}, not by omission
 *   <li>{@code additionalProperties} must be {@code false} on every object
 * </ul>
 *
 * <p>Every field here is nullable by design: the model is told to use null
 * rather than invent a place or a caption it cannot see.
 */
public final class JsonSchemas {

    private JsonSchemas() {
    }

    /** @throws IllegalArgumentException if {@code type} is not a record */
    public static Map<String, Object> of(Class<?> type) {
        if (!type.isRecord()) {
            throw new IllegalArgumentException(type + " is not a record");
        }
        Map<String, Object> properties = new LinkedHashMap<>();
        List<String> required = new ArrayList<>();
        for (RecordComponent component : type.getRecordComponents()) {
            properties.put(component.getName(),
                    describe(component.getType(), component.getGenericType()));
            required.add(component.getName());
        }
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", required);
        schema.put("additionalProperties", false);
        return schema;
    }

    private static Map<String, Object> describe(Class<?> raw, Type generic) {
        if (raw.isRecord()) {
            return of(raw);
        }
        if (List.class.isAssignableFrom(raw)) {
            Class<?> element = elementOf(generic);
            Map<String, Object> array = new LinkedHashMap<>();
            array.put("type", List.of("array", "null"));
            // Element type only, not its generics: a list of lists would need
            // more than this, and neither output shape has one.
            array.put("items", describe(element, element));
            return array;
        }
        Map<String, Object> leaf = new LinkedHashMap<>();
        leaf.put("type", List.of(jsonType(raw), "null"));
        return leaf;
    }

    /** Defaults to String for a raw {@code List}, which is the safe read. */
    private static Class<?> elementOf(Type generic) {
        if (generic instanceof ParameterizedType parameterized) {
            Type[] arguments = parameterized.getActualTypeArguments();
            if (arguments.length == 1 && arguments[0] instanceof Class<?> element) {
                return element;
            }
        }
        return String.class;
    }

    private static String jsonType(Class<?> raw) {
        if (raw == Long.class || raw == long.class
                || raw == Integer.class || raw == int.class) {
            return "integer";
        }
        if (raw == Double.class || raw == double.class
                || raw == Float.class || raw == float.class) {
            return "number";
        }
        if (raw == Boolean.class || raw == boolean.class) {
            return "boolean";
        }
        return "string";
    }
}
