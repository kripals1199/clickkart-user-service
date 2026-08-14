// src/main/java/com/clickkart/user/exception/DuplicateGstinException.java
package com.clickkart.user.exception;

/**
 * Another seller already claims this GSTIN.
 *
 * <p>Reported as a conflict rather than a validation error because the value is well-formed - the
 * problem is that it is taken, which the caller can only resolve by using a different registration.
 */
public class DuplicateGstinException extends RuntimeException {

    public DuplicateGstinException() {
        super("This GSTIN is already registered to another seller");
    }
}
