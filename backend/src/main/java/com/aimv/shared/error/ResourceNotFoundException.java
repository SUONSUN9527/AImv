package com.aimv.shared.error;

import org.springframework.http.HttpStatus;

public class ResourceNotFoundException extends BusinessException {

    public ResourceNotFoundException(String message) {
        super(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", message);
    }
}
