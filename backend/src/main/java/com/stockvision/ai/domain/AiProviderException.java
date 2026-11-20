package com.stockvision.ai.domain;

import java.util.Objects;

/**
 * Exception raised when an external AI explanation provider cannot return raw output.
 */
public class AiProviderException extends RuntimeException {

    private final AiProviderErrorType errorType;

    public AiProviderException(AiProviderErrorType errorType, String message) {
        super(message);
        this.errorType = Objects.requireNonNull(errorType, "errorType must not be null");
    }

    public AiProviderException(AiProviderErrorType errorType, String message, Throwable cause) {
        super(message, cause);
        this.errorType = Objects.requireNonNull(errorType, "errorType must not be null");
    }

    public AiProviderErrorType getErrorType() {
        return errorType;
    }
}
