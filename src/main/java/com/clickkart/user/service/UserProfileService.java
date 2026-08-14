// src/main/java/com/clickkart/user/service/UserProfileService.java
package com.clickkart.user.service;

import com.clickkart.user.dto.request.UpdatePreferencesRequest;
import com.clickkart.user.dto.request.UpdateProfileRequest;
import com.clickkart.user.dto.response.UserProfileResponse;
import com.clickkart.user.entity.UserProfileEntity;
import com.clickkart.user.web.RequestMetadata;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface UserProfileService {

    /**
     * Returns the caller's profile, creating an empty one on first access.
     *
     * <p>Lazy creation rather than Auth Service calling this service at registration: that would
     * couple registration to this service's availability and need a distributed transaction to stay
     * consistent. A valid access token is already proof the identity exists, so the profile can be
     * materialised the first time it's actually needed, with no cross-service coordination.
     */
    UserProfileEntity getOrCreateProfile(String userPublicId, String correlationId, RequestMetadata requestMetadata);

    UserProfileResponse getOwnProfile(String userPublicId, String correlationId, RequestMetadata requestMetadata);

    UserProfileResponse updateOwnProfile(
            String userPublicId, UpdateProfileRequest request, String correlationId, RequestMetadata requestMetadata);

    UserProfileResponse updateOwnPreferences(
            String userPublicId,
            UpdatePreferencesRequest request,
            String correlationId,
            RequestMetadata requestMetadata);

    /** Admin-only read of any customer's profile. Never auto-creates - see {@code ProfileNotFoundException}. */
    UserProfileResponse getProfileByPublicId(String userPublicId);

    /** Admin-only browse. {@code search} matches display/first/last name, case-insensitively; null returns everything. */
    Page<UserProfileResponse> browseProfiles(String search, Pageable pageable);

    /**
     * Bulk resolution for the internal API. Ids with no profile are simply absent from the result
     * rather than failing the batch - a caller resolving 50 order customers should not lose all 50
     * because one never opened their profile.
     */
    List<UserProfileResponse> findProfilesByPublicIds(List<String> userPublicIds);
}
