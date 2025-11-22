package com.example.demo.exception;

/**
 * Resource Not Found Exception
 * Thrown when the requested resource (user, game, etc.) does not exist
 * Will be caught by GlobalExceptionHandler and return 404 Not Found
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String resourceName, Object id) {
        super(String.format("%s not found with id: %s", resourceName, id));
    }

    public ResourceNotFoundException(String message) {
        super(message);
    }

    public ResourceNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
