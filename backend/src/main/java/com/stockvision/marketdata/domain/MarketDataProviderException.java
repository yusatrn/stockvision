package com.stockvision.marketdata.domain;

/**
 * Thrown when a {@link MarketDataProvider} fails, times out, or cannot serve valid data.
 */
public class MarketDataProviderException extends RuntimeException {

    public MarketDataProviderException(String message) {
        super(message);
    }

    public MarketDataProviderException(String message, Throwable cause) {
        super(message, cause);
    }
}
