// src/main/java/com/clickkart/user/enums/UserAuditAction.java
package com.clickkart.user.enums;

/**
 * This service's own audit vocabulary, reported to the Audit Log Service as a plain string.
 *
 * <p>There is deliberately no shared enum with Auth Service or the Audit Log Service (Rule 4:
 * no shared library) - the sink stores {@code action} as a {@code String} precisely so each
 * service can own its vocabulary. Names must stay within the sink's 60-character column.
 */
public enum UserAuditAction {
    PROFILE_CREATED,
    PROFILE_UPDATED,
    PREFERENCES_UPDATED,
    ADDRESS_ADDED,
    ADDRESS_UPDATED,
    ADDRESS_DELETED,
    DEFAULT_ADDRESS_CHANGED,
    SELLER_PROFILE_CREATED,
    SELLER_PROFILE_UPDATED,
    /** Seller changed their business identity, so a previously granted verification was withdrawn. */
    SELLER_VERIFICATION_RESET,
    /** An operator approved or rejected a seller. Recorded against the ADMIN, not the seller. */
    SELLER_VERIFICATION_DECIDED,
    /**
     * Personal data erased on request. The entry itself is deliberately retained - it records that
     * an erasure happened and when, which is the evidence a data-protection request was honoured,
     * and it carries no personal data to erase.
     */
    PROFILE_ERASED
}
