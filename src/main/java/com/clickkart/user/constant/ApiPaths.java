// src/main/java/com/clickkart/user/constant/ApiPaths.java
package com.clickkart.user.constant;

/**
 * Single source of truth for this service's route strings.
 *
 * <p>Every self-service route is rooted at {@link #ME} rather than taking a user id path
 * variable. That is deliberate and is the service's primary defence against horizontal
 * privilege escalation: with no id in the URL there is no id to tamper with, so
 * "read someone else's address book" is not a request this API can express. The subject of the
 * request is always the JWT's own subject. The only id-bearing routes are the admin ones, which
 * are separately guarded by {@code @PreAuthorize("hasRole('ADMIN')")}.
 */
public final class ApiPaths {

    private ApiPaths() {}

    public static final String BASE = "/api/v1/users";

    public static final String ME = BASE + "/me";
    public static final String ME_PREFERENCES = ME + "/preferences";
    public static final String ME_ADDRESSES = ME + "/addresses";
    public static final String ME_ADDRESS_BY_ID = ME_ADDRESSES + "/{addressId}";
    public static final String ME_ADDRESS_DEFAULT = ME_ADDRESS_BY_ID + "/default";

    /** Seller's own business profile. Writes additionally require ROLE_SELLER. */
    public static final String ME_SELLER = ME + "/seller";

    /** Admin-only. {@code userPublicId} is Auth Service's {@code publicId}, i.e. the JWT subject. */
    public static final String ADMIN_USERS = BASE;
    public static final String ADMIN_USER_BY_PUBLIC_ID = BASE + "/{userPublicId}";
    public static final String ADMIN_SELLERS = BASE + "/sellers";
    public static final String ADMIN_SELLER_VERIFICATION = BASE + "/{userPublicId}/seller/verification";

    /** This service's own tamper-evident activity log. ADMIN only - it names every actor who touched a profile. */
    public static final String ADMIN_AUDIT = BASE + "/audit";
    public static final String ADMIN_AUDIT_VERIFY = ADMIN_AUDIT + "/verification";

    /**
     * Service-to-service surface. Authenticated by a shared secret rather than a customer JWT
     * (see {@code InternalApiKeyFilter}) and deliberately given no route in the Gateway, so it is
     * reachable only from inside the cluster network. Kept under a separate root rather than mixed
     * into {@link #BASE} so "which endpoints are not customer-facing" is answerable by path alone.
     */
    public static final String INTERNAL_BASE = "/internal/v1/users";
    public static final String INTERNAL_WILDCARD = "/internal/**";
    public static final String INTERNAL_PROFILE = INTERNAL_BASE + "/{userPublicId}";
    public static final String INTERNAL_PROFILES_LOOKUP = INTERNAL_BASE + "/lookup";
    public static final String INTERNAL_ADDRESS = INTERNAL_BASE + "/{userPublicId}/addresses/{addressId}";
    public static final String INTERNAL_DEFAULT_ADDRESS = INTERNAL_BASE + "/{userPublicId}/addresses/default";
    public static final String INTERNAL_SELLER = INTERNAL_BASE + "/{userPublicId}/seller";

    public static final String ACTUATOR_HEALTH = "/actuator/health";
    public static final String ACTUATOR_HEALTH_WILDCARD = "/actuator/health/**";
    public static final String ACTUATOR_PROMETHEUS = "/actuator/prometheus";
    public static final String SWAGGER_UI = "/swagger-ui.html";
    public static final String SWAGGER_UI_WILDCARD = "/swagger-ui/**";
    public static final String API_DOCS_WILDCARD = "/v3/api-docs/**";
}
