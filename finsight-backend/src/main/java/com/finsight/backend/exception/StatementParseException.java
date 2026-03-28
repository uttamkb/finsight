package com.finsight.backend.exception;

/**
 * Thrown by the parser pipeline when a bank statement cannot be parsed.
 */
public class StatementParseException extends RuntimeException {
    public StatementParseException(String message) {
        super(message);
    }
    public StatementParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
