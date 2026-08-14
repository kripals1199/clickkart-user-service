// src/main/java/com/clickkart/user/serviceImpl/SellerProfileServiceImpl.java
package com.clickkart.user.serviceImpl;

import com.clickkart.user.constant.LoggerNames;
import com.clickkart.user.dto.request.SellerVerificationDecisionRequest;
import com.clickkart.user.dto.request.UpsertSellerProfileRequest;
import com.clickkart.user.dto.response.SellerProfileResponse;
import com.clickkart.user.entity.SellerProfileEntity;
import com.clickkart.user.entity.UserProfileEntity;
import com.clickkart.user.enums.SellerVerificationStatus;
import com.clickkart.user.enums.UserAuditAction;
import com.clickkart.user.exception.AddressNotFoundException;
import com.clickkart.user.exception.DuplicateGstinException;
import com.clickkart.user.exception.SellerProfileNotFoundException;
import com.clickkart.user.repository.AddressRepository;
import com.clickkart.user.repository.SellerProfileRepository;
import com.clickkart.user.service.AuditTrailService;
import com.clickkart.user.service.SellerProfileService;
import com.clickkart.user.service.UserProfileService;
import com.clickkart.user.web.RequestMetadata;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j(topic = LoggerNames.SECURITY)
@Service
@RequiredArgsConstructor
public class SellerProfileServiceImpl implements SellerProfileService {

    private final SellerProfileRepository sellerProfileRepository;
    private final AddressRepository addressRepository;
    private final UserProfileService userProfileService;
    private final AuditTrailService auditTrailService;

    @Override
    @Transactional(readOnly = true)
    public SellerProfileResponse getOwnSellerProfile(String userPublicId) {
        return SellerProfileResponse.from(requireSellerProfile(userPublicId));
    }

    @Override
    @Transactional
    public SellerProfileResponse upsertOwnSellerProfile(
            String userPublicId,
            UpsertSellerProfileRequest request,
            String correlationId,
            RequestMetadata requestMetadata) {

        String gstin = request.gstin().trim().toUpperCase();
        // Checked before writing so the caller gets a clear 409 rather than a constraint violation
        // surfacing as a 500. The unique index is still the real guarantee under concurrency.
        sellerProfileRepository
                .findByGstin(gstin)
                .filter(existing -> !existing.isOwnedBy(userPublicId))
                .ifPresent(existing -> {
                    log.warn("SELLER_GSTIN_CONFLICT userPublicId={} correlationId={}", userPublicId, correlationId);
                    throw new DuplicateGstinException();
                });

        Long pickupAddressId = resolvePickupAddress(userPublicId, request.pickupAddressId());

        Optional<SellerProfileEntity> existing = sellerProfileRepository.findByProfileUserPublicId(userPublicId);
        SellerProfileEntity seller;
        UserAuditAction action;
        boolean identityChanged;

        if (existing.isPresent()) {
            seller = existing.get();
            identityChanged = seller.update(
                    request.businessName().trim(), gstin, trimToNull(request.supportEmail()),
                    trimToNull(request.supportPhone()), pickupAddressId);
            action = UserAuditAction.SELLER_PROFILE_UPDATED;
        } else {
            UserProfileEntity profile =
                    userProfileService.getWritableProfile(userPublicId, correlationId, requestMetadata);
            seller = SellerProfileEntity.createFor(profile);
            seller.update(
                    request.businessName().trim(), gstin, trimToNull(request.supportEmail()),
                    trimToNull(request.supportPhone()), pickupAddressId);
            seller = sellerProfileRepository.saveAndFlush(seller);
            identityChanged = false;
            action = UserAuditAction.SELLER_PROFILE_CREATED;
        }

        // Business name and GSTIN are the identity an operator verified, so they belong in the
        // trail; that is the record of what was checked, unlike a customer's personal details.
        auditTrailService.record(
                correlationId, userPublicId, action, requestMetadata,
                "businessName=" + seller.getBusinessName() + " gstin=" + seller.getGstin()
                        + " status=" + seller.getVerificationStatus());

        if (identityChanged) {
            log.warn("SELLER_REVERIFICATION_REQUIRED userPublicId={} correlationId={} - identity fields changed",
                    userPublicId, correlationId);
            auditTrailService.record(
                    correlationId, userPublicId, UserAuditAction.SELLER_VERIFICATION_RESET, requestMetadata,
                    "identity changed - verification reset to PENDING");
        }
        return SellerProfileResponse.from(seller);
    }

    @Override
    @Transactional
    public SellerProfileResponse decideVerification(
            String userPublicId,
            SellerVerificationDecisionRequest request,
            String actorPublicId,
            String correlationId,
            RequestMetadata requestMetadata) {

        if (request.status() == SellerVerificationStatus.REJECTED
                && (request.note() == null || request.note().isBlank())) {
            // A rejection with no reason leaves the seller unable to fix anything and support
            // unable to explain it, so it is refused rather than silently accepted.
            throw new IllegalArgumentException("A note is required when rejecting a seller");
        }

        SellerProfileEntity seller = requireSellerProfile(userPublicId);
        SellerVerificationStatus previous = seller.getVerificationStatus();
        seller.decideVerification(request.status(), trimToNull(request.note()));

        // The actor is the ADMIN who decided, not the seller - the trail must answer "who approved
        // this business", which is the whole point of auditing a verification.
        auditTrailService.record(
                correlationId, actorPublicId, UserAuditAction.SELLER_VERIFICATION_DECIDED, requestMetadata,
                "seller=" + userPublicId + " from=" + previous + " to=" + request.status());
        return SellerProfileResponse.from(seller);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<SellerProfileResponse> browseSellers(SellerVerificationStatus status, Pageable pageable) {
        Page<SellerProfileEntity> page = status == null
                ? sellerProfileRepository.findAll(pageable)
                : sellerProfileRepository.findByVerificationStatus(status, pageable);
        return page.map(SellerProfileResponse::from);
    }

    @Override
    @Transactional(readOnly = true)
    public SellerProfileResponse getSellerProfile(String userPublicId) {
        return SellerProfileResponse.from(requireSellerProfile(userPublicId));
    }

    /**
     * A seller may only nominate an address from their own address book. Without this check a
     * seller could point pickup at any address id in the system, turning a field they control into
     * a way to read back another customer's address through their own seller profile.
     */
    private Long resolvePickupAddress(String userPublicId, Long pickupAddressId) {
        if (pickupAddressId == null) {
            return null;
        }
        return addressRepository
                .findByIdAndProfileUserPublicIdAndDeletedFalse(pickupAddressId, userPublicId)
                .map(address -> address.getId())
                .orElseThrow(() -> new AddressNotFoundException(pickupAddressId));
    }

    private SellerProfileEntity requireSellerProfile(String userPublicId) {
        return sellerProfileRepository
                .findByProfileUserPublicId(userPublicId)
                .orElseThrow(() -> new SellerProfileNotFoundException(userPublicId));
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
