package com.hanamizuki.backend.security;

import java.io.IOException;

import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import tools.jackson.databind.ObjectMapper;
import com.hanamizuki.backend.error.ApiError;
import com.hanamizuki.backend.error.ErrorCode;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Turns an unauthenticated request into the same {@link ApiError} body every
 * other failure uses. Without this, Spring Security would answer with an empty
 * 401 and the frontend would have no {@code code} to branch on.
 */
@Component
public class RestAuthEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    public RestAuthEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        Object recorded = request.getAttribute(JwtFilter.FAILURE_ATTRIBUTE);
        // No token at all is as invalid as a broken one; only a verified
        // expiry earns TOKEN_EXPIRED, so a missing header cannot send the
        // client into a pointless refresh.
        ErrorCode code = recorded instanceof ErrorCode c ? c : ErrorCode.TOKEN_INVALID;

        response.setStatus(code.status().value());
        response.setContentType("application/json;charset=UTF-8");
        objectMapper.writeValue(response.getOutputStream(),
                new ApiError(code.name(), "Authentication required", null, null));
    }
}
