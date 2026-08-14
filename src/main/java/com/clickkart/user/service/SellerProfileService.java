// src/main/java/com/clickkart/user/service/SellerProfileService.java
package com.clickkart.user.service;

import com.clickkart.user.dto.request.SellerVerificationDecisionRequest;
import com.clickkart.user.dto.request.UpsertSellerProfileRequest;
import com.clickkart.user.dto.response.SellerProfileResponse;
import com.clickkart.user.enums.SellerVerificationStatus;
import com.clickkart.user.web.RequestMetadata;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface SellerProfileService {

    SellerProfileResponse getOwnSellerProfile(String userPublicId);

    /** Creates on first call, updates thereafter. Caller must hold ROLE_SELLER. */
    SellerProfileResponse upsertOwnSellerProfile(
            String userPublicId,
            UpsertSellerProfileRequest request,
            String correlationId,
            RequestMetadata requestMetadata);

    /** ADMIN only. */
    SellerProfileResponse decideVerification(
            String userPublicId,
            SellerVerificationDecisionRequest request,
            String actorPublicId,
            String correlationId,
            RequestMetadata requestMetadata);

    /** ADMIN only - operator work queue, optionally filtered by status. */
    Page<SellerProfileResponse> browseSellers(SellerVerificationStatus status, Pageable pageable);

    /** Service-to-service lookup, used by Product Service to attribute and gate listings. */
    SellerProfileResponse getSellerProfile(String userPublicId);
}
