package com.mealflex.common.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.List;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiError {

    private String code;
    private String message;
    private List<FieldError> details;
    private Instant timestamp;

    @Getter
    @Builder
    public static class FieldError {
        private String field;
        private String message;
    }
}
