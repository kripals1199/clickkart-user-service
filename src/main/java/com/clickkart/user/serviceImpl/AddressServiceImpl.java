// src/main/java/com/clickkart/user/serviceImpl/AddressServiceImpl.java
package com.clickkart.user.serviceImpl;

import com.clickkart.user.config.UserProperties;
import com.clickkart.user.constant.LoggerNames;
import com.clickkart.user.dto.request.AddressRequest;
import com.clickkart.user.dto.response.AddressResponse;
import com.clickkart.user.entity.AddressEntity;
import com.clickkart.user.entity.UserProfileEntity;
import com.clickkart.user.enums.UserAuditAction;
import com.clickkart.user.exception.AddressLimitExceededException;
import com.clickkart.user.exception.AddressNotFoundException;
import com.clickkart.user.repository.AddressRepository;
import com.clickkart.user.service.AddressService;
import com.clickkart.user.service.AuditTrailService;
import com.clickkart.user.service.UserProfileService;
import com.clickkart.user.web.RequestMetadata;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Maintains two invariants that the API surface alone cannot guarantee:
 *
 * <ol>
 *   <li><strong>At most one default address per customer.</strong> Enforced by demoting all others
 *       in a single bulk UPDATE before promoting the target, rather than by iterating in memory -
 *       see {@code AddressRepository.clearDefaultForOtherAddresses}.
 *   <li><strong>A customer with at least one live address always has a default.</strong> Deleting
 *       the current default promotes the oldest survivor. Without this, deleting the default would
 *       leave a populated address book with nothing selected, and checkout would have no address to
 *       pre-fill - a state the customer never asked for and cannot see the cause of.
 * </ol>
 */
@Slf4j(topic = LoggerNames.SECURITY)
@Service
@RequiredArgsConstructor
public class AddressServiceImpl implements AddressService {

    private final AddressRepository addressRepository;
    private final UserProfileService userProfileService;
    private final AuditTrailService auditTrailService;
    private final UserProperties userProperties;

    @Override
    @Transactional(readOnly = true)
    public List<AddressResponse> listOwnAddresses(String userPublicId) {
        return addressRepository
                .findByProfileUserPublicIdAndDeletedFalseOrderByDefaultAddressDescIdAsc(userPublicId)
                .stream()
                .map(AddressResponse::from)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public AddressResponse getOwnAddress(String userPublicId, Long addressId) {
        return AddressResponse.from(requireOwnedAddress(userPublicId, addressId));
    }

    @Override
    @Transactional
    public AddressResponse addOwnAddress(
            String userPublicId, AddressRequest request, String correlationId, RequestMetadata requestMetadata) {
        long liveCount = addressRepository.countByProfileUserPublicIdAndDeletedFalse(userPublicId);
        if (liveCount >= userProperties.getMaxAddressesPerUser()) {
            throw new AddressLimitExceededException(userProperties.getMaxAddressesPerUser());
        }

        UserProfileEntity profile = userProfileService.getOrCreateProfile(userPublicId, correlationId, requestMetadata);
        AddressEntity address = AddressEntity.createFor(profile);
        applyRequest(address, request);
        address = addressRepository.saveAndFlush(address);

        // The first address a customer saves becomes their default whether or not they asked -
        // a lone address that isn't the default would be a state with no meaning.
        if (request.shouldMakeDefault() || liveCount == 0) {
            promoteToDefault(userPublicId, address);
        }

        auditTrailService.record(
                correlationId,
                userPublicId,
                UserAuditAction.ADDRESS_ADDED,
                requestMetadata,
                "addressId=" + address.getId() + " default=" + address.isDefaultAddress());
        return AddressResponse.from(address);
    }

    @Override
    @Transactional
    public AddressResponse updateOwnAddress(
            String userPublicId,
            Long addressId,
            AddressRequest request,
            String correlationId,
            RequestMetadata requestMetadata) {
        AddressEntity address = requireOwnedAddress(userPublicId, addressId);
        applyRequest(address, request);

        // An update may promote, but never demote: unchecking "default" without naming a
        // replacement would leave the customer with no default at all, so it is ignored here.
        // Choosing a different default is what the dedicated endpoint is for.
        if (request.shouldMakeDefault() && !address.isDefaultAddress()) {
            promoteToDefault(userPublicId, address);
        }

        auditTrailService.record(
                correlationId,
                userPublicId,
                UserAuditAction.ADDRESS_UPDATED,
                requestMetadata,
                "addressId=" + address.getId());
        return AddressResponse.from(address);
    }

    @Override
    @Transactional
    public void deleteOwnAddress(
            String userPublicId, Long addressId, String correlationId, RequestMetadata requestMetadata) {
        AddressEntity address = requireOwnedAddress(userPublicId, addressId);
        boolean wasDefault = address.isDefaultAddress();
        address.markDeleted();
        addressRepository.saveAndFlush(address);

        if (wasDefault) {
            promoteOldestSurvivorToDefault(userPublicId);
        }

        auditTrailService.record(
                correlationId,
                userPublicId,
                UserAuditAction.ADDRESS_DELETED,
                requestMetadata,
                "addressId=" + addressId + " wasDefault=" + wasDefault);
    }

    @Override
    @Transactional
    public AddressResponse makeOwnAddressDefault(
            String userPublicId, Long addressId, String correlationId, RequestMetadata requestMetadata) {
        AddressEntity address = requireOwnedAddress(userPublicId, addressId);
        promoteToDefault(userPublicId, address);

        auditTrailService.record(
                correlationId,
                userPublicId,
                UserAuditAction.DEFAULT_ADDRESS_CHANGED,
                requestMetadata,
                "addressId=" + addressId);
        return AddressResponse.from(address);
    }

    /**
     * The single ownership gate. Resolving by (id, owner) means a row belonging to someone else is
     * indistinguishable from one that does not exist - both 404, so the endpoint cannot be used to
     * probe which ids are real. See {@code AddressNotFoundException}.
     */
    private AddressEntity requireOwnedAddress(String userPublicId, Long addressId) {
        return addressRepository
                .findByIdAndProfileUserPublicIdAndDeletedFalse(addressId, userPublicId)
                .orElseThrow(() -> {
                    log.debug("ADDRESS_ACCESS_DENIED_OR_MISSING addressId={} userPublicId={}", addressId, userPublicId);
                    return new AddressNotFoundException(addressId);
                });
    }

    private void promoteToDefault(String userPublicId, AddressEntity address) {
        // Demote first, then promote. The reverse order would leave a window in which no address
        // is default, and a concurrent read landing there would see a customer with none.
        addressRepository.clearDefaultForOtherAddresses(userPublicId, address.getId());
        address.markDefault(true);
        addressRepository.saveAndFlush(address);
    }

    private void promoteOldestSurvivorToDefault(String userPublicId) {
        addressRepository
                .findByProfileUserPublicIdAndDeletedFalseOrderByDefaultAddressDescIdAsc(userPublicId)
                .stream()
                .min(Comparator.comparing(AddressEntity::getId))
                .ifPresent(survivor -> {
                    survivor.markDefault(true);
                    addressRepository.saveAndFlush(survivor);
                });
    }

    private void applyRequest(AddressEntity address, AddressRequest request) {
        address.update(
                request.label(),
                request.recipientName().trim(),
                request.contactNumber().trim(),
                request.line1().trim(),
                trimToNull(request.line2()),
                trimToNull(request.landmark()),
                request.city().trim(),
                request.state().trim(),
                request.postalCode().trim(),
                request.country().trim());
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
