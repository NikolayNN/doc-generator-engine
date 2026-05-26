package com.example.docengine.api.exception;

public class InvalidGenerationRequestException extends DocumentGenerationException {
    public InvalidGenerationRequestException(String message) {
        super(null, null, null, message, null);
    }
}
