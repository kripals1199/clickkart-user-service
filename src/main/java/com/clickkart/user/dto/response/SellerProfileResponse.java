// src/main/java/com/clickkart/user/dto/response/SellerProfileResponse.java
package com.clickkart.user.dto.response;

import com.clickkart.user.entity.SellerProfileEntity;
import com.clickkart.user.enums.SellerVerificationStatus;
import java.time.Instant;

public record SellerProfileResponse(
        String userPublicId,
        String businessName,
        String gstin,
        String supportEmail,
        String supportPhone,
        Long pickupAddressId,
        SellerVerificationStatus verificationStatus,
        String verificationNote,
        Instant verificationDecidedAt,
        Instant createdDate,
        Instant updatedDate) {

    public static SellerProfileResponse from(SellerProfileEntity entity) {
        return new SellerProfileResponse(
                entity.getProfile().getUserPublicId(),
                entity.getBusinessName(),
                entity.getGstin(),
                entity.getSupportEmail(),
                entity.getSupportPhone(),
                entity.getPickupAddressId(),
                entity.getVerificationStatus(),
                entity.getVerificationNote(),
                entity.getVerificationDecidedAt(),
                entity.getCreatedDate(),
                entity.getUpdatedDate());
    }
}
