// src/test/java/com/clickkart/user/serviceImpl/SellerProfileServiceImplTest.java
package com.clickkart.user.serviceImpl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.clickkart.user.dto.request.SellerVerificationDecisionRequest;
import com.clickkart.user.dto.request.UpsertSellerProfileRequest;
import com.clickkart.user.dto.response.SellerProfileResponse;
import com.clickkart.user.entity.AddressEntity;
import com.clickkart.user.entity.SellerProfileEntity;
import com.clickkart.user.entity.UserProfileEntity;
import com.clickkart.user.enums.AddressLabel;
import com.clickkart.user.enums.SellerVerificationStatus;
import com.clickkart.user.enums.UserAuditAction;
import com.clickkart.user.exception.AddressNotFoundException;
import com.clickkart.user.exception.DuplicateGstinException;
import com.clickkart.user.exception.SellerProfileNotFoundException;
import com.clickkart.user.repository.AddressRepository;
import com.clickkart.user.repository.SellerProfileRepository;
import com.clickkart.user.service.AuditTrailService;
import com.clickkart.user.service.UserProfileService;
import com.clickkart.user.web.RequestMetadata;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SellerProfileServiceImplTest {

    private static final String SELLER = "usr_seller";
    private static final String ADMIN = "usr_admin";
    private static final String CORRELATION_ID = "corr-1";
    private static final String GSTIN = "29ABCDE1234F1Z5";
    private static final RequestMetadata METADATA = new RequestMetadata("203.0.113.7", "junit");

    @Mock private SellerProfileRepository sellerProfileRepository;
    @Mock private AddressRepository addressRepository;
    @Mock private UserProfileService userProfileService;
    @Mock private AuditTrailService auditTrailService;

    private SellerProfileServiceImpl service;
    private UserProfileEntity profile;

    @BeforeEach
    void setUp() {
        service = new SellerProfileServiceImpl(
                sellerProfileRepository, addressRepository, userProfileService, auditTrailService);
        profile = UserProfileEntity.createFor(SELLER, "en", "INR");
        when(userProfileService.getOrCreateProfile(eq(SELLER), any(), any())).thenReturn(profile);
        when(sellerProfileRepository.saveAndFlush(any(SellerProfileEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    private UpsertSellerProfileRequest request(String businessName, String gstin, Long pickupAddressId) {
        return new UpsertSellerProfileRequest(businessName, gstin, "help@shop.example", "9845550100", pickupAddressId);
    }

    private SellerProfileEntity existingSeller(SellerVerificationStatus status) {
        SellerProfileEntity seller = SellerProfileEntity.createFor(profile);
        ReflectionTestUtils.setField(seller, "id", 5L);
        seller.update("Menon Traders", GSTIN, "help@shop.example", "9845550100", null);
        seller.decideVerification(status, null);
        return seller;
    }

    @Test
    void aNewSellerProfileStartsPendingRatherThanVerified() {
        when(sellerProfileRepository.findByProfileUserPublicId(SELLER)).thenReturn(Optional.empty());

        SellerProfileResponse created =
                service.upsertOwnSellerProfile(SELLER, request("Menon Traders", GSTIN, null), CORRELATION_ID, METADATA);

        // A self-declared VERIFIED would make the operator check decorative.
        assertThat(created.verificationStatus()).isEqualTo(SellerVerificationStatus.PENDING);
        verify(auditTrailService)
                .record(eq(CORRELATION_ID), eq(SELLER), eq(UserAuditAction.SELLER_PROFILE_CREATED), any(), any());
    }

    @Test
    void theGstinIsUppercasedAndTrimmedBeforeStorage() {
        when(sellerProfileRepository.findByProfileUserPublicId(SELLER)).thenReturn(Optional.empty());

        SellerProfileResponse created = service.upsertOwnSellerProfile(
                SELLER, request("  Menon Traders  ", "  29abcde1234f1z5  ", null), CORRELATION_ID, METADATA);

        assertThat(created.gstin()).isEqualTo(GSTIN);
        assertThat(created.businessName()).isEqualTo("Menon Traders");
    }

    @Test
    void changingTheGstinWithdrawsAnExistingVerification() {
        // Otherwise a seller passes verification with one legitimate registration and then swaps in
        // another, carrying the verified badge onto a business nobody checked.
        SellerProfileEntity seller = existingSeller(SellerVerificationStatus.VERIFIED);
        when(sellerProfileRepository.findByProfileUserPublicId(SELLER)).thenReturn(Optional.of(seller));

        SellerProfileResponse updated = service.upsertOwnSellerProfile(
                SELLER, request("Menon Traders", "27ZZZZZ9999Z1Z9", null), CORRELATION_ID, METADATA);

        assertThat(updated.verificationStatus()).isEqualTo(SellerVerificationStatus.PENDING);
        verify(auditTrailService)
                .record(any(), any(), eq(UserAuditAction.SELLER_VERIFICATION_RESET), any(), any());
    }

    @Test
    void changingOnlyContactDetailsKeepsTheVerification() {
        SellerProfileEntity seller = existingSeller(SellerVerificationStatus.VERIFIED);
        when(sellerProfileRepository.findByProfileUserPublicId(SELLER)).thenReturn(Optional.of(seller));

        SellerProfileResponse updated = service.upsertOwnSellerProfile(
                SELLER,
                new UpsertSellerProfileRequest("Menon Traders", GSTIN, "new@shop.example", "9845550111", null),
                CORRELATION_ID,
                METADATA);

        // Contact details are not what was verified, so re-checking would be pointless friction.
        assertThat(updated.verificationStatus()).isEqualTo(SellerVerificationStatus.VERIFIED);
        verify(auditTrailService, never())
                .record(any(), any(), eq(UserAuditAction.SELLER_VERIFICATION_RESET), any(), any());
    }

    @Test
    void aGstinAlreadyClaimedByAnotherSellerIsRejected() {
        UserProfileEntity otherProfile = UserProfileEntity.createFor("usr_other", "en", "INR");
        SellerProfileEntity other = SellerProfileEntity.createFor(otherProfile);
        other.update("Other Traders", GSTIN, null, null, null);
        when(sellerProfileRepository.findByGstin(GSTIN)).thenReturn(Optional.of(other));

        assertThatThrownBy(() ->
                        service.upsertOwnSellerProfile(SELLER, request("Menon Traders", GSTIN, null), CORRELATION_ID, METADATA))
                .isInstanceOf(DuplicateGstinException.class);
        verify(sellerProfileRepository, never()).saveAndFlush(any());
    }

    @Test
    void reusingYourOwnGstinIsNotTreatedAsADuplicate() {
        SellerProfileEntity seller = existingSeller(SellerVerificationStatus.PENDING);
        when(sellerProfileRepository.findByGstin(GSTIN)).thenReturn(Optional.of(seller));
        when(sellerProfileRepository.findByProfileUserPublicId(SELLER)).thenReturn(Optional.of(seller));

        SellerProfileResponse updated = service.upsertOwnSellerProfile(
                SELLER, request("Menon Traders Ltd", GSTIN, null), CORRELATION_ID, METADATA);

        assertThat(updated.businessName()).isEqualTo("Menon Traders Ltd");
    }

    @Test
    void aPickupAddressBelongingToSomeoneElseIsRefused() {
        // The seller controls this field, so an unchecked id would let them read back another
        // customer's address through their own seller profile.
        when(sellerProfileRepository.findByProfileUserPublicId(SELLER)).thenReturn(Optional.empty());
        when(addressRepository.findByIdAndProfileUserPublicIdAndDeletedFalse(42L, SELLER))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                        service.upsertOwnSellerProfile(SELLER, request("Menon Traders", GSTIN, 42L), CORRELATION_ID, METADATA))
                .isInstanceOf(AddressNotFoundException.class);
    }

    @Test
    void aPickupAddressTheSellerOwnsIsAccepted() {
        AddressEntity own = AddressEntity.createFor(profile);
        ReflectionTestUtils.setField(own, "id", 42L);
        own.update(AddressLabel.WORK, "Asha", "9845550100", "Line", null, null,
                "Bengaluru", "Karnataka", "560001", "India");
        when(sellerProfileRepository.findByProfileUserPublicId(SELLER)).thenReturn(Optional.empty());
        when(addressRepository.findByIdAndProfileUserPublicIdAndDeletedFalse(42L, SELLER))
                .thenReturn(Optional.of(own));

        SellerProfileResponse created =
                service.upsertOwnSellerProfile(SELLER, request("Menon Traders", GSTIN, 42L), CORRELATION_ID, METADATA);

        assertThat(created.pickupAddressId()).isEqualTo(42L);
    }

    @Test
    void rejectingASellerWithoutAReasonIsRefused() {
        SellerProfileEntity seller = existingSeller(SellerVerificationStatus.PENDING);
        when(sellerProfileRepository.findByProfileUserPublicId(SELLER)).thenReturn(Optional.of(seller));

        assertThatThrownBy(() -> service.decideVerification(
                        SELLER,
                        new SellerVerificationDecisionRequest(SellerVerificationStatus.REJECTED, "  "),
                        ADMIN, CORRELATION_ID, METADATA))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(seller.getVerificationStatus()).isEqualTo(SellerVerificationStatus.PENDING);
    }

    @Test
    void theVerificationDecisionIsAuditedAgainstTheAdminNotTheSeller() {
        SellerProfileEntity seller = existingSeller(SellerVerificationStatus.PENDING);
        when(sellerProfileRepository.findByProfileUserPublicId(SELLER)).thenReturn(Optional.of(seller));

        SellerProfileResponse decided = service.decideVerification(
                SELLER,
                new SellerVerificationDecisionRequest(SellerVerificationStatus.VERIFIED, "GSTIN confirmed"),
                ADMIN, CORRELATION_ID, METADATA);

        assertThat(decided.verificationStatus()).isEqualTo(SellerVerificationStatus.VERIFIED);
        assertThat(decided.verificationDecidedAt()).isNotNull();
        // The trail must answer "who approved this business" - that is the point of auditing it.
        verify(auditTrailService)
                .record(eq(CORRELATION_ID), eq(ADMIN), eq(UserAuditAction.SELLER_VERIFICATION_DECIDED), any(), any());
    }

    @Test
    void aUserWithNoSellerProfileIsReportedAsNotFound() {
        when(sellerProfileRepository.findByProfileUserPublicId("usr_customer")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getSellerProfile("usr_customer"))
                .isInstanceOf(SellerProfileNotFoundException.class);
    }
}
