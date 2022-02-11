package com.cgi.eoss.ftep.io;

public class ServiceIo429Exception extends ServiceIoException {

    private double retryAfterSeconds;

    public ServiceIo429Exception(String message, double retryAfterSeconds) {
        super(message);
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public double getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
