// src/main/java/com/clickkart/user/controller/AddressController.java
package com.clickkart.user.controller;

import com.clickkart.user.constant.ApiPaths;
import com.clickkart.user.constant.MdcKeys;
import com.clickkart.user.dto.ApiResponse;
import com.clickkart.user.dto.request.AddressRequest;
import com.clickkart.user.dto.response.AddressResponse;
import com.clickkart.user.security.AuthenticatedPrincipal;
import com.clickkart.user.service.AddressService;
import com.clickkart.user.web.ClientIpResolver;
import com.clickkart.user.web.RequestMetadata;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * The authenticated customer's shipping address book.
 *
 * <p>{@code addressId} appears in the path, but it is always resolved <em>within</em> the caller's
 * own profile, so supplying another customer's id yields 404 rather than access - see {@code
 * AddressServiceImpl.requireOwnedAddress}.
 */
@Tag(name = "Addresses", description = "The authenticated customer's shipping address book")
@RestController
@RequiredArgsConstructor
public class AddressController {

    private final AddressService addressService;
    private final ClientIpResolver clientIpResolver;

    @Operation(summary = "List saved addresses, default first")
    @GetMapping(ApiPaths.ME_ADDRESSES)
    public ResponseEntity<ApiResponse<List<AddressResponse>>> listMyAddresses(
            @AuthenticationPrincipal AuthenticatedPrincipal principal, HttpServletRequest httpRequest) {
        return envelope(HttpStatus.OK.value(), addressService.listOwnAddresses(principal.userId()), httpRequest);
    }

    @Operation(summary = "Fetch one saved address")
    @GetMapping(ApiPaths.ME_ADDRESS_BY_ID)
    public ResponseEntity<ApiResponse<AddressResponse>> getMyAddress(
            @AuthenticationPrincipal AuthenticatedPrincipal principal,
            @PathVariable Long addressId,
            HttpServletRequest httpRequest) {
        return envelope(
                HttpStatus.OK.value(), addressService.getOwnAddress(principal.userId(), addressId), httpRequest);
    }

    /** 201 Created - the first address saved is promoted to default automatically. */
    @Operation(summary = "Save a new address")
    @PostMapping(ApiPaths.ME_ADDRESSES)
    public ResponseEntity<ApiResponse<AddressResponse>> addMyAddress(
            @AuthenticationPrincipal AuthenticatedPrincipal principal,
            @Valid @RequestBody AddressRequest request,
            HttpServletRequest httpRequest) {
        AddressResponse created = addressService.addOwnAddress(
                principal.userId(), request, principal.correlationId(), metadataOf(httpRequest));
        return envelope(HttpStatus.CREATED.value(), created, httpRequest);
    }

    @Operation(summary = "Replace a saved address")
    @PutMapping(ApiPaths.ME_ADDRESS_BY_ID)
    public ResponseEntity<ApiResponse<AddressResponse>> updateMyAddress(
            @AuthenticationPrincipal AuthenticatedPrincipal principal,
            @PathVariable Long addressId,
            @Valid @RequestBody AddressRequest request,
            HttpServletRequest httpRequest) {
        AddressResponse updated = addressService.updateOwnAddress(
                principal.userId(), addressId, request, principal.correlationId(), metadataOf(httpRequest));
        return envelope(HttpStatus.OK.value(), updated, httpRequest);
    }

    /** 204 No Content. Soft delete - see {@code AddressEntity}'s Javadoc for why the row survives. */
    @Operation(summary = "Remove a saved address")
    @DeleteMapping(ApiPaths.ME_ADDRESS_BY_ID)
    public ResponseEntity<ApiResponse<Void>> deleteMyAddress(
            @AuthenticationPrincipal AuthenticatedPrincipal principal,
            @PathVariable Long addressId,
            HttpServletRequest httpRequest) {
        addressService.deleteOwnAddress(
                principal.userId(), addressId, principal.correlationId(), metadataOf(httpRequest));
        return envelope(HttpStatus.NO_CONTENT.value(), null, httpRequest);
    }

    @Operation(summary = "Make this the default delivery address")
    @PutMapping(ApiPaths.ME_ADDRESS_DEFAULT)
    public ResponseEntity<ApiResponse<AddressResponse>> makeMyAddressDefault(
            @AuthenticationPrincipal AuthenticatedPrincipal principal,
            @PathVariable Long addressId,
            HttpServletRequest httpRequest) {
        AddressResponse promoted = addressService.makeOwnAddressDefault(
                principal.userId(), addressId, principal.correlationId(), metadataOf(httpRequest));
        return envelope(HttpStatus.OK.value(), promoted, httpRequest);
    }

    private RequestMetadata metadataOf(HttpServletRequest request) {
        return new RequestMetadata(clientIpResolver.resolve(request), request.getHeader(HttpHeaders.USER_AGENT));
    }

    private <T> ResponseEntity<ApiResponse<T>> envelope(int status, T data, HttpServletRequest request) {
        String correlationId = MDC.get(MdcKeys.CORRELATION_ID);
        ApiResponse<T> body = ApiResponse.success(status, data, request.getRequestURI(), correlationId);
        return ResponseEntity.status(status).body(body);
    }
}
