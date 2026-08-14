// src/main/java/com/clickkart/user/config/UserProperties.java
package com.clickkart.user.config;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Externalized settings for this service, bound from {@code clickkart-user-service.properties} in
 * whichever config-repository branch matches the active profile.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "user")
public class UserProperties {

    /** Shared HMAC secret. Must be the same value Auth Service signs with and the Gateway validates against. */
    private String jwtSecret;

    /**
     * Shared secret guarding {@code /internal/**}, presented by calling services as
     * {@code X-Internal-Api-Key}. Separate from {@link #jwtSecret} on purpose: that one is a
     * signature key held by three services, while this authenticates callers. Reusing one secret
     * for both would mean anything able to validate a token could also call the internal API.
     *
     * <p>Blank means no caller can authenticate - {@code InternalApiKeyFilter} refuses every
     * internal request rather than treating an unset key as a match.
     */
    private String internalApiKey;

    /** Must match Auth Service's {@code auth.revocation-key-prefix}, or logout would not be seen here. */
    private String revocationKeyPrefix = "revoked:jti:";

    /** CORS allow-list. Defence in depth - this service is independently reachable, bypassing the Gateway's own CORS config. */
    private String allowedOrigins = "http://localhost:4200";

    /**
     * Upper bound on saved addresses per customer. Not a business rule so much as a cheap guard:
     * without it, an authenticated client can grow one profile's address book without limit and
     * turn a legitimate account into a storage-exhaustion vector.
     */
    private int maxAddressesPerUser = 20;

    /** Applied to a profile at creation, when the customer has expressed no preference yet. */
    private String defaultLanguage = "en";

    private String defaultCurrency = "INR";

    /**
     * CIDR ranges whose {@code X-Forwarded-For} header is believed. Empty means trust nothing and
     * always use the socket address - see {@code ClientIpResolver}. Same shape as Auth Service's
     * {@code auth.trusted-proxy-cidrs}; Spring binds a comma-separated property to this list.
     */
    private List<String> trustedProxyCidrs = new ArrayList<>();
}
