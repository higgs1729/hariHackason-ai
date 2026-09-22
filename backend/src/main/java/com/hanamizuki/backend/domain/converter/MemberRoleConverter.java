package com.hanamizuki.backend.domain.converter;

import com.hanamizuki.backend.domain.enums.MemberRole;

import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class MemberRoleConverter extends LowerCaseEnumConverter<MemberRole> {
    public MemberRoleConverter() {
        super(MemberRole.class);
    }
}
