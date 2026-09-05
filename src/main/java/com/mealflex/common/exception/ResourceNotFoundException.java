package com.mealflex.common.exception;

import org.springframework.http.HttpStatus;

public class ResourceNotFoundException extends BusinessException {

    public ResourceNotFoundException(String entityName, Long id) {
        super("RESOURCE_NOT_FOUND",
              entityName + " bulunamadı. ID: " + id,
              HttpStatus.NOT_FOUND);
    }

    public ResourceNotFoundException(String entityName, String identifier) {
        super("RESOURCE_NOT_FOUND",
              entityName + " bulunamadı: " + identifier,
              HttpStatus.NOT_FOUND);
    }
}
