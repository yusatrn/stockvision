package com.stockvision.portfolio.api;

import com.stockvision.auth.domain.AuthenticatedUser;
import com.stockvision.common.exception.AppException;
import com.stockvision.common.exception.ErrorCode;
import com.stockvision.portfolio.application.PortfolioService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Authenticated endpoints for user-owned virtual portfolios, transaction ledgers, and derived
 * BigDecimal performance metrics.
 */
@RestController
@RequestMapping("/api/v1/portfolios")
@Tag(name = "Portfolios", description = "User-owned virtual portfolios and transaction ledgers")
@SecurityRequirement(name = "bearerAuth")
public class PortfolioController {

    private final PortfolioService portfolioService;

    public PortfolioController(PortfolioService portfolioService) {
        this.portfolioService = portfolioService;
    }

    @GetMapping
    @Operation(summary = "List user portfolios", description = "Returns virtual portfolios owned by the authenticated user with derived counts.")
    public List<PortfolioSummaryResponse> listPortfolios(
            @AuthenticationPrincipal AuthenticatedUser principal) {
        return portfolioService.listPortfolios(requireUserId(principal));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create virtual portfolio", description = "Creates a new virtual portfolio with explicit fee/tax handling policy.")
    public PortfolioSummaryResponse createPortfolio(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody CreatePortfolioRequest request) {
        return portfolioService.createPortfolio(requireUserId(principal), request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete virtual portfolio", description = "Deletes an owned virtual portfolio and all ledger transactions.")
    public void deletePortfolio(
            @AuthenticationPrincipal AuthenticatedUser principal, @PathVariable("id") UUID id) {
        portfolioService.deletePortfolio(requireUserId(principal), id);
    }

    @GetMapping("/{id}/transactions")
    @Operation(summary = "List portfolio transactions", description = "Returns chronological BUY/SELL ledger entries for an owned portfolio.")
    public List<PortfolioTransactionResponse> listTransactions(
            @AuthenticationPrincipal AuthenticatedUser principal, @PathVariable("id") UUID id) {
        return portfolioService.listTransactions(requireUserId(principal), id);
    }

    @PostMapping("/{id}/transactions")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Record BUY/SELL transaction", description = "Appends a BUY or SELL ledger transaction after verifying open quantity and fee/tax rules.")
    public PortfolioTransactionResponse addTransaction(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable("id") UUID id,
            @Valid @RequestBody CreateTransactionRequest request) {
        return portfolioService.addTransaction(requireUserId(principal), id, request);
    }

    @DeleteMapping("/{id}/transactions/{transactionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete transaction", description = "Removes a ledger entry provided remaining chronology remains valid.")
    public void deleteTransaction(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable("id") UUID id,
            @PathVariable("transactionId") UUID transactionId) {
        portfolioService.deleteTransaction(requireUserId(principal), id, transactionId);
    }

    @GetMapping("/{id}/performance")
    @Operation(summary = "Calculate portfolio performance", description = "Derives open holdings, average cost, realized P&L, and unrealized P&L strictly from transactions.")
    public PortfolioPerformanceResponse getPerformance(
            @AuthenticationPrincipal AuthenticatedUser principal, @PathVariable("id") UUID id) {
        return portfolioService.getPerformance(requireUserId(principal), id);
    }

    private static UUID requireUserId(AuthenticatedUser principal) {
        if (principal == null) {
            throw new AppException(ErrorCode.UNAUTHORIZED, "Authentication required");
        }
        return principal.userId();
    }
}
