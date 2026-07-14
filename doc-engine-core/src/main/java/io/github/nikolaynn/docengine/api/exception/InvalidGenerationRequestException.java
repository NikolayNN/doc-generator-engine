package io.github.nikolaynn.docengine.api.exception;

public class InvalidGenerationRequestException extends DocumentGenerationException {
    public InvalidGenerationRequestException(String message) {
        super(null, null, null, message, null);
    }
}
