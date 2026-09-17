package com.queuewise.common.error;

import java.time.Instant;
import java.util.List;

public record ApiErrorResponse(Instant timestamp, int status, String error, String message,
                               String path, String correlationId, List<FieldErrorResponse> errors) {
    public record FieldErrorResponse(String field, String code, String message) { }
}
