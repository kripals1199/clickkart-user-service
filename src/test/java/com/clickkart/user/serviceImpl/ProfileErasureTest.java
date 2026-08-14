// src/test/java/com/clickkart/user/serviceImpl/ProfileErasureTest.java
package com.clickkart.user.serviceImpl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.clickkart.user.config.UserProperties;
import com.clickkart.user.dto.request.UpdatePreferencesRequest;
import com.clickkart.user.dto.request.UpdateProfileRequest;
import com.clickkart.user.entity.AddressEntity;
import com.clickkart.user.entity.SellerProfileEntity;
import com.clickkart.user.entity.UserProfileEntity;
import com.clickkart.user.enums.AddressLabel;
import com.clickkart.user.enums.Gender;
import com.clickkart.user.enums.SellerVerificationStatus;
import com.clickkart.user.enums.UserAuditAction;
import com.clickkart.user.exception.ErasureBlockedException;
import com.clickkart.user.exception.ProfileErasedException;
import com.clickkart.user.exception.ProfileNotFoundException;
import com.clickkart.user.repository.AddressRepository;
import com.clickkart.user.repository.SellerProfileRepository;
import com.clickkart.user.repository.UserProfileRepository;
import com.clickkart.user.service.AuditTrailService;
import com.clickkart.user.web.RequestMetadata;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Erasure is irreversible and legally meaningful, so the properties worth pinning are: nothing
 * recoverable is left behind, it cannot be silently undone, and it is provably recorded.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProfileErasureTest {

    private static final String USER_ID = "usr_owner";
    private static final String ADMIN = "usr_admin";
    private static final String CORRELATION_ID = "corr-1";
    private static final RequestMetadata METADATA = new RequestMetadata("203.0.113.7", "junit");

    @Mock private UserProfileRepository userProfileRepository;
    @Mock private UserProfileCreator userProfileCreator;
    @Mock private AddressRepository addressRepository;
    @Mock private SellerProfileRepository sellerProfileRepository;
    @Mock private AuditTrailService auditTrailService;

    private UserProfileServiceImpl service;
    private UserProfileEntity profile;

    @BeforeEach
    void setUp() {
        UserProperties properties = new UserProperties();
        properties.setDefaultLanguage("en");
        properties.setDefaultCurrency("INR");
        service = new UserProfileServiceImpl(
                userProfileRepository, userProfileCreator, addressRepository, sellerProfileRepository,
                auditTrailService, properties);

        profile = UserProfileEntity.createFor(USER_ID, "en", "INR");
        profile.updateProfile("Asha", "Menon", "asha", LocalDate.of(1995, 4, 12), Gender.FEMALE, "https://x/a.png");
        profile.updatePreferences(true, true, "en-IN", "INR");
        when(userProfileRepository.findByUserPublicId(USER_ID)).thenReturn(Optional.of(profile));
        when(addressRepository.findByProfileUserPublicIdAndDeletedFalseOrderByDefaultAddressDescIdAsc(USER_ID))
                .thenReturn(List.of());
        when(sellerProfileRepository.findByProfileUserPublicId(USER_ID)).thenReturn(Optional.empty());
    }

    private AddressEntity address(long id) {
        AddressEntity entity = AddressEntity.createFor(profile);
        ReflectionTestUtils.setField(entity, "id", id);
        entity.update(AddressLabel.HOME, "Asha Menon", "9845550100", "12 MG Road", "Flat 4", "Near park",
                "Bengaluru", "Karnataka", "560001", "India");
        entity.markDefault(true);
        return entity;
    }

    @Test
    void erasureClearsEveryPersonalFieldAndWithdrawsConsent() {
        service.eraseOwnProfile(USER_ID, CORRELATION_ID, METADATA);

        assertThat(profile.getFirstName()).isNull();
        assertThat(profile.getLastName()).isNull();
        assertThat(profile.getDisplayName()).isNull();
        assertThat(profile.getDateOfBirth()).isNull();
        assertThat(profile.getGender()).isNull();
        assertThat(profile.getAvatarUrl()).isNull();
        // An erased account must not stay on a marketing list - the most concrete expectation
        // someone asking for erasure has.
        assertThat(profile.isMarketingEmailOptIn()).isFalse();
        assertThat(profile.isMarketingSmsOptIn()).isFalse();
        assertThat(profile.getErasedAt()).isNotNull();
        // The row itself survives: the append-only audit chain references this publicId.
        assertThat(profile.getUserPublicId()).isEqualTo(USER_ID);
    }

    @Test
    void everySavedAddressIsScrubbedNotMerelyFlagged() {
        AddressEntity one = address(1L);
        AddressEntity two = address(2L);
        when(addressRepository.findByProfileUserPublicIdAndDeletedFalseOrderByDefaultAddressDescIdAsc(USER_ID))
                .thenReturn(List.of(one, two));

        service.eraseOwnProfile(USER_ID, CORRELATION_ID, METADATA);

        for (AddressEntity address : List.of(one, two)) {
            assertThat(address.isDeleted()).isTrue();
            assertThat(address.isDefaultAddress()).isFalse();
            // Soft-delete alone would leave the full address recoverable in the table.
            assertThat(address.getRecipientName()).doesNotContain("Asha");
            assertThat(address.getContactNumber()).doesNotContain("9845550100");
            assertThat(address.getLine1()).doesNotContain("MG Road");
            assertThat(address.getPostalCode()).doesNotContain("560001");
            assertThat(address.getLine2()).isNull();
            assertThat(address.getLandmark()).isNull();
        }
    }

    @Test
    void aSellerAccountCannotEraseItselfWithoutSupport() {
        // A GSTIN and its trading history carry statutory retention; erasing them would also orphan
        // whatever the seller has listed. Not a call a self-service endpoint should make.
        when(sellerProfileRepository.findByProfileUserPublicId(USER_ID))
                .thenReturn(Optional.of(SellerProfileEntity.createFor(profile)));

        assertThatThrownBy(() -> service.eraseOwnProfile(USER_ID, CORRELATION_ID, METADATA))
                .isInstanceOf(ErasureBlockedException.class)
                .hasMessageContaining("support");
        assertThat(profile.isErased()).isFalse();
        verify(auditTrailService, never()).record(any(), any(), eq(UserAuditAction.PROFILE_ERASED), any(), any());
    }

    @Test
    void anAdminCanEraseASellerAndTheBusinessIdentityIsScrubbedToo() {
        SellerProfileEntity seller = SellerProfileEntity.createFor(profile);
        ReflectionTestUtils.setField(seller, "id", 9L);
        seller.update("Menon Traders", "29ABCDE1234F1Z5", "help@shop.example", "9845550100", 1L);
        seller.decideVerification(SellerVerificationStatus.VERIFIED, "ok");
        when(sellerProfileRepository.findByProfileUserPublicId(USER_ID)).thenReturn(Optional.of(seller));

        service.eraseProfile(USER_ID, ADMIN, CORRELATION_ID, METADATA);

        assertThat(seller.getBusinessName()).doesNotContain("Menon");
        assertThat(seller.getGstin()).doesNotContain("ABCDE");
        // Unique per row - a fixed marker would collide with the next erased seller and fail the
        // erasure at exactly the moment it must not.
        assertThat(seller.getGstin()).isEqualTo("ERASED-9");
        assertThat(seller.getSupportEmail()).isNull();
        assertThat(seller.getPickupAddressId()).isNull();
        // Must not read as still cleared to trade.
        assertThat(seller.getVerificationStatus()).isEqualTo(SellerVerificationStatus.REJECTED);
        assertThat(profile.isErased()).isTrue();
    }

    @Test
    void erasureIsRecordedWithCountsRatherThanContent() {
        when(addressRepository.findByProfileUserPublicIdAndDeletedFalseOrderByDefaultAddressDescIdAsc(USER_ID))
                .thenReturn(List.of(address(1L), address(2L)));

        service.eraseOwnProfile(USER_ID, CORRELATION_ID, METADATA);

        ArgumentCaptor<String> details = ArgumentCaptor.forClass(String.class);
        verify(auditTrailService)
                .record(eq(CORRELATION_ID), eq(USER_ID), eq(UserAuditAction.PROFILE_ERASED), any(), details.capture());
        assertThat(details.getValue()).contains("addressesScrubbed=2").contains("selfService=true");
        // An erasure record that preserved what was erased would defeat itself.
        assertThat(details.getValue()).doesNotContain("Asha").doesNotContain("MG Road");
    }

    @Test
    void anAdminErasureIsAttributedToTheOperatorWithTheSubjectNamed() {
        service.eraseProfile(USER_ID, ADMIN, CORRELATION_ID, METADATA);

        ArgumentCaptor<String> details = ArgumentCaptor.forClass(String.class);
        verify(auditTrailService)
                .record(eq(CORRELATION_ID), eq(ADMIN), eq(UserAuditAction.PROFILE_ERASED), any(), details.capture());
        assertThat(details.getValue()).contains("subject=" + USER_ID).contains("selfService=false");
    }

    @Test
    void erasingTwiceIsNotAnErrorAndDoesNotRecordASecondEvent() {
        service.eraseOwnProfile(USER_ID, CORRELATION_ID, METADATA);
        service.eraseOwnProfile(USER_ID, CORRELATION_ID, METADATA);

        verify(auditTrailService, org.mockito.Mockito.times(1))
                .record(any(), any(), eq(UserAuditAction.PROFILE_ERASED), any(), any());
    }

    @Test
    void everyWritePathRefusesAnErasedProfile() {
        service.eraseOwnProfile(USER_ID, CORRELATION_ID, METADATA);

        // Critically, the self-service endpoints auto-provision on first access - without this
        // guard the very next request would quietly repopulate a profile someone asked to empty.
        assertThatThrownBy(() -> service.getWritableProfile(USER_ID, CORRELATION_ID, METADATA))
                .isInstanceOf(ProfileErasedException.class);
        assertThatThrownBy(() -> service.updateOwnProfile(
                        USER_ID,
                        new UpdateProfileRequest("Back", "Again", null, null, null, null),
                        CORRELATION_ID, METADATA))
                .isInstanceOf(ProfileErasedException.class);
        assertThatThrownBy(() -> service.updateOwnPreferences(
                        USER_ID, new UpdatePreferencesRequest(true, true, "en", "INR"), CORRELATION_ID, METADATA))
                .isInstanceOf(ProfileErasedException.class);
    }

    @Test
    void readingAnErasedProfileStillWorksAndReportsWhenItHappened() {
        service.eraseOwnProfile(USER_ID, CORRELATION_ID, METADATA);

        // A 404 here would be indistinguishable from "never created", leaving a client unable to
        // explain to the customer why their data is gone.
        var response = service.getOwnProfile(USER_ID, CORRELATION_ID, METADATA);
        assertThat(response.erasedAt()).isNotNull();
        assertThat(response.firstName()).isNull();
    }

    @Test
    void erasingAProfileThatNeverExistedIsReportedAsNotFound() {
        when(userProfileRepository.findByUserPublicId("usr_ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.eraseProfile("usr_ghost", ADMIN, CORRELATION_ID, METADATA))
                .isInstanceOf(ProfileNotFoundException.class);
    }
}
