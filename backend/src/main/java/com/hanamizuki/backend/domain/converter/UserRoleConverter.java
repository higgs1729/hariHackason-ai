package com.hanamizuki.backend.domain.converter;

import com.hanamizuki.backend.domain.enums.UserRole;

import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class UserRoleConverter extends LowerCaseEnumConverter<UserRole> {
    public UserRoleConverter() {
        super(UserRole.class);
    }
}
