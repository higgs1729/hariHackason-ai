package com.hanamizuki.backend.domain.converter;

import jakarta.persistence.AttributeConverter;

/**
 * Maps an enum to its lowercase name and back.
 *
 * <p>{@code @Enumerated(STRING)} would write {@code OWNER}, but the DDL stores
 * {@code owner} and a MySQL default collation would make that comparison
 * case-insensitive by accident rather than by design. Being explicit keeps the
 * column readable in a SQL client and the mapping deliberate.
 */
abstract class LowerCaseEnumConverter<E extends Enum<E>> implements AttributeConverter<E, String> {

    private final Class<E> type;

    protected LowerCaseEnumConverter(Class<E> type) {
        this.type = type;
    }

    @Override
    public String convertToDatabaseColumn(E value) {
        return value == null ? null : value.name().toLowerCase();
    }

    @Override
    public E convertToEntityAttribute(String column) {
        return column == null ? null : Enum.valueOf(type, column.toUpperCase());
    }
}
