package com.stockvision.auth.infrastructure;

import com.stockvision.user.domain.EmailNormalizer;
import com.stockvision.user.domain.User;
import com.stockvision.user.domain.UserRepository;
import com.stockvision.user.domain.UserStatus;
import java.util.List;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * Bridges {@link UserRepository} to Spring Security {@link UserDetailsService} and disables
 * Spring Boot's default generated in-memory development password.
 */
@Service
public class StockVisionUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public StockVisionUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        String normalizedEmail;
        try {
            normalizedEmail = EmailNormalizer.normalize(username);
        } catch (IllegalArgumentException ex) {
            throw new UsernameNotFoundException("User not found");
        }

        User user =
                userRepository
                        .findByEmail(normalizedEmail)
                        .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        return org.springframework.security.core.userdetails.User.withUsername(user.email())
                .password(user.passwordHash())
                .authorities(List.of(new SimpleGrantedAuthority("ROLE_" + user.role().name())))
                .disabled(!user.isActive())
                .accountLocked(user.status() == UserStatus.LOCKED)
                .build();
    }
}
