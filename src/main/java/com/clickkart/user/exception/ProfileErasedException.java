// src/main/java/com/clickkart/user/exception/ProfileErasedException.java
package com.clickkart.user.exception;

/**
 * A write was attempted against a profile whose data has been erased.
 *
 * <p>Erasure is final. Silently accepting the write would quietly repopulate a profile someone
 * asked to have emptied - and because the self-service endpoints auto-provision on first access,
 * that would happen on the very next request rather than needing any deliberate act. Reads still
 * succeed and report {@code erasedAt}, so a client can tell the difference between "erased" and
 * "never filled in", which a bare 404 would hide.
 */
public class ProfileErasedException extends RuntimeException {

    public ProfileErasedException(String userPublicId) {
        super("The profile for " + userPublicId + " has been erased and can no longer be modified");
    }
}
