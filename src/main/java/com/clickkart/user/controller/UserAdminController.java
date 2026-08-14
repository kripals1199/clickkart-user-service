// src/main/java/com/clickkart/user/controller/UserAdminController.java
package com.clickkart.user.controller;

import com.clickkart.user.constant.ApiPaths;
import com.clickkart.user.constant.MdcKeys;
import com.clickkart.user.dto.ApiResponse;
import com.clickkart.user.dto.PageResponse;
import com.clickkart.user.dto.response.UserProfileResponse;
import com.clickkart.user.security.AuthenticatedPrincipal;
import com.clickkart.user.service.UserProfileService;
import com.clickkart.user.web.ClientIpResolver;
import com.clickkart.user.web.RequestMetadata;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Operator endpoints, gated by {@code hasRole('ADMIN')} against the roles claim of the verified
 * token.
 *
 * <p>Reads plus exactly one write: erasure. Everything else stays read-only on purpose - an
 * operator may need to look a customer up for a support case, but editing someone else's profile on
 * their behalf is not a flow this platform has, and the change would not be attributable to the
 * customer who supposedly made it. Erasure is the exception because a data-protection request can
 * legitimately arrive through support rather than the app, and because the self-service path has to
 * refuse cases an operator can resolve.
 *
 * <p>{@code GET /api/v1/users/{userPublicId}} coexists with the self-service {@code
 * /api/v1/users/me} because Spring resolves a literal path segment ahead of a template one, so
 * {@code /me} can never be swallowed by this route. Moving either path without keeping that in mind
 * would silently route a customer's own request into an admin-only handler and 403 them.
 */
@Tag(name = "User Administration", description = "Operator lookup across customer profiles (ADMIN only)")
@RestController
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class UserAdminController {

    private final UserProfileService userProfileService;
    private final ClientIpResolver clientIpResolver;

    @Operation(summary = "Browse customer profiles, optionally filtered by name or public id")
    @GetMapping(ApiPaths.ADMIN_USERS)
    public ResponseEntity<ApiResponse<PageResponse<UserProfileResponse>>> browseProfiles(
            @RequestParam(required = false) String search, Pageable pageable, HttpServletRequest httpRequest) {
        PageResponse<UserProfileResponse> page =
                PageResponse.from(userProfileService.browseProfiles(search, pageable));
        return envelope(HttpStatus.OK.value(), page, httpRequest);
    }

    @Operation(summary = "Fetch one customer's profile by their Auth Service public id")
    @GetMapping(ApiPaths.ADMIN_USER_BY_PUBLIC_ID)
    public ResponseEntity<ApiResponse<UserProfileResponse>> getProfile(
            @PathVariable String userPublicId, HttpServletRequest httpRequest) {
        return envelope(HttpStatus.OK.value(), userProfileService.getProfileByPublicId(userPublicId), httpRequest);
    }

    /**
     * Erasure on a customer's behalf, for a data-protection request arriving through support rather
     * than the app.
     *
     * <p>The one write this controller has, and it is here because it cannot be self-service in
     * every case: unlike {@code DELETE /me} it proceeds even when a seller profile exists, on the
     * basis that an operator has already handled the statutory-retention question a self-service
     * endpoint has no way to judge. Audited against the operator, with the subject in the details.
     */
    @Operation(summary = "Erase a customer's personal data on their behalf (irreversible)")
    @DeleteMapping(ApiPaths.ADMIN_USER_BY_PUBLIC_ID)
    public ResponseEntity<ApiResponse<Void>> eraseProfile(
            @AuthenticationPrincipal AuthenticatedPrincipal principal,
            @PathVariable String userPublicId,
            HttpServletRequest httpRequest) {
        userProfileService.eraseProfile(
                userPublicId,
                principal.userId(),
                principal.correlationId(),
                new RequestMetadata(clientIpResolver.resolve(httpRequest), httpRequest.getHeader(HttpHeaders.USER_AGENT)));
        return envelope(HttpStatus.NO_CONTENT.value(), null, httpRequest);
    }

    private <T> ResponseEntity<ApiResponse<T>> envelope(int status, T data, HttpServletRequest request) {
        String correlationId = MDC.get(MdcKeys.CORRELATION_ID);
        ApiResponse<T> body = ApiResponse.success(status, data, request.getRequestURI(), correlationId);
        return ResponseEntity.status(status).body(body);
    }
}
