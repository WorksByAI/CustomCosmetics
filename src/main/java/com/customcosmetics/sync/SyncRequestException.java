package com.customcosmetics.sync;

import java.io.IOException;

public final class SyncRequestException extends IOException {
    private final SyncFailure failure;
    public SyncRequestException(SyncFailure failure) {
        super(failure.description());
        this.failure = failure;
    }
    public SyncFailure failure() { return failure; }
}
