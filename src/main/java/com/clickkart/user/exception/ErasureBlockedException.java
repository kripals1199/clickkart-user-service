// src/main/java/com/clickkart/user/exception/ErasureBlockedException.java
package com.clickkart.user.exception;

/**
 * Self-service erasure was refused because the account carries an obligation that outlives the
 * customer's request.
 *
 * <p>Currently the only such case is a seller profile: a GSTIN and the trading history attached to
 * it are subject to statutory retention under Indian GST rules, and erasing a seller's business
 * identity would also orphan whatever they have listed. That is not a decision a self-service
 * endpoint should make silently in either direction - so it refuses and says why, leaving a human
 * to handle the parts that need judgement.
 */
public class ErasureBlockedException extends RuntimeException {

    private final String reason;

    public ErasureBlockedException(String reason) {
        super(reason);
        this.reason = reason;
    }

    public String getReason() {
        return reason;
    }
}
