package com.picojava.session;

import java.io.IOException;

public class SessionException extends IOException {
    public SessionException(String message) {
        super(message);
    }

    public SessionException(String message, Throwable cause) {
        super(message, cause);
    }
}
