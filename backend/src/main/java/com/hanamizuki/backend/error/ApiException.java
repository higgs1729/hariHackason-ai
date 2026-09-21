package com.hanamizuki.backend.error;

import java.util.Map;

/**
 * Every deliberate failure in the application is one of these. Services throw
 * it with an {@link ErrorCode}; {@link GlobalExceptionHandler} turns it into
 * the wire format. Nothing else should build an error response by hand.
 */
public class ApiException extends RuntimeException {

    private final ErrorCode code;
    private final transient Map<String, Object> details;

    public ApiException(ErrorCode code) {
        this(code, code.name(), null);
    }

    public ApiException(ErrorCode code, String message) {
        this(code, message, null);
    }

    public ApiException(ErrorCode code, String message, Map<String, Object> details) {
        super(message);
        this.code = code;
        this.details = details;
    }

    public ErrorCode code() {
        return code;
    }

    public Map<String, Object> details() {
        return details;
    }
}
