package org.aldousdev.dockflowbackend.auth.exceptions;

public class CompanyAccessDeniedException extends RuntimeException {
    public CompanyAccessDeniedException(String message) {
        super(message);
    }

    public CompanyAccessDeniedException(String message, Throwable cause) {
        super(message, cause);
    }
}
