// src/test/java/com/clickkart/user/dto/request/RequestValidationTest.java
package com.clickkart.user.dto.request;

import static org.assertj.core.api.Assertions.assertThat;

import com.clickkart.user.enums.AddressLabel;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Exercises the bean-validation annotations themselves.
 *
 * <p>These exist because the service-layer tests construct request records directly and therefore
 * never run validation - which hid a real defect: {@code UpsertSellerProfileRequest} rejected a
 * lowercase GSTIN at the edge while the service layer was busy uppercasing it, so the
 * normalization was unreachable through the API and the two layers disagreed about whether case
 * mattered. Only a request that actually passes through a {@link Validator} catches that.
 */
class RequestValidationTest {

    private static Validator validator;

    @BeforeAll
    static void setUp() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            validator = factory.getValidator();
        }
    }

    private Set<String> violatedFields(Object request) {
        return validator.validate(request).stream()
                .map(violation -> violation.getPropertyPath().toString())
                .collect(Collectors.toSet());
    }

    private UpsertSellerProfileRequest seller(String gstin) {
        return new UpsertSellerProfileRequest("Menon Traders", gstin, "help@shop.example", "9845550100", null);
    }

    @Test
    void aLowercaseGstinIsAcceptedBecauseTheServiceUppercasesIt() {
        // The regression: an uppercase-only pattern made the service's own normalization dead code
        // on the API path, and a customer typing lowercase got a validation error for a value the
        // system was about to canonicalize anyway.
        assertThat(violatedFields(seller("29abcde1234f1z5"))).isEmpty();
    }

    @Test
    void anUppercaseGstinIsAccepted() {
        assertThat(violatedFields(seller("29ABCDE1234F1Z5"))).isEmpty();
    }

    @Test
    void aMalformedGstinIsRejected() {
        assertThat(violatedFields(seller("29ABCDE1234F1Y5"))).contains("gstin"); // 'Z' position wrong
        assertThat(violatedFields(seller("29ABCDE1234F1Z"))).contains("gstin"); // too short
        assertThat(violatedFields(seller("ABCDE12341234Z5"))).contains("gstin"); // state code not numeric
        assertThat(violatedFields(seller(""))).contains("gstin");
    }

    @Test
    void anOptionalSupportPhoneMayBeEmptyButNotMalformed() {
        assertThat(violatedFields(new UpsertSellerProfileRequest(
                        "Menon Traders", "29ABCDE1234F1Z5", null, "", null)))
                .isEmpty();
        assertThat(violatedFields(new UpsertSellerProfileRequest(
                        "Menon Traders", "29ABCDE1234F1Z5", null, "12345", null)))
                .contains("supportPhone");
    }

    @Test
    void addressRequiresAnIndianMobileAndSixDigitPinCode() {
        assertThat(violatedFields(address("9845550100", "560001"))).isEmpty();
        // Indian mobiles start 6-9; a leading 5 is not a real number.
        assertThat(violatedFields(address("5845550100", "560001"))).contains("contactNumber");
        // PIN codes never start with 0.
        assertThat(violatedFields(address("9845550100", "060001"))).contains("postalCode");
        assertThat(violatedFields(address("9845550100", "56001"))).contains("postalCode");
    }

    @Test
    void preferencesRequireBothConsentFlagsToBeStatedExplicitly() {
        // A missing flag must not silently read as false and revoke a consent nobody touched.
        assertThat(violatedFields(new UpdatePreferencesRequest(null, false, "en", "INR")))
                .contains("marketingEmailOptIn");
        assertThat(violatedFields(new UpdatePreferencesRequest(true, null, "en", "INR")))
                .contains("marketingSmsOptIn");
        assertThat(violatedFields(new UpdatePreferencesRequest(true, false, "english", "INR")))
                .contains("preferredLanguage");
        assertThat(violatedFields(new UpdatePreferencesRequest(true, false, "en", "Rupees")))
                .contains("preferredCurrency");
        assertThat(violatedFields(new UpdatePreferencesRequest(true, false, "en-IN", "INR"))).isEmpty();
    }

    @Test
    void aBulkLookupMustBeNonEmptyAndBounded() {
        assertThat(violatedFields(new ProfileLookupRequest(java.util.List.of()))).contains("userPublicIds");
        assertThat(violatedFields(new ProfileLookupRequest(
                        java.util.stream.IntStream.range(0, 201).mapToObj(i -> "usr_" + i).toList())))
                .contains("userPublicIds");
        assertThat(violatedFields(new ProfileLookupRequest(java.util.List.of("usr_1")))).isEmpty();
    }

    private AddressRequest address(String contactNumber, String postalCode) {
        return new AddressRequest(
                AddressLabel.HOME, "Asha Menon", contactNumber, "12 MG Road", null, null,
                "Bengaluru", "Karnataka", postalCode, "India", null);
    }
}
