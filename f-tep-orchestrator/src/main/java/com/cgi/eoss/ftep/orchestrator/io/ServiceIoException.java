package com.cgi.eoss.ftep.orchestrator.io;

public class ServiceIoException extends RuntimeException {
    public ServiceIoException(Throwable cause) {
        super(cause);
    }

    public ServiceIoException(String message) {
        super(message);
    }

    public ServiceIoException(String message, Throwable cause) {
        super(message, cause);
    }
}
