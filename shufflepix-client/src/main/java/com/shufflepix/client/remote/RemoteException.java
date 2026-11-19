package com.shufflepix.client.remote;

public class RemoteException extends RuntimeException {
    private final int status;
    private final String code;

    public RemoteException(int status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public int getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }

    public boolean isNotFound() {
        return status == 404;
    }

    public boolean isVersionConflict() {
        return status == 409 && "VERSION_CONFLICT".equals(code);
    }
}
