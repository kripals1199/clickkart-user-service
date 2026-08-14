// src/main/java/com/clickkart/user/exception/AddressLimitExceededException.java
package com.clickkart.user.exception;

/** The customer already holds {@code user.max-addresses-per-user} live addresses. */
public class AddressLimitExceededException extends RuntimeException {

    private final int limit;

    public AddressLimitExceededException(int limit) {
        super("A maximum of " + limit + " saved addresses is allowed");
        this.limit = limit;
    }

    public int getLimit() {
        return limit;
    }
}
