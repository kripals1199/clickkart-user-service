// src/main/java/com/clickkart/user/dto/request/UpdatePreferencesRequest.java
package com.clickkart.user.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * Separate from {@link UpdateProfileRequest} on purpose: marketing consent is a compliance-relevant
 * decision with its own audit action, and burying it in the same PUT as a display-name change would
 * make it possible to flip consent as a side effect of an unrelated edit.
 *
 * <p>Both opt-in flags are {@code @NotNull} rather than defaulting - a missing field would otherwise
 * silently read as {@code false} and revoke a consent the customer never touched.
 */
public record UpdatePreferencesRequest(
        @NotNull(message = "must be specified") Boolean marketingEmailOptIn,
        @NotNull(message = "must be specified") Boolean marketingSmsOptIn,
        @NotBlank(message = "must not be blank")
                @Pattern(regexp = "^[a-z]{2}(-[A-Z]{2})?$", message = "must be an ISO language tag such as en or en-IN")
                String preferredLanguage,
        @NotBlank(message = "must not be blank")
                @Pattern(regexp = "^[A-Z]{3}$", message = "must be a 3-letter ISO 4217 currency code")
                String preferredCurrency) {}
