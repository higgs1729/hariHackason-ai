package com.hanamizuki.backend.domain.converter;

import com.hanamizuki.backend.domain.enums.MediaType;

import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class MediaTypeConverter extends LowerCaseEnumConverter<MediaType> {
    public MediaTypeConverter() {
        super(MediaType.class);
    }
}
