package com.hanamizuki.backend.error;

import org.springframework.http.HttpStatus;

/**
 * The full error vocabulary, from section 3.2 of the detailed design.
 *
 * <p>The frontend branches on {@code code}, never on the HTTP status or the
 * message text, so these names are part of the contract — renaming one is a
 * breaking change. The status lives here rather than at each throw site so a
 * given code can never come back with two different statuses.
 */
public enum ErrorCode {

    VALIDATION_FAILED(HttpStatus.BAD_REQUEST),
    UNSUPPORTED_MEDIA(HttpStatus.BAD_REQUEST),
    IDEMPOTENCY_KEY_REQUIRED(HttpStatus.BAD_REQUEST),

    /** Access token past its 15 minutes: refresh and resend. */
    TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED),
    /** Tampered or revoked: log the user out, do not retry. */
    TOKEN_INVALID(HttpStatus.UNAUTHORIZED),
    CREDENTIALS_INVALID(HttpStatus.UNAUTHORIZED),

    /** {@code user.userRole = 'ban'}: the credentials were right, the account is not. */
    ACCOUNT_BANNED(HttpStatus.FORBIDDEN),
    ALBUM_FORBIDDEN(HttpStatus.FORBIDDEN),
    ROLE_INSUFFICIENT(HttpStatus.FORBIDDEN),
    NOT_FRIENDS(HttpStatus.FORBIDDEN),
    USER_BLOCKED(HttpStatus.FORBIDDEN),

    /**
     * Not in the section 3.2 table. Needed for routing-level misses — an unknown
     * path has no resource-specific code to report.
     */
    NOT_FOUND(HttpStatus.NOT_FOUND),
    /** Also an addition: wrong HTTP verb on a real path. */
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED),

    USER_NOT_FOUND(HttpStatus.NOT_FOUND),
    PHOTO_NOT_FOUND(HttpStatus.NOT_FOUND),
    ALBUM_NOT_FOUND(HttpStatus.NOT_FOUND),
    CAPSULE_NOT_FOUND(HttpStatus.NOT_FOUND),
    JOB_NOT_FOUND(HttpStatus.NOT_FOUND),

    /** {@code userAccount} is taken. */
    ACCOUNT_EXISTS(HttpStatus.CONFLICT),
    /** If-Match did not match; {@code details.current} carries the live version. */
    VERSION_CONFLICT(HttpStatus.CONFLICT),
    FRIEND_REQUEST_EXISTS(HttpStatus.CONFLICT),
    CAPSULE_NOT_YET_OPEN(HttpStatus.CONFLICT),
    JOB_ALREADY_RUNNING(HttpStatus.CONFLICT),

    SHARE_REVOKED(HttpStatus.GONE),
    SHARE_EXPIRED(HttpStatus.GONE),
    /** Friend QR past its ten minutes, already scanned, or never issued. */
    QR_EXPIRED(HttpStatus.GONE),

    FILE_TOO_LARGE(HttpStatus.CONTENT_TOO_LARGE),
    NO_VALID_PHOTOS(HttpStatus.UNPROCESSABLE_CONTENT),
    IF_MATCH_REQUIRED(HttpStatus.PRECONDITION_REQUIRED),
    RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS),

    /** Not in the spec table: the catch-all for anything unmapped. */
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR);

    private final HttpStatus status;

    ErrorCode(HttpStatus status) {
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }
}
