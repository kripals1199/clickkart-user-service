// src/main/java/com/clickkart/user/dto/request/UpdateProfileRequest.java
package com.clickkart.user.dto.request;

import com.clickkart.user.enums.Gender;
import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * Full replacement of the editable profile fields (PUT semantics) - an omitted field is cleared,
 * not left alone. Every field is optional because a customer may legitimately want to remove any
 * of them; there is no minimum profile this service insists on.
 *
 * <p>Notably absent: email, mobile number and roles. Those live in Auth Service and are changed
 * through its own verification flows. Accepting them here would let a customer edit their way to
 * a different identity, or to a role they were never granted.
 */
public record UpdateProfileRequest(
        @Size(max = 60, message = "must be at most 60 characters") String firstName,
        @Size(max = 60, message = "must be at most 60 characters") String lastName,
        @Size(max = 80, message = "must be at most 80 characters") String displayName,
        @Past(message = "must be a date in the past") @JsonFormat(pattern = "yyyy-MM-dd") LocalDate dateOfBirth,
        Gender gender,
        @Size(max = 500, message = "must be at most 500 characters")
                @Pattern(
                        regexp = "^$|^https://.*",
                        message = "must be an https URL")
                String avatarUrl) {}
