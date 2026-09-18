package com.opencredit.platform.document.exception;

import java.util.UUID;

public class LoanDocumentNotFoundException extends RuntimeException {

    public LoanDocumentNotFoundException(UUID documentId) {
        super("No loan document found with id: " + documentId);
    }
}
