// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.update;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.concurrent.*;

/** Update I/O never occupies the game's shared worker pool. Cancellation interrupts socket I/O. */
final class UpdateIo {
    static final ExecutorService WORKERS = Executors.newVirtualThreadPerTaskExecutor();
    private static final ScheduledExecutorService TIMER = Executors.newSingleThreadScheduledExecutor(
        Thread.ofPlatform().daemon(true).name("csb-update-deadline").factory());
    private UpdateIo() {}

    static <T> CompletableFuture<T> submit(Callable<T> action, Duration timeout) {
        var result = new CompletableFuture<T>();
        var task = WORKERS.submit(() -> {
            try { if (!result.isDone()) result.complete(action.call()); }
            catch (Exception failure) { result.completeExceptionally(failure); }
        });
        var timer = TIMER.schedule(() -> result.completeExceptionally(new SocketTimeoutException("Update request timed out")),
            timeout.toMillis(), TimeUnit.MILLISECONDS);
        result.whenComplete((value, failure) -> {
            timer.cancel(false);
            if (failure != null) task.cancel(true);
        });
        return result;
    }

    static <T> T await(Callable<T> action, Duration timeout) throws IOException {
        var result = submit(action, timeout);
        try { return result.get(); }
        catch (InterruptedException interrupted) {
            result.cancel(true); Thread.currentThread().interrupt();
            throw new IOException("Update request cancelled", interrupted);
        } catch (ExecutionException failure) {
            if (failure.getCause() instanceof IOException io) throw io;
            throw new IOException("Update request failed", failure.getCause());
        }
    }

    static void requireActive() throws IOException {
        if (Thread.currentThread().isInterrupted()) throw new IOException("Update request cancelled");
    }
}
