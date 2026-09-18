package com.opencredit.platform.document.exception;

import com.opencredit.platform.document.model.DocumentStatus;

public class IllegalDocumentTransitionException extends RuntimeException {

    public IllegalDocumentTransitionException(DocumentStatus from, DocumentStatus to) {
        super("Cannot transition loan document from " + from + " to " + to);
    }
}
