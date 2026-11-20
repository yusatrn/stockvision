package com.stockvision.portfolio.api;

import com.stockvision.portfolio.domain.TransactionSide;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * Payload to append a virtual BUY or SELL execution to a portfolio's ledger.
 */
public record CreateTransactionRequest(
        @NotBlank(message = "symbol is required")
                @Size(max = 24, message = "symbol must not exceed 24 characters")
                String symbol,
        @NotNull(message = "side is required") TransactionSide side,
        @NotNull(message = "quantity is required")
                @DecimalMin(value = "0.000001", message = "quantity must be greater than zero")
                BigDecimal quantity,
        @NotNull(message = "unitPrice is required")
                @DecimalMin(value = "0.0001", message = "unitPrice must be greater than zero")
                BigDecimal unitPrice,
        @DecimalMin(value = "0.00", message = "feeAmount must not be negative")
                BigDecimal feeAmount,
        @DecimalMin(value = "0.00", message = "taxAmount must not be negative")
                BigDecimal taxAmount,
        Instant executedAt,
        @Size(max = 255, message = "notes must not exceed 255 characters") String notes) {
}
