package com.stockvision.auth.api;

/**
 * Neutral registration response designed to prevent email enumeration.
 */
public record RegisterResponse(String status, String message) {
}
