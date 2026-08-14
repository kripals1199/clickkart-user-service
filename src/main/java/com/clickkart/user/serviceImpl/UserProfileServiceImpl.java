// src/main/java/com/clickkart/user/serviceImpl/UserProfileServiceImpl.java
package com.clickkart.user.serviceImpl;

import com.clickkart.user.config.UserProperties;
import com.clickkart.user.constant.LoggerNames;
import com.clickkart.user.dto.request.UpdatePreferencesRequest;
import com.clickkart.user.dto.request.UpdateProfileRequest;
import com.clickkart.user.dto.response.UserProfileResponse;
import com.clickkart.user.entity.UserProfileEntity;
import com.clickkart.user.enums.UserAuditAction;
import com.clickkart.user.exception.ProfileNotFoundException;
import com.clickkart.user.repository.UserProfileRepository;
import com.clickkart.user.repository.UserProfileSpecifications;
import com.clickkart.user.service.AuditTrailService;
import com.clickkart.user.service.UserProfileService;
import com.clickkart.user.web.RequestMetadata;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j(topic = LoggerNames.SECURITY)
@Service
@RequiredArgsConstructor
public class UserProfileServiceImpl implements UserProfileService {

    private final UserProfileRepository userProfileRepository;
    private final UserProfileCreator userProfileCreator;
    private final AuditTrailService auditTrailService;
    private final UserProperties userProperties;

    /**
     * {@inheritDoc}
     *
     * <p>The insert can lose a race: two concurrent first-time requests from the same customer (a
     * page firing several calls at once is enough) both see no row and both insert. The unique
     * constraint on {@code user_public_id} is what actually prevents a duplicate profile - "check
     * then insert" alone is a correctness bug that only shows up under concurrency.
     *
     * <p>The insert therefore runs in its own transaction ({@link UserProfileCreator}). It cannot be
     * done inline: a failed flush marks the surrounding transaction rollback-only, so catching the
     * violation and re-reading in the same transaction would fail on the re-read and turn a routine
     * race into a 500. Both paths finish by re-reading through this transaction's own persistence
     * context, so the returned entity is managed here and the caller's subsequent mutations are
     * picked up by dirty checking.
     */
    @Override
    @Transactional
    public UserProfileEntity getOrCreateProfile(
            String userPublicId, String correlationId, RequestMetadata requestMetadata) {
        Optional<UserProfileEntity> existing = userProfileRepository.findByUserPublicId(userPublicId);
        if (existing.isPresent()) {
            return existing.get();
        }

        boolean createdByThisRequest = true;
        try {
            userProfileCreator.createInNewTransaction(
                    userPublicId, userProperties.getDefaultLanguage(), userProperties.getDefaultCurrency());
        } catch (DataIntegrityViolationException e) {
            // Another request created it between our read and our insert. That is the constraint
            // doing its job, not an error - fall through and read the winner's row.
            log.debug("PROFILE_CREATE_RACE_LOST userPublicId={} correlationId={} - reading the winner's row",
                    userPublicId, correlationId);
            createdByThisRequest = false;
        }

        UserProfileEntity profile = userProfileRepository
                .findByUserPublicId(userPublicId)
                .orElseThrow(() -> new IllegalStateException(
                        "Profile for " + userPublicId + " was neither found nor created"));

        // Only the request that actually inserted the row reports it, so a lost race does not emit
        // a second PROFILE_CREATED event for a profile that was only created once.
        if (createdByThisRequest) {
            auditTrailService.record(
                    correlationId,
                    userPublicId,
                    UserAuditAction.PROFILE_CREATED,
                    requestMetadata,
                    "profile auto-provisioned on first access");
        }
        return profile;
    }

    @Override
    @Transactional
    public UserProfileResponse getOwnProfile(
            String userPublicId, String correlationId, RequestMetadata requestMetadata) {
        return UserProfileResponse.from(getOrCreateProfile(userPublicId, correlationId, requestMetadata));
    }

    @Override
    @Transactional
    public UserProfileResponse updateOwnProfile(
            String userPublicId,
            UpdateProfileRequest request,
            String correlationId,
            RequestMetadata requestMetadata) {
        UserProfileEntity profile = getOrCreateProfile(userPublicId, correlationId, requestMetadata);
        profile.updateProfile(
                trimToNull(request.firstName()),
                trimToNull(request.lastName()),
                trimToNull(request.displayName()),
                request.dateOfBirth(),
                request.gender(),
                trimToNull(request.avatarUrl()));

        // Names the fields that now hold a value, never their contents - see AuditTrailServiceImpl.
        auditTrailService.record(
                correlationId, userPublicId, UserAuditAction.PROFILE_UPDATED, requestMetadata, describePopulatedFields(profile));
        return UserProfileResponse.from(profile);
    }

    @Override
    @Transactional
    public UserProfileResponse updateOwnPreferences(
            String userPublicId,
            UpdatePreferencesRequest request,
            String correlationId,
            RequestMetadata requestMetadata) {
        UserProfileEntity profile = getOrCreateProfile(userPublicId, correlationId, requestMetadata);
        profile.updatePreferences(
                request.marketingEmailOptIn(),
                request.marketingSmsOptIn(),
                request.preferredLanguage(),
                request.preferredCurrency());

        // Consent values ARE recorded, unlike profile contents: proving what a customer consented
        // to, and when, is the reason a marketing-consent audit exists at all.
        String details = "marketingEmailOptIn=%s marketingSmsOptIn=%s language=%s currency=%s"
                .formatted(
                        request.marketingEmailOptIn(),
                        request.marketingSmsOptIn(),
                        request.preferredLanguage(),
                        request.preferredCurrency());
        auditTrailService.record(
                correlationId, userPublicId, UserAuditAction.PREFERENCES_UPDATED, requestMetadata, details);
        return UserProfileResponse.from(profile);
    }

    @Override
    @Transactional(readOnly = true)
    public UserProfileResponse getProfileByPublicId(String userPublicId) {
        return userProfileRepository
                .findByUserPublicId(userPublicId)
                .map(UserProfileResponse::from)
                .orElseThrow(() -> new ProfileNotFoundException(userPublicId));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<UserProfileResponse> browseProfiles(String search, Pageable pageable) {
        return userProfileRepository
                .findAll(UserProfileSpecifications.matchesSearchTerm(search), pageable)
                .map(UserProfileResponse::from);
    }

    /** Blank-to-null so "cleared" and "whitespace" are the same stored state rather than two. */
    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /**
     * Lists which optional fields now hold a value, so the trail shows the shape of a change
     * without ever recording the customer's actual name or date of birth.
     */
    private static String describePopulatedFields(UserProfileEntity profile) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("firstName", profile.getFirstName());
        fields.put("lastName", profile.getLastName());
        fields.put("displayName", profile.getDisplayName());
        fields.put("dateOfBirth", profile.getDateOfBirth());
        fields.put("gender", profile.getGender());
        fields.put("avatarUrl", profile.getAvatarUrl());

        String populated = fields.entrySet().stream()
                .filter(entry -> entry.getValue() != null)
                .map(Map.Entry::getKey)
                .collect(Collectors.joining(","));
        return "fieldsSet=[" + populated + "]";
    }
}
