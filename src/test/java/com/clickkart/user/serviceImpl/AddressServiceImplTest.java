// src/test/java/com/clickkart/user/serviceImpl/AddressServiceImplTest.java
package com.clickkart.user.serviceImpl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.clickkart.user.config.UserProperties;
import com.clickkart.user.dto.request.AddressRequest;
import com.clickkart.user.dto.response.AddressResponse;
import com.clickkart.user.entity.AddressEntity;
import com.clickkart.user.entity.UserProfileEntity;
import com.clickkart.user.enums.AddressLabel;
import com.clickkart.user.enums.UserAuditAction;
import com.clickkart.user.exception.AddressLimitExceededException;
import com.clickkart.user.exception.AddressNotFoundException;
import com.clickkart.user.repository.AddressRepository;
import com.clickkart.user.service.AuditTrailService;
import com.clickkart.user.service.UserProfileService;
import com.clickkart.user.web.RequestMetadata;
import java.util.List;
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
class AddressServiceImplTest {

    private static final String OWNER = "usr_owner";
    private static final String CORRELATION_ID = "corr-1";
    private static final RequestMetadata METADATA = new RequestMetadata("203.0.113.7", "junit");

    @Mock private AddressRepository addressRepository;
    @Mock private UserProfileService userProfileService;
    @Mock private AuditTrailService auditTrailService;

    private UserProperties userProperties;
    private AddressServiceImpl service;
    private UserProfileEntity profile;

    @BeforeEach
    void setUp() {
        userProperties = new UserProperties();
        userProperties.setMaxAddressesPerUser(3);
        service = new AddressServiceImpl(addressRepository, userProfileService, auditTrailService, userProperties);

        profile = UserProfileEntity.createFor(OWNER, "en", "INR");
        when(userProfileService.getOrCreateProfile(eq(OWNER), any(), any())).thenReturn(profile);
        when(addressRepository.saveAndFlush(any(AddressEntity.class)))
                .thenAnswer(invocation -> {
                    AddressEntity entity = invocation.getArgument(0);
                    if (entity.getId() == null) {
                        ReflectionTestUtils.setField(entity, "id", 100L);
                    }
                    return entity;
                });
    }

    private AddressRequest request(Boolean makeDefault) {
        return new AddressRequest(
                AddressLabel.HOME, " Asha Menon ", "9845550100", " 12 MG Road ", "  ", null,
                "Bengaluru", "Karnataka", "560001", "India", makeDefault);
    }

    private AddressEntity existingAddress(long id, boolean isDefault) {
        AddressEntity address = AddressEntity.createFor(profile);
        ReflectionTestUtils.setField(address, "id", id);
        address.update(AddressLabel.WORK, "Asha", "9845550100", "Line", null, null,
                "Bengaluru", "Karnataka", "560001", "India");
        address.markDefault(isDefault);
        return address;
    }

    @Test
    void theFirstAddressSavedBecomesTheDefaultEvenWhenNotRequested() {
        when(addressRepository.countByProfileUserPublicIdAndDeletedFalse(OWNER)).thenReturn(0L);

        AddressResponse saved = service.addOwnAddress(OWNER, request(false), CORRELATION_ID, METADATA);

        // A customer's only address not being their default would be a state with no meaning.
        assertThat(saved.defaultAddress()).isTrue();
    }

    @Test
    void aLaterAddressIsNotPromotedUnlessAsked() {
        when(addressRepository.countByProfileUserPublicIdAndDeletedFalse(OWNER)).thenReturn(2L);

        AddressResponse saved = service.addOwnAddress(OWNER, request(false), CORRELATION_ID, METADATA);

        assertThat(saved.defaultAddress()).isFalse();
        verify(addressRepository, never()).clearDefaultForOtherAddresses(any(), any());
    }

    @Test
    void savedFieldsAreTrimmedAndBlankOptionalsBecomeNull() {
        when(addressRepository.countByProfileUserPublicIdAndDeletedFalse(OWNER)).thenReturn(0L);

        AddressResponse saved = service.addOwnAddress(OWNER, request(false), CORRELATION_ID, METADATA);

        assertThat(saved.recipientName()).isEqualTo("Asha Menon");
        assertThat(saved.line1()).isEqualTo("12 MG Road");
        // Whitespace-only line2 must not persist as "  " - otherwise a printed label gets a blank row.
        assertThat(saved.line2()).isNull();
    }

    @Test
    void savingBeyondTheConfiguredLimitIsRejected() {
        when(addressRepository.countByProfileUserPublicIdAndDeletedFalse(OWNER)).thenReturn(3L);

        assertThatThrownBy(() -> service.addOwnAddress(OWNER, request(false), CORRELATION_ID, METADATA))
                .isInstanceOf(AddressLimitExceededException.class)
                .hasMessageContaining("3");
        verify(addressRepository, never()).saveAndFlush(any());
    }

