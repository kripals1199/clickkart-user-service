// src/main/java/com/clickkart/user/dto/request/UpsertSellerProfileRequest.java
package com.clickkart.user.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * What a seller may set on their own business profile.
 *
 * <p>Deliberately absent: {@code verificationStatus}, {@code verificationNote} and
 * {@code verificationDecidedAt}. Accepting any of them here would let a seller mark their own
 * business verified, which is the one thing the verification step exists to prevent. They are
 * settable only through the ADMIN decision endpoint.
 */
public record UpsertSellerProfileRequest(
        @NotBlank(message = "must not be blank") @Size(max = 150, message = "must be at most 150 characters")
                String businessName,

        /*
         * India GSTIN: 2-digit state code, 5 letters + 4 digits + 1 letter of the PAN, an
         * entity-number character, a literal 'Z', then a checksum character. Validating the shape
         * catches transposition and truncation at the edge; it is not a substitute for the
         * operator actually checking the registration, which is what verification is for.
         *
         * Accepts either case even though a GSTIN is canonically uppercase, because the service
         * layer uppercases before storing. An uppercase-only pattern rejected lowercase input at
         * the edge, which made that normalization unreachable on the API path and meant the code
         * and the contract disagreed about whether case mattered - the kind of mismatch a unit
         * test calling the service directly does not catch, since it never runs bean validation.
         */
        @NotBlank(message = "must not be blank")
                @Pattern(
                        regexp = "^[0-9]{2}[A-Za-z]{5}[0-9]{4}[A-Za-z][1-9A-Za-z][Zz][0-9A-Za-z]$",
                        message = "must be a valid 15-character GSTIN")
                String gstin,

        @Email(message = "must be a valid email address") @Size(max = 254, message = "must be at most 254 characters")
                String supportEmail,

        @Pattern(regexp = "^$|^[6-9]\\d{9}$", message = "must be a valid 10-digit Indian mobile number")
                String supportPhone,

        /** An address id from this seller's own address book, or null to nominate none. */
        Long pickupAddressId) {}
