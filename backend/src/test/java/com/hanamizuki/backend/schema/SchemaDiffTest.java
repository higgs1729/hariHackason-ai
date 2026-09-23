package com.hanamizuki.backend.schema;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

/**
 * Compares the schema the entities imply against the DDL that actually builds
 * the database.
 *
 * <p>A stand-in for {@code ddl-auto=validate}, which is the real check but
 * needs a running MySQL. As a test it means an entity that drifts from
 * {@code create_table.sql} fails the build, rather than the next person to
 * start the app.
 *
 * <p>It exports the expected schema itself rather than relying on another
 * test having run first: a check that silently skips depending on test order
 * is worse than no check.
 */
class SchemaDiffTest {

    private static final Path EXPECTED = Path.of("target", "expected-schema.sql");
    private static final Path DDL = Path.of("sql", "create_table.sql");

    /**
     * Differences MySQL does not actually have: {@code integer} is {@code int},
     * {@code float(53)} is {@code double}, a native {@code enum} column accepts
     * the same strings a {@code varchar} does, and a {@code datetime} without
     * an explicit precision is still a datetime.
     */
    private static final Set<String> BENIGN = Set.of(
            "integer|int", "float|double", "enum|varchar", "datetime|datetime");

    @Test
    void entitiesAgreeWithTheDdl() throws IOException {
        ExpectedSchema.writeTo(EXPECTED);
        Map<String, Map<String, String>> expected = parseExpected(Files.readString(EXPECTED));
        Map<String, Map<String, String>> actual = parseDdl(Files.readString(DDL));

        List<String> problems = new ArrayList<>();
        for (var table : actual.entrySet()) {
            Map<String, String> wanted = expected.get(table.getKey());
            if (wanted == null) {
                continue;
            }
            for (var column : wanted.entrySet()) {
                String have = table.getValue().get(column.getKey());
                if (have == null) {
                    problems.add(table.getKey() + "." + column.getKey()
                            + ": the entity expects it, the DDL has no such column");
                } else if (!compatible(column.getValue(), have)) {
                    problems.add(table.getKey() + "." + column.getKey()
                            + ": entity wants " + column.getValue() + ", DDL has " + have);
                }
            }
        }
        assertThat(problems).isEmpty();
    }

    private static boolean compatible(String wanted, String have) {
        if (wanted.equals(have)) {
            return true;
        }
        String wantedBase = baseType(wanted);
        String haveBase = baseType(have);
        return wantedBase.equals(haveBase) && BENIGN.contains(wantedBase + "|" + haveBase)
                || BENIGN.contains(wantedBase + "|" + haveBase);
    }

    private static String baseType(String type) {
        int paren = type.indexOf('(');
        return paren < 0 ? type : type.substring(0, paren);
    }

    private static Map<String, Map<String, String>> parseExpected(String text) {
        Map<String, Map<String, String>> tables = new LinkedHashMap<>();
        Matcher m = Pattern.compile("create table (\\w+)\\s*\\((.*?)\\)\\s*engine",
                Pattern.DOTALL | Pattern.CASE_INSENSITIVE).matcher(text);
        while (m.find()) {
            tables.put(m.group(1), splitTopLevel(m.group(2)));
        }
        return tables;
    }

    /** Splits on commas outside parentheses, so {@code decimal(10,7)} survives. */
    private static Map<String, String> splitTopLevel(String body) {
        Map<String, String> columns = new LinkedHashMap<>();
        StringBuilder current = new StringBuilder();
        int depth = 0;
        for (char c : body.toCharArray()) {
            if (c == '(') {
                depth++;
            }
            if (c == ')') {
                depth--;
            }
            if (c == ',' && depth == 0) {
                addColumn(columns, current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        addColumn(columns, current.toString());
        return columns;
    }

    private static Map<String, Map<String, String>> parseDdl(String text) {
        Map<String, Map<String, String>> tables = new LinkedHashMap<>();
        Matcher m = Pattern.compile("create table if not exists (\\w+)\\s*\\((.*?)\\n\\) comment",
                Pattern.DOTALL | Pattern.CASE_INSENSITIVE).matcher(text);
        while (m.find()) {
            Map<String, String> columns = new LinkedHashMap<>();
            for (String line : m.group(2).split("\\n")) {
                String cleaned = line.trim()
                        .replaceAll(",$", "")
                        .replaceAll("comment\\s+'.*$", "")
                        .replaceAll("decimal\\(\\s*(\\d+),\\s*(\\d+)\\)", "decimal($1,$2)")
                        .trim();
                addColumn(columns, cleaned);
            }
            tables.put(m.group(1), columns);
        }
        return tables;
    }

    private static void addColumn(Map<String, String> columns, String fragment) {
        String text = fragment.trim();
        if (text.isEmpty()
                || text.matches("(?i)^(primary key|unique key|unique|key|index|constraint|foreign key)\\b.*")) {
            return;
        }
        String[] parts = text.split("\\s+");
        if (parts.length >= 2) {
            columns.put(parts[0], parts[1].toLowerCase());
        }
    }
}
