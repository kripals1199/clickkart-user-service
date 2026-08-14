// src/main/java/com/clickkart/user/controller/UserProfileController.java
package com.clickkart.user.controller;

import com.clickkart.user.constant.ApiPaths;
import com.clickkart.user.constant.MdcKeys;
import com.clickkart.user.dto.ApiResponse;
import com.clickkart.user.dto.request.UpdatePreferencesRequest;
import com.clickkart.user.dto.request.UpdateProfileRequest;
import com.clickkart.user.dto.response.UserProfileResponse;
import com.clickkart.user.security.AuthenticatedPrincipal;
import com.clickkart.user.service.UserProfileService;
import com.clickkart.user.web.ClientIpResolver;
import com.clickkart.user.web.RequestMetadata;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Self-service profile endpoints. The subject is always {@code principal.userId()} - taken from the
 * signature-verified token, never from the path or a header - so there is no request a caller can
 * construct that reads or edits another customer's profile.
 */
@Tag(name = "User Profile", description = "The authenticated customer's own profile and preferences")
@RestController
@RequiredArgsConstructor
public class UserProfileController {

    private final UserProfileService userProfileService;
    private final ClientIpResolver clientIpResolver;

    @Operation(summary = "Fetch the authenticated customer's profile, creating an empty one on first access")
    @GetMapping(ApiPaths.ME)
    public ResponseEntity<ApiResponse<UserProfileResponse>> getMyProfile(
            @AuthenticationPrincipal AuthenticatedPrincipal principal, HttpServletRequest httpRequest) {
        UserProfileResponse profile = userProfileService.getOwnProfile(
                principal.userId(), principal.correlationId(), metadataOf(httpRequest));
        return envelope(HttpStatus.OK.value(), profile, httpRequest);
    }

    @Operation(summary = "Replace the authenticated customer's editable profile fields")
    @PutMapping(ApiPaths.ME)
    public ResponseEntity<ApiResponse<UserProfileResponse>> updateMyProfile(
            @AuthenticationPrincipal AuthenticatedPrincipal principal,
            @Valid @RequestBody UpdateProfileRequest request,
            HttpServletRequest httpRequest) {
        UserProfileResponse profile = userProfileService.updateOwnProfile(
                principal.userId(), request, principal.correlationId(), metadataOf(httpRequest));
        return envelope(HttpStatus.OK.value(), profile, httpRequest);
    }

    @Operation(summary = "Update marketing consent and locale preferences")
    @PutMapping(ApiPaths.ME_PREFERENCES)
    public ResponseEntity<ApiResponse<UserProfileResponse>> updateMyPreferences(
            @AuthenticationPrincipal AuthenticatedPrincipal principal,
            @Valid @RequestBody UpdatePreferencesRequest request,
            HttpServletRequest httpRequest) {
        UserProfileResponse profile = userProfileService.updateOwnPreferences(
                principal.userId(), request, principal.correlationId(), metadataOf(httpRequest));
        return envelope(HttpStatus.OK.value(), profile, httpRequest);
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
