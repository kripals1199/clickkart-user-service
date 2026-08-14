// src/main/java/com/clickkart/user/dto/request/SellerVerificationDecisionRequest.java
package com.clickkart.user.dto.request;

import com.clickkart.user.enums.SellerVerificationStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * ADMIN-only verification decision.
 *
 * <p>{@code note} is what the seller gets told when rejected, so a rejection is actionable rather
 * than a dead end. It is required for {@link SellerVerificationStatus#REJECTED} - enforced in the
 * service layer rather than by an annotation, since the requirement is conditional on the status.
 */
public record SellerVerificationDecisionRequest(
        @NotNull(message = "must be specified") SellerVerificationStatus status,
        @Size(max = 500, message = "must be at most 500 characters") String note) {}
