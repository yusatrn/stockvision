package com.stockvision.alert.api;

import com.stockvision.alert.application.AlertEvaluationService;
import com.stockvision.alert.application.AlertService;
import com.stockvision.auth.domain.AuthenticatedUser;
import com.stockvision.common.exception.AppException;
import com.stockvision.common.exception.ErrorCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Authenticated endpoints for managing price/indicator alert rules and triggering evaluation.
 */
@RestController
@RequestMapping("/api/v1/alerts")
@Tag(name = "Alerts", description = "Price and indicator threshold alert rules with cooldown protection")
@SecurityRequirement(name = "bearerAuth")
public class AlertController {

    private final AlertService alertService;
    private final AlertEvaluationService alertEvaluationService;

    public AlertController(
            AlertService alertService, AlertEvaluationService alertEvaluationService) {
        this.alertService = alertService;
        this.alertEvaluationService = alertEvaluationService;
    }

    @GetMapping
    @Operation(summary = "List user alert rules", description = "Returns all alert rules owned by the authenticated user with provider metadata.")
    public AlertRuleListResponse listAlerts(@AuthenticationPrincipal AuthenticatedUser principal) {
        return alertService.listAlerts(requireUserId(principal));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create alert rule", description = "Creates a price or technical indicator threshold rule with configurable cooldown.")
    public AlertRuleResponse createAlert(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody CreateAlertRequest request) {
        return alertService.createAlert(requireUserId(principal), request);
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Update alert rule", description = "Updates status (ACTIVE/PAUSED), threshold, cooldown, or note for an owned alert.")
    public AlertRuleResponse updateAlert(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable("id") UUID id,
            @Valid @RequestBody UpdateAlertRequest request) {
        return alertService.updateAlert(requireUserId(principal), id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete alert rule", description = "Deletes an owned alert rule and associated notifications.")
    public void deleteAlert(
            @AuthenticationPrincipal AuthenticatedUser principal, @PathVariable("id") UUID id) {
        alertService.deleteAlert(requireUserId(principal), id);
    }

    @PostMapping("/evaluate")
    @Operation(summary = "Evaluate active alerts now", description = "Runs idempotent evaluation against active rules owned by the authenticated user.")
    public AlertEvaluationRunResponse evaluateNow(
            @AuthenticationPrincipal AuthenticatedUser principal) {
        return alertEvaluationService.evaluateActiveRulesForUser(requireUserId(principal));
    }

    private static UUID requireUserId(AuthenticatedUser principal) {
        if (principal == null) {
            throw new AppException(ErrorCode.UNAUTHORIZED, "Authentication required");
        }
        return principal.userId();
    }
}
