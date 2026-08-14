// src/main/java/com/clickkart/user/dto/request/AddressRequest.java
package com.clickkart.user.dto.request;

import com.clickkart.user.enums.AddressLabel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Used for both create and update - the fields a customer supplies are identical in each case, and
 * two structurally identical records would only invite them to drift apart.
 *
 * <p>Validation is India-shaped, matching the platform's existing choices (Auth Service validates
 * Indian mobile numbers; SMS goes through MSG91's DLT-registered templates): a 6-digit PIN code and
 * a 10-digit mobile starting 6-9. {@code makeDefault} is nullable and treated as false when absent -
 * unlike consent flags, defaulting this one is harmless, and the first address a customer saves is
 * promoted to default automatically regardless.
 */
public record AddressRequest(
        @NotNull(message = "must be specified") AddressLabel label,
        @NotBlank(message = "must not be blank") @Size(max = 120, message = "must be at most 120 characters")
                String recipientName,
        @NotBlank(message = "must not be blank")
                @Pattern(regexp = "^[6-9]\\d{9}$", message = "must be a valid 10-digit Indian mobile number")
                String contactNumber,
        @NotBlank(message = "must not be blank") @Size(max = 200, message = "must be at most 200 characters")
                String line1,
        @Size(max = 200, message = "must be at most 200 characters") String line2,
        @Size(max = 150, message = "must be at most 150 characters") String landmark,
        @NotBlank(message = "must not be blank") @Size(max = 100, message = "must be at most 100 characters")
                String city,
        @NotBlank(message = "must not be blank") @Size(max = 100, message = "must be at most 100 characters")
                String state,
        @NotBlank(message = "must not be blank")
                @Pattern(regexp = "^[1-9]\\d{5}$", message = "must be a valid 6-digit PIN code")
                String postalCode,
        @NotBlank(message = "must not be blank") @Size(max = 60, message = "must be at most 60 characters")
                String country,
        Boolean makeDefault) {

    /** Null-safe view of {@link #makeDefault} for the service layer. */
    public boolean shouldMakeDefault() {
        return Boolean.TRUE.equals(makeDefault);
    }
}
