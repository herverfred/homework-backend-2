package com.example.demo.exception;

/**
 * Authentication Exception
 * Thrown when user login authentication fails
 * Will be caught by GlobalExceptionHandler and return 401 Unauthorized
 */
public class AuthenticationException extends RuntimeException {

    public AuthenticationException(String message) {
        super(message);
    }

    public AuthenticationException(String message, Throwable cause) {
        super(message, cause);
    }
}
