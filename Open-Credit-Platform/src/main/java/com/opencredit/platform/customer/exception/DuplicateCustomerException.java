package com.opencredit.platform.customer.exception;

/**
 * Thrown when a new customer's email, phone number or PAN collides with an
 * already-registered customer.
 */
public class DuplicateCustomerException extends RuntimeException {

    public DuplicateCustomerException(String field, String value) {
        super("A customer already exists with " + field + ": " + value);
    }
}
