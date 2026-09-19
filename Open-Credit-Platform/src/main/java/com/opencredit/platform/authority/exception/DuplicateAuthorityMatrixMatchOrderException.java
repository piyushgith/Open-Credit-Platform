package com.opencredit.platform.authority.exception;

public class DuplicateAuthorityMatrixMatchOrderException extends RuntimeException {

    public DuplicateAuthorityMatrixMatchOrderException(int matchOrder) {
        super("An active authority matrix entry already exists with matchOrder: " + matchOrder);
    }
}
