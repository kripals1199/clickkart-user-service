// src/main/java/com/clickkart/user/entity/AddressEntity.java
package com.clickkart.user.entity;

import com.clickkart.user.enums.AddressLabel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * One entry in a customer's shipping address book.
 *
 * <p><strong>Soft delete, not hard delete.</strong> {@link #deleted} exists because an address is
 * referenced by orders that have already shipped to it. Physically removing the row would either
 * break that reference or silently rewrite history for a past order - both unacceptable for
 * something a customer or a dispute may need to look at years later. Order Service does not exist
 * yet, so nothing depends on this today; getting it right now is much cheaper than migrating a
 * populated table later, once real orders point at these rows.
 *
 * <p>The customer-facing effect is identical to a delete: every read path filters
 * {@code deleted = false}, so a deleted address disappears from the address book and can never be
 * fetched, updated, or promoted to default again.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(
        name = "addresses",
        indexes = {
            @Index(name = "idx_addresses_profile_id", columnList = "profile_id"),
            @Index(name = "idx_addresses_profile_id_deleted", columnList = "profile_id, deleted")
        })
public class AddressEntity extends BaseEntity {

    /** Fits every scrubbed column's length limit, including {@code postal_code} at 10. */
    private static final String REDACTED = "[erased]";

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "profile_id", nullable = false, updatable = false)
    private UserProfileEntity profile;

    @Enumerated(EnumType.STRING)
    @Column(name = "label", nullable = false, length = 20)
    private AddressLabel label;

    /** Who the courier asks for - not necessarily the account holder (gifts, family orders). */
    @Column(name = "recipient_name", nullable = false, length = 120)
    private String recipientName;

    /** Delivery contact for this address specifically, independent of the account's sign-in mobile. */
    @Column(name = "contact_number", nullable = false, length = 15)
    private String contactNumber;

    @Column(name = "line1", nullable = false, length = 200)
    private String line1;

    @Column(name = "line2", length = 200)
    private String line2;

    @Column(name = "landmark", length = 150)
    private String landmark;

    @Column(name = "city", nullable = false, length = 100)
    private String city;

    @Column(name = "state", nullable = false, length = 100)
    private String state;

    @Column(name = "postal_code", nullable = false, length = 10)
    private String postalCode;

    @Column(name = "country", nullable = false, length = 60)
    private String country;

    @Column(name = "default_address", nullable = false)
    private boolean defaultAddress;

    @Column(name = "deleted", nullable = false)
    private boolean deleted;

    private AddressEntity(UserProfileEntity profile) {
        this.profile = profile;
    }

    public static AddressEntity createFor(UserProfileEntity profile) {
        return new AddressEntity(profile);
    }

    public void update(
            AddressLabel label,
            String recipientName,
            String contactNumber,
            String line1,
            String line2,
            String landmark,
            String city,
            String state,
            String postalCode,
            String country) {
        this.label = label;
        this.recipientName = recipientName;
        this.contactNumber = contactNumber;
        this.line1 = line1;
        this.line2 = line2;
        this.landmark = landmark;
        this.city = city;
        this.state = state;
        this.postalCode = postalCode;
        this.country = country;
    }

    public void markDefault(boolean defaultAddress) {
        this.defaultAddress = defaultAddress;
    }

    /** Also clears the default flag - a deleted address must never remain the one orders default to. */
    public void markDeleted() {
        this.deleted = true;
        this.defaultAddress = false;
    }

    /**
     * Irreversibly overwrites every field that identifies a person or a place, as part of profile
     * erasure. The row itself survives for the same reason it survives an ordinary delete - it may
     * be referenced - but nothing recoverable is left in it.
     *
     * <p>This is why Order Service must <em>snapshot</em> an address at checkout rather than hold a
     * reference to this row: an order's own record of where it shipped is subject to statutory
     * retention and must not be rewritten by a later erasure request, while this row must be. Two
     * different obligations on the same data, which only separate copies can satisfy.
     *
     * <p>Non-null columns get a fixed marker rather than empty strings, so a redacted row is
     * obviously redacted in a query result instead of looking like corrupt or partial data.
     */
    public void scrubForErasure() {
        this.recipientName = REDACTED;
        this.contactNumber = REDACTED;
        this.line1 = REDACTED;
        this.line2 = null;
        this.landmark = null;
        this.city = REDACTED;
        this.state = REDACTED;
        this.postalCode = REDACTED;
        this.country = REDACTED;
        this.deleted = true;
        this.defaultAddress = false;
    }

    /** True when this row belongs to the given Auth Service publicId. The ownership check every write path runs. */
    public boolean isOwnedBy(String userPublicId) {
        return profile != null && profile.getUserPublicId().equals(userPublicId);
    }
}
