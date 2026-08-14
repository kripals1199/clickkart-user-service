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

    /** Admin-only. {@code userPublicId} is Auth Service's {@code publicId}, i.e. the JWT subject. */
    public static final String ADMIN_USERS = BASE;
    public static final String ADMIN_USER_BY_PUBLIC_ID = BASE + "/{userPublicId}";

    public static final String ACTUATOR_HEALTH = "/actuator/health";
    public static final String ACTUATOR_HEALTH_WILDCARD = "/actuator/health/**";
    public static final String ACTUATOR_PROMETHEUS = "/actuator/prometheus";
    public static final String SWAGGER_UI = "/swagger-ui.html";
    public static final String SWAGGER_UI_WILDCARD = "/swagger-ui/**";
    public static final String API_DOCS_WILDCARD = "/v3/api-docs/**";
}
