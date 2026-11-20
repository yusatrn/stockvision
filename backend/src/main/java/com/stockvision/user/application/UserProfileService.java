package com.stockvision.user.application;

import com.stockvision.common.exception.AppException;
import com.stockvision.common.exception.ErrorCode;
import com.stockvision.user.api.UserProfileResponse;
import com.stockvision.user.domain.User;
import com.stockvision.user.domain.UserRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Application service for reading the authenticated user's profile.
 */
@Service
@Transactional(readOnly = true)
public class UserProfileService {

    private final UserRepository userRepository;

    public UserProfileService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public UserProfileResponse getCurrentUserProfile(UUID userId) {
        User user =
                userRepository
                        .findById(userId)
                        .filter(User::isActive)
                        .orElseThrow(
                                () ->
                                        new AppException(
                                                ErrorCode.UNAUTHORIZED,
                                                "Authentication required"));
        return UserProfileResponse.from(user);
    }
}
