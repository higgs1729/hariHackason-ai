package com.hanamizuki.backend.domain.converter;

import com.hanamizuki.backend.domain.enums.FriendStatus;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** {@code friend.status} is a tinyint, not a string. */
@Converter(autoApply = true)
public class FriendStatusConverter implements AttributeConverter<FriendStatus, Integer> {

    @Override
    public Integer convertToDatabaseColumn(FriendStatus value) {
        return value == null ? null : value.code();
    }

    @Override
    public FriendStatus convertToEntityAttribute(Integer column) {
        return column == null ? null : FriendStatus.of(column);
    }
}
