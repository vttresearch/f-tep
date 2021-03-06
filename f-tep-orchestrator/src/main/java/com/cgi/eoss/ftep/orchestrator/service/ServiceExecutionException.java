package com.cgi.eoss.ftep.orchestrator.service;

/**
 * <p>Signals that an exception occurred in the execution of an F-TEP Service.</p>
 */
public class ServiceExecutionException extends RuntimeException {

    /**
     * <p>Constructs a new service execution exception with the given detail message.</p>
     *
     * @param message
     */
    public ServiceExecutionException(String message) {
        super(message);
    }

    /**
     * <p>Constructs a new service execution exception with the given detail message and cause.</p>
     *
     * @param message
     * @param cause
     */
    public ServiceExecutionException(String message, Throwable cause) {
        super(message, cause);
    }
}
