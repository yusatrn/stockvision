package com.stockvision.user.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.stockvision.common.exception.AppException;
import com.stockvision.common.exception.ErrorCode;
import com.stockvision.user.api.UserProfileResponse;
import com.stockvision.user.domain.User;
import com.stockvision.user.domain.UserRepository;
import com.stockvision.user.domain.UserRole;
import com.stockvision.user.domain.UserStatus;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class UserProfileServiceTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final UserProfileService service = new UserProfileService(userRepository);

    @Test
    void returnsProfileForActiveUser() {
        UUID userId = UUID.randomUUID();
        Instant now = Instant.parse("2026-11-19T12:00:00Z");
        User user =
                new User(
                        userId,
                        "investor@example.com",
                        "Investor",
                        "secret-hash",
                        UserRole.USER,
                        UserStatus.ACTIVE,
                        "Europe/Istanbul",
                        now,
                        now);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        UserProfileResponse profile = service.getCurrentUserProfile(userId);

        assertThat(profile.id()).isEqualTo(userId);
        assertThat(profile.email()).isEqualTo("investor@example.com");
        assertThat(profile.role()).isEqualTo(UserRole.USER);
        assertThat(profile.status()).isEqualTo(UserStatus.ACTIVE);
    }

    @Test
    void throwsUnauthorizedWhenUserMissingOrSuspended() {
        UUID missingId = UUID.randomUUID();
        when(userRepository.findById(missingId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getCurrentUserProfile(missingId))
                .isInstanceOf(AppException.class)
                .satisfies(
                        ex ->
                                assertThat(((AppException) ex).getErrorCode())
                                        .isEqualTo(ErrorCode.UNAUTHORIZED));

        UUID suspendedId = UUID.randomUUID();
        Instant now = Instant.parse("2026-11-19T12:00:00Z");
        User suspended =
                new User(
                        suspendedId,
                        "suspended@example.com",
                        "Suspended",
                        "secret-hash",
                        UserRole.USER,
                        UserStatus.SUSPENDED,
                        "Europe/Istanbul",
                        now,
                        now);
        when(userRepository.findById(suspendedId)).thenReturn(Optional.of(suspended));

        assertThatThrownBy(() -> service.getCurrentUserProfile(suspendedId))
                .isInstanceOf(AppException.class)
                .satisfies(
                        ex ->
                                assertThat(((AppException) ex).getErrorCode())
                                        .isEqualTo(ErrorCode.UNAUTHORIZED));
    }
}
