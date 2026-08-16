// src/main/java/com/clickkart/user/enums/AuditOutcome.java
package com.clickkart.user.enums;

/**
 * Whether the audited action succeeded or failed - kept distinct from the action enum (what
 * happened) so both are queryable independently.
 */
public enum AuditOutcome {
    SUCCESS,
    FAILURE
}
