// src/main/java/com/clickkart/user/exception/ProfileNotFoundException.java
package com.clickkart.user.exception;

/**
 * Raised only by the admin lookup, for a publicId with no profile row.
 *
 * <p>The self-service {@code /me} endpoints never throw this: a profile is created on first
 * access for any identity holding a valid token (see {@code UserProfileServiceImpl}), so from the
 * customer's point of view their profile always exists.
 */
public class ProfileNotFoundException extends RuntimeException {

    public ProfileNotFoundException(String userPublicId) {
        super("No profile exists for user " + userPublicId);
    }
}
