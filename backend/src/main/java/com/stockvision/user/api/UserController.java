package com.stockvision.user.api;

import com.stockvision.auth.domain.AuthenticatedUser;
import com.stockvision.common.exception.AppException;
import com.stockvision.common.exception.ErrorCode;
import com.stockvision.user.application.UserProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST endpoints for the current authenticated user's profile.
 */
@RestController
@RequestMapping("/api/v1/users")
@Tag(name = "Users", description = "Authenticated user profile endpoints")
public class UserController {

    private final UserProfileService userProfileService;

    public UserController(UserProfileService userProfileService) {
        this.userProfileService = userProfileService;
    }

    @GetMapping("/me")
    @Operation(
            summary = "Get current user profile",
            description =
                    "Returns the authenticated user's profile without exposing password hashes or internal credentials.")
    @SecurityRequirement(name = "bearerAuth")
    public UserProfileResponse getCurrentUser(@AuthenticationPrincipal AuthenticatedUser principal) {
        if (principal == null) {
            throw new AppException(ErrorCode.UNAUTHORIZED, "Authentication required");
        }
        return userProfileService.getCurrentUserProfile(principal.userId());
    }
}