    @Test
    void anotherCustomersAddressIsIndistinguishableFromOneThatDoesNotExist() {
        // The repository scopes by owner, so a foreign id simply returns empty - and must surface
        // as 404, never 403, or the endpoint becomes an id-enumeration oracle.
        when(addressRepository.findByIdAndProfileUserPublicIdAndDeletedFalse(999L, OWNER))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getOwnAddress(OWNER, 999L))
                .isInstanceOf(AddressNotFoundException.class);
    }

    @Test
    void everyWritePathRefusesAnAddressTheCallerDoesNotOwn() {
        when(addressRepository.findByIdAndProfileUserPublicIdAndDeletedFalse(999L, OWNER))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateOwnAddress(OWNER, 999L, request(false), CORRELATION_ID, METADATA))
                .isInstanceOf(AddressNotFoundException.class);
        assertThatThrownBy(() -> service.deleteOwnAddress(OWNER, 999L, CORRELATION_ID, METADATA))
                .isInstanceOf(AddressNotFoundException.class);
        assertThatThrownBy(() -> service.makeOwnAddressDefault(OWNER, 999L, CORRELATION_ID, METADATA))
                .isInstanceOf(AddressNotFoundException.class);

        // Nothing was written and nothing was audited on any of the three attempts.
        verify(addressRepository, never()).saveAndFlush(any());
        verify(auditTrailService, never()).record(any(), any(), any(), any(), any());
    }

    @Test
    void promotingAnAddressDemotesTheOthersFirst() {
        AddressEntity target = existingAddress(7L, false);
        when(addressRepository.findByIdAndProfileUserPublicIdAndDeletedFalse(7L, OWNER))
                .thenReturn(Optional.of(target));

        AddressResponse promoted = service.makeOwnAddressDefault(OWNER, 7L, CORRELATION_ID, METADATA);

        assertThat(promoted.defaultAddress()).isTrue();
        verify(addressRepository).clearDefaultForOtherAddresses(OWNER, 7L);
        verify(auditTrailService)
                .record(eq(CORRELATION_ID), eq(OWNER), eq(UserAuditAction.DEFAULT_ADDRESS_CHANGED), any(), any());
    }

    @Test
    void anUpdateMayPromoteButNeverSilentlyDemotes() {
        AddressEntity target = existingAddress(7L, true);
        when(addressRepository.findByIdAndProfileUserPublicIdAndDeletedFalse(7L, OWNER))
                .thenReturn(Optional.of(target));

        AddressResponse updated = service.updateOwnAddress(OWNER, 7L, request(false), CORRELATION_ID, METADATA);

        // makeDefault=false on an address that IS the default must not leave the customer with none.
        assertThat(updated.defaultAddress()).isTrue();
    }

    @Test
    void deletingTheDefaultPromotesTheOldestSurvivor() {
        AddressEntity target = existingAddress(7L, true);
        AddressEntity older = existingAddress(2L, false);
        AddressEntity newer = existingAddress(9L, false);
        when(addressRepository.findByIdAndProfileUserPublicIdAndDeletedFalse(7L, OWNER))
                .thenReturn(Optional.of(target));
        when(addressRepository.findByProfileUserPublicIdAndDeletedFalseOrderByDefaultAddressDescIdAsc(OWNER))
                .thenReturn(List.of(newer, older));

        service.deleteOwnAddress(OWNER, 7L, CORRELATION_ID, METADATA);

        assertThat(target.isDeleted()).isTrue();
        // A deleted address must never remain the one checkout would pre-fill.
        assertThat(target.isDefaultAddress()).isFalse();
        assertThat(older.isDefaultAddress()).isTrue();
        assertThat(newer.isDefaultAddress()).isFalse();
    }

    @Test
    void deletingANonDefaultAddressLeavesTheDefaultAlone() {
        AddressEntity target = existingAddress(7L, false);
        when(addressRepository.findByIdAndProfileUserPublicIdAndDeletedFalse(7L, OWNER))
                .thenReturn(Optional.of(target));

        service.deleteOwnAddress(OWNER, 7L, CORRELATION_ID, METADATA);

        assertThat(target.isDeleted()).isTrue();
        verify(addressRepository, never())
                .findByProfileUserPublicIdAndDeletedFalseOrderByDefaultAddressDescIdAsc(OWNER);
    }

    @Test
    void listingReturnsOnlyLiveAddresses() {
        when(addressRepository.findByProfileUserPublicIdAndDeletedFalseOrderByDefaultAddressDescIdAsc(OWNER))
                .thenReturn(List.of(existingAddress(1L, true), existingAddress(2L, false)));

        assertThat(service.listOwnAddresses(OWNER)).hasSize(2);
    }

    @Test
    void addressOwnershipIsCheckedAgainstTheOwningProfile() {
        AddressEntity address = existingAddress(1L, false);

        assertThat(address.isOwnedBy(OWNER)).isTrue();
        assertThat(address.isOwnedBy("usr_someone_else")).isFalse();
    }
}
