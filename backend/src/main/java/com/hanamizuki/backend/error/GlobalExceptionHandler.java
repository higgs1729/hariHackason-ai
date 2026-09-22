package com.hanamizuki.backend.error;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Turns exceptions into {@link ApiError}. The only place in the application
 * that decides an HTTP status for a failure.
 *
 * <p>Extending {@link ResponseEntityExceptionHandler} is load-bearing, not
 * decoration. Without it the {@code @ExceptionHandler(Exception.class)}
 * catch-all below is the most specific match for Spring's own dispatch
 * exceptions too, so an unknown path and a wrong HTTP verb both come back as
 * 500 INTERNAL_ERROR instead of 404 and 405. The base class supplies proper
 * handlers for those; {@link #handleExceptionInternal} then rewrites whatever
 * body they produced into our shape.
 *
 * <p>Two rules worth keeping: the {@code traceId} goes into both the log line
 * and the response, so a user can quote it and the line is findable; and the
 * catch-all never echoes {@code e.getMessage()} back to the client, because
 * stack-derived text leaks table and class names.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiError> handleApi(ApiException e) {
        String traceId = newTraceId();
        log.warn("[{}] {} — {}", traceId, e.code(), e.getMessage());
        return body(e.code(), e.getMessage(), e.details(), traceId);
    }

    /**
     * Raised by Hibernate when an {@code @Version} check fails. The current
     * value is deliberately not read here — loading the row again to report it
     * would race the very update that just lost.
     */
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ApiError> handleOptimisticLock(ObjectOptimisticLockingFailureException e) {
        String traceId = newTraceId();
        log.warn("[{}] VERSION_CONFLICT — {}", traceId, e.getMessage());
        return body(ErrorCode.VERSION_CONFLICT, "Resource was modified by someone else", null, traceId);
    }

    /*
     * No handler for MaxUploadSizeExceededException here. The base class already
     * claims it through its final handleException(Exception, WebRequest), and a
     * second mapping for the same type fails context startup with "Ambiguous
     * @ExceptionHandler method mapped". It is mapped by status in codeFor()
     * instead.
     */

    /** Anything genuinely unexpected. Spring's dispatch exceptions never reach here. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception e) {
        String traceId = newTraceId();
        log.error("[{}] unhandled exception", traceId, e);
        return body(ErrorCode.INTERNAL_ERROR, "Unexpected server error", null, traceId);
    }

    /** Bean-validation failures on {@code @Valid} request bodies. */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException e, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {
        Map<String, Object> details = new HashMap<>();
        e.getBindingResult().getFieldErrors()
                .forEach(fe -> details.putIfAbsent(fe.getField(), fe.getDefaultMessage()));
        String traceId = newTraceId();
        log.warn("[{}] VALIDATION_FAILED — {}", traceId, details);
        return ResponseEntity.status(ErrorCode.VALIDATION_FAILED.status())
                .body(new ApiError(ErrorCode.VALIDATION_FAILED.name(),
                        "Request validation failed", details, traceId));
    }

    /**
     * Every exception the base class handles funnels through here, so the
     * status it chose is kept and only the body is replaced.
     */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception e, Object requestBody, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {
        ErrorCode code = codeFor(status);
        String traceId = newTraceId();
        log.warn("[{}] {} — {}", traceId, code, e.getMessage());
        return ResponseEntity.status(status)
                .body(new ApiError(code.name(), e.getMessage(), null, traceId));
    }

    private ErrorCode codeFor(HttpStatusCode status) {
        if (status.isSameCodeAs(HttpStatus.NOT_FOUND)) {
            return ErrorCode.NOT_FOUND;
        }
        if (status.isSameCodeAs(HttpStatus.METHOD_NOT_ALLOWED)) {
            return ErrorCode.METHOD_NOT_ALLOWED;
        }
        if (status.isSameCodeAs(HttpStatus.UNSUPPORTED_MEDIA_TYPE)) {
            return ErrorCode.UNSUPPORTED_MEDIA;
        }
        if (status.isSameCodeAs(HttpStatus.CONTENT_TOO_LARGE)) {
            return ErrorCode.FILE_TOO_LARGE;
        }
        if (status.is4xxClientError()) {
            return ErrorCode.VALIDATION_FAILED;
        }
        return ErrorCode.INTERNAL_ERROR;
    }

    private ResponseEntity<ApiError> body(ErrorCode code, String message,
                                          Map<String, Object> details, String traceId) {
        return ResponseEntity.status(code.status())
                .body(new ApiError(code.name(), message, details, traceId));
    }

    private String newTraceId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
