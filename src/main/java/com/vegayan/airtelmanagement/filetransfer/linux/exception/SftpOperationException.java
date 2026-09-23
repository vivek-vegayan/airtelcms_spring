package com.vegayan.airtelmanagement.filetransfer.linux.exception;

/**
 * Wraps failures talking to the remote Linux server (connect/auth failure,
 * transfer failure, remote path not found, etc.) so controllers can map
 * them to a 502 Bad Gateway distinct from validation errors (400) or
 * unexpected server bugs (500).
 */
public class SftpOperationException extends RuntimeException {
    public SftpOperationException(String message) {
        super(message);
    }

    public SftpOperationException(String message, Throwable cause) {
        super(message, cause);
    }
}
