// src/main/java/com/clickkart/user/enums/SellerVerificationStatus.java
package com.clickkart.user.enums;

/**
 * Whether an operator has checked this seller's business identity.
 *
 * <p>Never settable by the seller themselves - only an ADMIN moves a profile out of {@link
 * #PENDING}. A seller who could self-declare {@link #VERIFIED} would make the whole check
 * decorative, and Product Service is expected to refuse listings from anything but a verified
 * seller.
 */
public enum SellerVerificationStatus {
    /** Newly submitted, or resubmitted after the seller changed an identity field. */
    PENDING,
    VERIFIED,
    REJECTED
}
