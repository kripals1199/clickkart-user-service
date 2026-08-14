// src/main/java/com/clickkart/user/controller/InternalUserController.java
package com.clickkart.user.controller;

import com.clickkart.user.constant.ApiPaths;
import com.clickkart.user.constant.MdcKeys;
import com.clickkart.user.dto.ApiResponse;
import com.clickkart.user.dto.request.ProfileLookupRequest;
import com.clickkart.user.dto.response.AddressResponse;
import com.clickkart.user.dto.response.SellerProfileResponse;
import com.clickkart.user.dto.response.UserProfileResponse;
import com.clickkart.user.service.AddressService;
import com.clickkart.user.service.SellerProfileService;
import com.clickkart.user.service.UserProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Service-to-service reads. Authenticated by shared secret, not a customer token - see {@code
 * InternalApiKeyFilter} for why the network position alone is not treated as sufficient.
 *
 * <p>Every method is a <strong>read</strong>. Nothing here mutates a customer's data, so a leaked
 * internal key is a disclosure problem rather than a tampering one - a meaningful difference when
 * the credential is long-lived and shared between services. Writes stay on the customer-facing API
 * where they are attributable to the person who made them.
 *
 * <p>Address lookups are scoped by owner, exactly as the customer-facing paths are: passing an
 * address id belonging to a different user returns 404. That keeps the ownership rule in one place
 * rather than trusting each calling service to pair the right ids.
 *
 * <p>These operations are excluded from the published OpenAPI spec (see {@code OpenApiConfig}) -
 * they are not part of the product's API and listing them in the Gateway's aggregated Swagger would
 * advertise an unrouted surface to the internet.
 */
@Tag(name = "Internal", description = "Service-to-service reads. Not routed through the Gateway.")
@RestController
@RequiredArgsConstructor
public class InternalUserController {

    private final UserProfileService userProfileService;
    private final AddressService addressService;
    private final SellerProfileService sellerProfileService;

    @Operation(summary = "Resolve one profile by public id")
    @GetMapping(ApiPaths.INTERNAL_PROFILE)
    public ResponseEntity<ApiResponse<UserProfileResponse>> getProfile(
            @PathVariable String userPublicId, HttpServletRequest httpRequest) {
        return envelope(HttpStatus.OK.value(), userProfileService.getProfileByPublicId(userPublicId), httpRequest);
    }

    /** Unknown ids are simply absent from the result rather than failing the whole batch. */
    @Operation(summary = "Resolve many profiles in one call")
    @PostMapping(ApiPaths.INTERNAL_PROFILES_LOOKUP)
    public ResponseEntity<ApiResponse<List<UserProfileResponse>>> lookupProfiles(
            @Valid @RequestBody ProfileLookupRequest request, HttpServletRequest httpRequest) {
        return envelope(
                HttpStatus.OK.value(), userProfileService.findProfilesByPublicIds(request.userPublicIds()), httpRequest);
    }

    /**
     * The call Order Service makes at checkout to snapshot a shipping address. Returns 404 if the
     * address does not exist, is deleted, or belongs to a different user.
     */
    @Operation(summary = "Resolve one address owned by a given user")
    @GetMapping(ApiPaths.INTERNAL_ADDRESS)
    public ResponseEntity<ApiResponse<AddressResponse>> getAddress(
            @PathVariable String userPublicId, @PathVariable Long addressId, HttpServletRequest httpRequest) {
        return envelope(HttpStatus.OK.value(), addressService.getOwnAddress(userPublicId, addressId), httpRequest);
    }

    /** Checkout pre-fill. 404 when the customer has saved no addresses at all. */
    @Operation(summary = "Fetch a user's default delivery address")
    @GetMapping(ApiPaths.INTERNAL_DEFAULT_ADDRESS)
    public ResponseEntity<ApiResponse<AddressResponse>> getDefaultAddress(
            @PathVariable String userPublicId, HttpServletRequest httpRequest) {
        return envelope(HttpStatus.OK.value(), addressService.getDefaultAddress(userPublicId), httpRequest);
    }

    /** Product Service uses this to attribute a listing and to refuse unverified sellers. */
    @Operation(summary = "Fetch a seller's business profile and verification status")
    @GetMapping(ApiPaths.INTERNAL_SELLER)
    public ResponseEntity<ApiResponse<SellerProfileResponse>> getSeller(
            @PathVariable String userPublicId, HttpServletRequest httpRequest) {
        return envelope(HttpStatus.OK.value(), sellerProfileService.getSellerProfile(userPublicId), httpRequest);
    }

    private <T> ResponseEntity<ApiResponse<T>> envelope(int status, T data, HttpServletRequest request) {
        String correlationId = MDC.get(MdcKeys.CORRELATION_ID);
        ApiResponse<T> body = ApiResponse.success(status, data, request.getRequestURI(), correlationId);
        return ResponseEntity.status(status).body(body);
    }
}
