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

    /**
     * Same as {@link #getOrCreateProfile} but refuses an erased profile, for use by every write
     * path. Kept separate rather than folded into the getter because reads must still succeed on an
     * erased profile - a client needs to be able to see {@code erasedAt} rather than get a 404 that
     * looks identical to "never created".
     */
    UserProfileEntity getWritableProfile(String userPublicId, String correlationId, RequestMetadata requestMetadata);

    UserProfileResponse getOwnProfile(String userPublicId, String correlationId, RequestMetadata requestMetadata);

    UserProfileResponse updateOwnProfile(
            String userPublicId, UpdateProfileRequest request, String correlationId, RequestMetadata requestMetadata);

    UserProfileResponse updateOwnPreferences(
            String userPublicId,
            UpdatePreferencesRequest request,
            String correlationId,
            RequestMetadata requestMetadata);

    /**
     * Irreversibly erases the caller's personal data: profile fields cleared, marketing consent
     * withdrawn, every saved address scrubbed and deleted.
     *
     * <p>Refused while a seller profile exists - see {@code ErasureBlockedException}. Already-erased
     * profiles return normally rather than failing, so a retried request is not an error.
     */
    void eraseOwnProfile(String userPublicId, String correlationId, RequestMetadata requestMetadata);

    /**
     * ADMIN erasure on behalf of a customer, for a data-protection request that arrives through
     * support rather than the app. Unlike the self-service path this proceeds even when a seller
     * profile exists, because an operator has by then handled the parts needing judgement - the
     * seller's business identity is scrubbed along with everything else.
     */
    void eraseProfile(
            String userPublicId, String actorPublicId, String correlationId, RequestMetadata requestMetadata);

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
