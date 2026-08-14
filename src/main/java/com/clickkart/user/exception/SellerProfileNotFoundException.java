// src/main/java/com/clickkart/user/exception/SellerProfileNotFoundException.java
package com.clickkart.user.exception;

/** No seller profile exists for this user - they have never submitted business details. */
public class SellerProfileNotFoundException extends RuntimeException {

    public SellerProfileNotFoundException(String userPublicId) {
        super("No seller profile exists for user " + userPublicId);
    }
}
