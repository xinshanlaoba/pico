package com.picojava.session;

public class SessionCorruptedException extends SessionException {
    public SessionCorruptedException(String message, Throwable cause) {
        super(message, cause);
    }

    public SessionCorruptedException(String message) {
        super(message);
    }
}
