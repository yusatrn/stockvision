package com.stockvision.auth.infrastructure;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Password hashing configuration properties.
 */
@ConfigurationProperties(prefix = "app.security")
public class SecurityPasswordProperties {

    private static final int MIN_BCRYPT_STRENGTH = 10;
    private static final int MAX_BCRYPT_STRENGTH = 14;

    private int bcryptStrength = 12;

    @PostConstruct
    public void validate() {
        if (bcryptStrength < MIN_BCRYPT_STRENGTH || bcryptStrength > MAX_BCRYPT_STRENGTH) {
            throw new IllegalStateException(
                    "app.security.bcrypt-strength must be between 10 and 14");
        }
    }

    public int getBcryptStrength() {
        return bcryptStrength;
    }

    public void setBcryptStrength(int bcryptStrength) {
        this.bcryptStrength = bcryptStrength;
    }
}
