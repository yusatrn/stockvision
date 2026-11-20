package com.stockvision.watchlist.api;

import com.stockvision.auth.domain.AuthenticatedUser;
import com.stockvision.common.exception.AppException;
import com.stockvision.common.exception.ErrorCode;
import com.stockvision.watchlist.application.WatchlistService;
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
 * Authenticated endpoints for user-owned named watchlists and instrument memberships.
 */
@RestController
@RequestMapping("/api/v1/watchlists")
@Tag(name = "Watchlists", description = "User-owned watchlists with duplicate protection and quotes")
@SecurityRequirement(name = "bearerAuth")
public class WatchlistController {

    private final WatchlistService watchlistService;

    public WatchlistController(WatchlistService watchlistService) {
        this.watchlistService = watchlistService;
    }

    @GetMapping
    @Operation(summary = "List user watchlists", description = "Returns all watchlists owned by the authenticated user.")
    public WatchlistCollectionResponse listWatchlists(
            @AuthenticationPrincipal AuthenticatedUser principal) {
        return watchlistService.listWatchlists(requireUserId(principal));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create watchlist", description = "Creates a new named watchlist for the authenticated user.")
    public WatchlistResponse createWatchlist(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody CreateWatchlistRequest request) {
        return watchlistService.createWatchlist(requireUserId(principal), request);
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Update watchlist", description = "Updates name or description for an owned watchlist.")
    public WatchlistResponse updateWatchlist(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable("id") UUID id,
            @Valid @RequestBody UpdateWatchlistRequest request) {
        return watchlistService.updateWatchlist(requireUserId(principal), id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete watchlist", description = "Deletes an owned watchlist and all its entries.")
    public void deleteWatchlist(
            @AuthenticationPrincipal AuthenticatedUser principal, @PathVariable("id") UUID id) {
        watchlistService.deleteWatchlist(requireUserId(principal), id);
    }

    @PostMapping("/{id}/items")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add instrument to watchlist", description = "Adds an active instrument to an owned watchlist; duplicates are rejected.")
    public WatchlistItemResponse addItem(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable("id") UUID id,
            @Valid @RequestBody AddWatchlistItemRequest request) {
        return watchlistService.addItem(requireUserId(principal), id, request);
    }

    @DeleteMapping("/{id}/items/{itemId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Remove instrument from watchlist", description = "Removes an entry from an owned watchlist.")
    public void removeItem(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable("id") UUID id,
            @PathVariable("itemId") UUID itemId) {
        watchlistService.removeItem(requireUserId(principal), id, itemId);
    }

    private static UUID requireUserId(AuthenticatedUser principal) {
        if (principal == null) {
            throw new AppException(ErrorCode.UNAUTHORIZED, "Authentication required");
        }
        return principal.userId();
    }
}
