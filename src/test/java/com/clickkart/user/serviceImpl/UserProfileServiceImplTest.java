// src/test/java/com/clickkart/user/serviceImpl/UserProfileServiceImplTest.java
package com.clickkart.user.serviceImpl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.clickkart.user.config.UserProperties;
import com.clickkart.user.dto.request.UpdatePreferencesRequest;
import com.clickkart.user.dto.request.UpdateProfileRequest;
import com.clickkart.user.dto.response.UserProfileResponse;
import com.clickkart.user.entity.UserProfileEntity;
import com.clickkart.user.enums.Gender;
import com.clickkart.user.enums.UserAuditAction;
import com.clickkart.user.exception.ProfileNotFoundException;
import com.clickkart.user.repository.UserProfileRepository;
import com.clickkart.user.service.AuditTrailService;
import com.clickkart.user.web.RequestMetadata;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UserProfileServiceImplTest {

    private static final String USER_ID = "usr_owner";
    private static final String CORRELATION_ID = "corr-1";
    private static final RequestMetadata METADATA = new RequestMetadata("203.0.113.7", "junit");

    @Mock private UserProfileRepository userProfileRepository;
    @Mock private UserProfileCreator userProfileCreator;
    @Mock private com.clickkart.user.repository.AddressRepository addressRepository;
    @Mock private com.clickkart.user.repository.SellerProfileRepository sellerProfileRepository;
    @Mock private AuditTrailService auditTrailService;

    private UserProfileServiceImpl service;

    @BeforeEach
    void setUp() {
        UserProperties properties = new UserProperties();
        properties.setDefaultLanguage("en");
        properties.setDefaultCurrency("INR");
        service = new UserProfileServiceImpl(
                userProfileRepository, userProfileCreator, addressRepository, sellerProfileRepository,
                auditTrailService, properties);
    }

    /** Mirrors the real flow: the creator commits the row, so the following read finds it. */
    private void creatorInsertsSuccessfully(String userPublicId) {
        UserProfileEntity created = UserProfileEntity.createFor(userPublicId, "en", "INR");
        when(userProfileRepository.findByUserPublicId(userPublicId))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(created));
    }

    @Test
    void anEmptyProfileIsCreatedOnFirstAccessWithConfiguredDefaults() {
        creatorInsertsSuccessfully(USER_ID);

        UserProfileResponse profile = service.getOwnProfile(USER_ID, CORRELATION_ID, METADATA);

        verify(userProfileCreator).createInNewTransaction(USER_ID, "en", "INR");

        assertThat(profile.userPublicId()).isEqualTo(USER_ID);
        assertThat(profile.preferredLanguage()).isEqualTo("en");
        assertThat(profile.preferredCurrency()).isEqualTo("INR");
        // Nothing is invented - every optional field stays empty until the customer fills it in.
        assertThat(profile.firstName()).isNull();
        assertThat(profile.marketingEmailOptIn()).isFalse();
        assertThat(profile.marketingSmsOptIn()).isFalse();
        verify(auditTrailService)
                .record(eq(CORRELATION_ID), eq(USER_ID), eq(UserAuditAction.PROFILE_CREATED), any(), any());
    }

    @Test
    void anExistingProfileIsReturnedWithoutCreatingAnother() {
        when(userProfileRepository.findByUserPublicId(USER_ID))
                .thenReturn(Optional.of(UserProfileEntity.createFor(USER_ID, "en", "INR")));

        service.getOwnProfile(USER_ID, CORRELATION_ID, METADATA);

        verify(userProfileCreator, never()).createInNewTransaction(any(), any(), any());
        verify(auditTrailService, never())
                .record(any(), any(), eq(UserAuditAction.PROFILE_CREATED), any(), any());
    }

    @Test
    void losingTheFirstAccessRaceReturnsTheWinnersProfileRatherThanFailing() {
        // Two concurrent first-time requests both see no row and both insert; the unique constraint
        // rejects the loser. The loser must end up with the winner's row, not a 500.
        UserProfileEntity winner = UserProfileEntity.createFor(USER_ID, "en", "INR");
        when(userProfileRepository.findByUserPublicId(USER_ID))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(winner));
        doThrow(new DataIntegrityViolationException("duplicate key user_public_id"))
                .when(userProfileCreator)
                .createInNewTransaction(eq(USER_ID), any(), any());

        UserProfileEntity resolved = service.getOrCreateProfile(USER_ID, CORRELATION_ID, METADATA);

        assertThat(resolved).isSameAs(winner);
        // The loser must not also emit a PROFILE_CREATED event - only one profile was created.
        verify(auditTrailService, never())
                .record(any(), any(), eq(UserAuditAction.PROFILE_CREATED), any(), any());
    }

    @Test
    void aRowThatIsNeitherFoundNorCreatedFailsLoudlyRatherThanReturningNull() {
        when(userProfileRepository.findByUserPublicId(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getOrCreateProfile(USER_ID, CORRELATION_ID, METADATA))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(USER_ID);
    }

    @Test
    void updatingTheProfileTrimsValuesAndAuditsFieldNamesOnly() {
        when(userProfileRepository.findByUserPublicId(USER_ID))
                .thenReturn(Optional.of(UserProfileEntity.createFor(USER_ID, "en", "INR")));
        UpdateProfileRequest request = new UpdateProfileRequest(
                "  Asha  ", "Menon", "   ", LocalDate.of(1995, 4, 12), Gender.FEMALE, null);

        UserProfileResponse updated = service.updateOwnProfile(USER_ID, request, CORRELATION_ID, METADATA);

        assertThat(updated.firstName()).isEqualTo("Asha");
        // Whitespace-only is the same as cleared, not a stored blank.
        assertThat(updated.displayName()).isNull();

        ArgumentCaptor<String> details = ArgumentCaptor.forClass(String.class);
        verify(auditTrailService)
                .record(eq(CORRELATION_ID), eq(USER_ID), eq(UserAuditAction.PROFILE_UPDATED), any(), details.capture());
        // The trail records which fields are set, never the customer's actual name or birth date.
        assertThat(details.getValue()).isEqualTo("fieldsSet=[firstName,lastName,dateOfBirth,gender]");
        assertThat(details.getValue()).doesNotContain("Asha").doesNotContain("Menon").doesNotContain("1995");
    }

    @Test
    void clearingEveryProfileFieldProducesAnEmptyFieldList() {
        when(userProfileRepository.findByUserPublicId(USER_ID))
                .thenReturn(Optional.of(UserProfileEntity.createFor(USER_ID, "en", "INR")));
        UpdateProfileRequest request = new UpdateProfileRequest(null, null, null, null, null, null);

        service.updateOwnProfile(USER_ID, request, CORRELATION_ID, METADATA);

        ArgumentCaptor<String> details = ArgumentCaptor.forClass(String.class);
        verify(auditTrailService)
                .record(any(), any(), eq(UserAuditAction.PROFILE_UPDATED), any(), details.capture());
        assertThat(details.getValue()).isEqualTo("fieldsSet=[]");
    }

    @Test
    void consentValuesAreRecordedInTheAuditTrailUnlikeProfileContents() {
        when(userProfileRepository.findByUserPublicId(USER_ID))
                .thenReturn(Optional.of(UserProfileEntity.createFor(USER_ID, "en", "INR")));
        UpdatePreferencesRequest request = new UpdatePreferencesRequest(true, false, "en-IN", "INR");

        UserProfileResponse updated = service.updateOwnPreferences(USER_ID, request, CORRELATION_ID, METADATA);

        assertThat(updated.marketingEmailOptIn()).isTrue();
        assertThat(updated.marketingSmsOptIn()).isFalse();
        assertThat(updated.preferredLanguage()).isEqualTo("en-IN");

        ArgumentCaptor<String> details = ArgumentCaptor.forClass(String.class);
        verify(auditTrailService)
                .record(eq(CORRELATION_ID), eq(USER_ID), eq(UserAuditAction.PREFERENCES_UPDATED), any(), details.capture());
        // Proving what was consented to, and when, is the whole point of auditing consent.
        assertThat(details.getValue()).contains("marketingEmailOptIn=true").contains("marketingSmsOptIn=false");
    }

    @Test
    void theAdminLookupNeverAutoCreatesAProfile() {
        when(userProfileRepository.findByUserPublicId("usr_absent")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getProfileByPublicId("usr_absent"))
                .isInstanceOf(ProfileNotFoundException.class);
        verify(userProfileRepository, never()).saveAndFlush(any());
    }

    @Test
    void theAdminLookupReturnsAnExistingProfile() {
        when(userProfileRepository.findByUserPublicId(USER_ID))
                .thenReturn(Optional.of(UserProfileEntity.createFor(USER_ID, "en", "INR")));

        assertThat(service.getProfileByPublicId(USER_ID).userPublicId()).isEqualTo(USER_ID);
    }
}
