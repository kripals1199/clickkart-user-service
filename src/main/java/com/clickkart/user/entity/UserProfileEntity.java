// src/main/java/com/clickkart/user/entity/UserProfileEntity.java
package com.clickkart.user.entity;

import com.clickkart.user.enums.Gender;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * A customer's profile: everything about a person that is <em>not</em> a credential.
 *
 * <p>The split from Auth Service's {@code ClickKartUserEntity} is deliberate and is the reason
 * this service exists. Auth owns identity and secrets (password hash, roles, lockout state,
 * email/mobile used to sign in); this service owns the mutable, non-sensitive profile a customer
 * edits freely. They are joined only by {@link #userPublicId} - Auth Service's {@code publicId},
 * which is also the JWT {@code sub} claim. No foreign key spans the two databases, because they
 * are two databases, each reachable only by its own least-privilege role.
 *
 * <p>There is no local copy of the user's email or mobile number. Duplicating them here would
 * create a second source of truth that silently goes stale the moment a customer changes their
 * sign-in address in Auth Service, and would widen the blast radius of a compromise of this
 * service's database. Anything needing the current contact details asks Auth Service.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(
        name = "user_profiles",
        uniqueConstraints = @UniqueConstraint(name = "uk_user_profiles_user_public_id", columnNames = "user_public_id"),
        indexes = @Index(name = "idx_user_profiles_user_public_id", columnList = "user_public_id"))
public class UserProfileEntity extends BaseEntity {

    /**
     * Auth Service's {@code publicId} and the JWT subject. Immutable: a profile belongs to
     * exactly one identity for its whole life, and letting this change would silently transfer
     * one customer's address book to another.
     */
    @Column(name = "user_public_id", nullable = false, updatable = false, length = 64)
    private String userPublicId;

    @Column(name = "first_name", length = 60)
    private String firstName;

    @Column(name = "last_name", length = 60)
    private String lastName;

    @Column(name = "display_name", length = 80)
    private String displayName;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @Enumerated(EnumType.STRING)
    @Column(name = "gender", length = 20)
    private Gender gender;

    @Column(name = "avatar_url", length = 500)
    private String avatarUrl;

    /**
     * Marketing consent, default false on both channels. Opt-in rather than opt-out is the
     * legally safer default and matches the platform's transactional-only SMS setup.
     */
    @Column(name = "marketing_email_opt_in", nullable = false)
    private boolean marketingEmailOptIn;

    @Column(name = "marketing_sms_opt_in", nullable = false)
    private boolean marketingSmsOptIn;

    @Column(name = "preferred_language", nullable = false, length = 10)
    private String preferredLanguage;

    @Column(name = "preferred_currency", nullable = false, length = 3)
    private String preferredCurrency;

    private UserProfileEntity(String userPublicId, String preferredLanguage, String preferredCurrency) {
        this.userPublicId = userPublicId;
        this.preferredLanguage = preferredLanguage;
        this.preferredCurrency = preferredCurrency;
    }

    /**
     * A brand-new, empty profile for an identity that already exists in Auth Service. Every
     * optional field stays null until the customer fills it in - this service never invents
     * profile data it was not given.
     */
    public static UserProfileEntity createFor(String userPublicId, String defaultLanguage, String defaultCurrency) {
        return new UserProfileEntity(userPublicId, defaultLanguage, defaultCurrency);
    }

    public void updateProfile(
            String firstName,
            String lastName,
            String displayName,
            LocalDate dateOfBirth,
            Gender gender,
            String avatarUrl) {
        this.firstName = firstName;
        this.lastName = lastName;
        this.displayName = displayName;
        this.dateOfBirth = dateOfBirth;
        this.gender = gender;
        this.avatarUrl = avatarUrl;
    }

    public void updatePreferences(
            boolean marketingEmailOptIn, boolean marketingSmsOptIn, String preferredLanguage, String preferredCurrency) {
        this.marketingEmailOptIn = marketingEmailOptIn;
        this.marketingSmsOptIn = marketingSmsOptIn;
        this.preferredLanguage = preferredLanguage;
        this.preferredCurrency = preferredCurrency;
    }
}
