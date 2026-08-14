// src/main/java/com/clickkart/user/service/AddressService.java
package com.clickkart.user.service;

import com.clickkart.user.dto.request.AddressRequest;
import com.clickkart.user.dto.response.AddressResponse;
import com.clickkart.user.web.RequestMetadata;
import java.util.List;

/**
 * Every method takes the caller's own {@code userPublicId} and scopes its work to that customer.
 * There is no variant that operates on an arbitrary owner - the ability to touch someone else's
 * address book simply is not expressible through this interface.
 */
public interface AddressService {

    List<AddressResponse> listOwnAddresses(String userPublicId);

    AddressResponse getOwnAddress(String userPublicId, Long addressId);

    AddressResponse addOwnAddress(
            String userPublicId, AddressRequest request, String correlationId, RequestMetadata requestMetadata);

    AddressResponse updateOwnAddress(
            String userPublicId,
            Long addressId,
            AddressRequest request,
            String correlationId,
            RequestMetadata requestMetadata);

    void deleteOwnAddress(
            String userPublicId, Long addressId, String correlationId, RequestMetadata requestMetadata);

    AddressResponse makeOwnAddressDefault(
            String userPublicId, Long addressId, String correlationId, RequestMetadata requestMetadata);
}
