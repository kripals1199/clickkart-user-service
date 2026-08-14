// src/main/java/com/clickkart/user/entity/SellerProfileEntity.java
package com.clickkart.user.entity;

import com.clickkart.user.enums.SellerVerificationStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * A seller's business identity, kept in its own table rather than as nullable columns on {@link
 * UserProfileEntity}.
 *
 * <p>Two reasons: almost every user is a customer and never a seller, so these columns would be
 * null on nearly every row; and seller data has a lifecycle a customer profile does not - it gets
 * verified, can be rejected, and must be re-checked when the business identity changes.
 *
 * <p>The pickup address is a reference into the seller's <em>own</em> address book rather than a
 * duplicated set of address columns, so a seller who corrects their address does it in one place
 * and both delivery and pickup stay consistent. Ownership of that address is validated on write.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(
        name = "seller_profiles",
        uniqueConstraints = {
            @UniqueConstraint(name = "uk_seller_profiles_profile_id", columnNames = "profile_id"),
            // A GSTIN identifies one business registration; two sellers claiming the same one is a
            // data-integrity problem the database should refuse, not something to catch in review.
            @UniqueConstraint(name = "uk_seller_profiles_gstin", columnNames = "gstin")
        },
        indexes = @Index(name = "idx_seller_profiles_verification_status", columnList = "verification_status"))
public class SellerProfileEntity extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "profile_id", nullable = false, updatable = false)
    private UserProfileEntity profile;

    @Column(name = "business_name", nullable = false, length = 150)
    private String businessName;

    /** India's 15-character GST identification number, stored uppercase. */
    @Column(name = "gstin", nullable = false, length = 15)
    private String gstin;

    @Column(name = "support_email", length = 254)
    private String supportEmail;

    @Column(name = "support_phone", length = 15)
    private String supportPhone;

    /** An address id from this seller's own address book. Null until they nominate one. */
    @Column(name = "pickup_address_id")
    private Long pickupAddressId;

    @Enumerated(EnumType.STRING)
    @Column(name = "verification_status", nullable = false, length = 20)
    private SellerVerificationStatus verificationStatus;

    /** Operator's note on a rejection, so the seller can be told what to fix. */
    @Column(name = "verification_note", length = 500)
    private String verificationNote;

    @Column(name = "verification_decided_at")
    private Instant verificationDecidedAt;

    private SellerProfileEntity(UserProfileEntity profile) {
        this.profile = profile;
        this.verificationStatus = SellerVerificationStatus.PENDING;
    }

    public static SellerProfileEntity createFor(UserProfileEntity profile) {
        return new SellerProfileEntity(profile);
    }

    /**
     * Applies seller-supplied changes.
     *
     * <p>Returns whether the business identity changed, because that must send an already-verified
     * seller back to {@link SellerVerificationStatus#PENDING}: otherwise a seller could pass
     * verification with one legitimate GSTIN and then quietly swap in another, carrying the
     * verified badge onto a business nobody checked. Contact details and pickup address do not
     * trigger re-verification - they are not what was verified.
     */
    public boolean update(
            String businessName, String gstin, String supportEmail, String supportPhone, Long pickupAddressId) {
        boolean identityChanged =
                !businessName.equals(this.businessName) || !gstin.equals(this.gstin);

        this.businessName = businessName;
        this.gstin = gstin;
        this.supportEmail = supportEmail;
        this.supportPhone = supportPhone;
        this.pickupAddressId = pickupAddressId;

        if (identityChanged && this.verificationStatus == SellerVerificationStatus.VERIFIED) {
            this.verificationStatus = SellerVerificationStatus.PENDING;
            this.verificationNote = null;
            this.verificationDecidedAt = null;
        }
        return identityChanged;
    }

    /** ADMIN only - see {@link SellerVerificationStatus}. */
    public void decideVerification(SellerVerificationStatus status, String note) {
        this.verificationStatus = status;
        this.verificationNote = note;
        this.verificationDecidedAt = Instant.now();
    }

    /** Cleared when the referenced address is deleted, so pickup never points at a removed row. */
    public void clearPickupAddress() {
        this.pickupAddressId = null;
    }

    public boolean isOwnedBy(String userPublicId) {
        return profile != null && profile.getUserPublicId().equals(userPublicId);
    }
}
