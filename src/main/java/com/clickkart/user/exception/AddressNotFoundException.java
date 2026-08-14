// src/main/java/com/clickkart/user/exception/AddressNotFoundException.java
package com.clickkart.user.exception;

/**
 * Raised when an address id does not exist, is soft-deleted, <em>or belongs to another customer</em>.
 *
 * <p>All three collapse into one 404 on purpose. Answering 403 for the third case would confirm
 * that the id exists and belongs to someone, letting an authenticated attacker walk the id space
 * and map out other customers' rows. A 404 leaks nothing beyond "not yours to see".
 */
public class AddressNotFoundException extends RuntimeException {

    public AddressNotFoundException(Long addressId) {
        super("Address " + addressId + " was not found");
    }

    private AddressNotFoundException(String message) {
        super(message);
    }

    /**
     * The customer has saved no addresses, so there is no default to return. Distinct message from
     * the id-based case because "Address null was not found" would be a worse answer than the
     * question deserves.
     */
    public static AddressNotFoundException noDefaultAddress() {
        return new AddressNotFoundException("No default address is set for this user");
    }
}
