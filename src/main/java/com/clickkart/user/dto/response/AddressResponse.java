// src/main/java/com/clickkart/user/dto/response/AddressResponse.java
package com.clickkart.user.dto.response;

import com.clickkart.user.entity.AddressEntity;
import com.clickkart.user.enums.AddressLabel;
import java.time.Instant;

/**
 * The address id <em>is</em> exposed, unlike the profile's - clients need it to address the
 * per-address routes. That is safe because every one of those routes resolves the id within the
 * caller's own profile, so knowing another customer's id grants nothing (see {@code
 * AddressRepository.findByIdAndProfileUserPublicIdAndDeletedFalse}).
 */
public record AddressResponse(
        Long id,
        AddressLabel label,
        String recipientName,
        String contactNumber,
        String line1,
        String line2,
        String landmark,
        String city,
        String state,
        String postalCode,
        String country,
        boolean defaultAddress,
        Instant createdDate,
        Instant updatedDate) {

    public static AddressResponse from(AddressEntity entity) {
        return new AddressResponse(
                entity.getId(),
                entity.getLabel(),
                entity.getRecipientName(),
                entity.getContactNumber(),
                entity.getLine1(),
                entity.getLine2(),
                entity.getLandmark(),
                entity.getCity(),
                entity.getState(),
                entity.getPostalCode(),
                entity.getCountry(),
                entity.isDefaultAddress(),
                entity.getCreatedDate(),
                entity.getUpdatedDate());
    }
}
