// src/main/java/com/clickkart/user/dto/response/UserProfileResponse.java
package com.clickkart.user.dto.response;

import com.clickkart.user.entity.UserProfileEntity;
import com.clickkart.user.enums.Gender;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Deliberately does not expose the database primary key. {@code userPublicId} is the identifier
 * every other service already knows this customer by (it is the JWT subject), so publishing a
 * second, internal, sequential id would leak row counts and invite clients to couple to it.
 */
public record UserProfileResponse(
        String userPublicId,
        String firstName,
        String lastName,
        String displayName,
        LocalDate dateOfBirth,
        Gender gender,
        String avatarUrl,
        boolean marketingEmailOptIn,
        boolean marketingSmsOptIn,
        String preferredLanguage,
        String preferredCurrency,
        Instant createdDate,
        Instant updatedDate,
        /**
         * When personal data was erased, or null while the account is live. Present so a client can
         * tell "erased" from "never filled in" - both otherwise look like a profile of nulls.
         */
        Instant erasedAt) {

    public static UserProfileResponse from(UserProfileEntity entity) {
        return new UserProfileResponse(
                entity.getUserPublicId(),
                entity.getFirstName(),
                entity.getLastName(),
                entity.getDisplayName(),
                entity.getDateOfBirth(),
                entity.getGender(),
                entity.getAvatarUrl(),
                entity.isMarketingEmailOptIn(),
                entity.isMarketingSmsOptIn(),
                entity.getPreferredLanguage(),
                entity.getPreferredCurrency(),
                entity.getCreatedDate(),
                entity.getUpdatedDate(),
                entity.getErasedAt());
    }
}
