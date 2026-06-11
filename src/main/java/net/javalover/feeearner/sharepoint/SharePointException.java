package net.javalover.feeearner.sharepoint;

/** Wraps any failure (transport, auth, HTTP non-2xx) talking to Microsoft Graph. */
public class SharePointException extends RuntimeException {
    public SharePointException(String message) { super(message); }
    public SharePointException(String message, Throwable cause) { super(message, cause); }
}
