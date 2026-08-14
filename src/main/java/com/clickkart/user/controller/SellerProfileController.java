// src/main/java/com/clickkart/user/controller/SellerProfileController.java
package com.clickkart.user.controller;

import com.clickkart.user.constant.ApiPaths;
import com.clickkart.user.constant.MdcKeys;
import com.clickkart.user.dto.ApiResponse;
import com.clickkart.user.dto.PageResponse;
import com.clickkart.user.dto.request.SellerVerificationDecisionRequest;
import com.clickkart.user.dto.request.UpsertSellerProfileRequest;
import com.clickkart.user.dto.response.SellerProfileResponse;
import com.clickkart.user.enums.SellerVerificationStatus;
import com.clickkart.user.security.AuthenticatedPrincipal;
import com.clickkart.user.service.SellerProfileService;
import com.clickkart.user.web.ClientIpResolver;
import com.clickkart.user.web.RequestMetadata;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * A seller's own business profile, plus the operator endpoints that verify it.
 *
 * <p>The write requires {@code ROLE_SELLER}, which only Auth Service grants - a customer cannot
 * create a seller profile for themselves and start listing. The read does not require the role, so
 * someone whose seller role was revoked can still see the state of their submission rather than
 * getting an opaque 403.
 */
@Tag(name = "Seller Profile", description = "Seller business identity and operator verification")
@RestController
@RequiredArgsConstructor
public class SellerProfileController {

    private final SellerProfileService sellerProfileService;
    private final ClientIpResolver clientIpResolver;

    @Operation(summary = "Fetch the authenticated seller's business profile")
    @GetMapping(ApiPaths.ME_SELLER)
    public ResponseEntity<ApiResponse<SellerProfileResponse>> getMySellerProfile(
            @AuthenticationPrincipal AuthenticatedPrincipal principal, HttpServletRequest httpRequest) {
        return envelope(
                HttpStatus.OK.value(), sellerProfileService.getOwnSellerProfile(principal.userId()), httpRequest);
    }

    /**
     * Creates on first call, updates thereafter. Changing business name or GSTIN sends an
     * already-verified seller back to PENDING - see {@code SellerProfileEntity.update}.
     */
    @Operation(summary = "Create or update the authenticated seller's business profile")
    @PreAuthorize("hasRole('SELLER')")
    @PutMapping(ApiPaths.ME_SELLER)
    public ResponseEntity<ApiResponse<SellerProfileResponse>> upsertMySellerProfile(
            @AuthenticationPrincipal AuthenticatedPrincipal principal,
            @Valid @RequestBody UpsertSellerProfileRequest request,
            HttpServletRequest httpRequest) {
        SellerProfileResponse saved = sellerProfileService.upsertOwnSellerProfile(
                principal.userId(), request, principal.correlationId(), metadataOf(httpRequest));
        return envelope(HttpStatus.OK.value(), saved, httpRequest);
    }

    @Operation(summary = "Browse sellers, optionally filtered by verification status")
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping(ApiPaths.ADMIN_SELLERS)
    public ResponseEntity<ApiResponse<PageResponse<SellerProfileResponse>>> browseSellers(
            @RequestParam(required = false) SellerVerificationStatus status,
            Pageable pageable,
            HttpServletRequest httpRequest) {
        return envelope(
                HttpStatus.OK.value(),
                PageResponse.from(sellerProfileService.browseSellers(status, pageable)),
                httpRequest);
    }

    /** The decision is audited against the ADMIN who made it, not the seller it concerns. */
    @Operation(summary = "Approve or reject a seller")
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping(ApiPaths.ADMIN_SELLER_VERIFICATION)
    public ResponseEntity<ApiResponse<SellerProfileResponse>> decideVerification(
            @AuthenticationPrincipal AuthenticatedPrincipal principal,
            @PathVariable String userPublicId,
            @Valid @RequestBody SellerVerificationDecisionRequest request,
            HttpServletRequest httpRequest) {
        SellerProfileResponse decided = sellerProfileService.decideVerification(
                userPublicId, request, principal.userId(), principal.correlationId(), metadataOf(httpRequest));
        return envelope(HttpStatus.OK.value(), decided, httpRequest);
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
