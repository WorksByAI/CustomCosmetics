// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.sync;

import java.io.IOException;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;

/** Cosmetics, IRC and realtime share one Mojang proof instead of racing join requests. */
final class SharedAuthSession {
    @FunctionalInterface interface Authenticate { PresenceAuthClient.Session run() throws Exception; }
    private record Key(String base, UUID uuid, String name) {}
    private final Executor executor;
    private PresenceAuthClient.Session session;
    private Key pendingKey;
    private CompletableFuture<PresenceAuthClient.Session> pending;

    SharedAuthSession() { this(ForkJoinPool.commonPool()); }
    SharedAuthSession(Executor executor) { this.executor = executor; }

    synchronized PresenceAuthClient.Session current(String base, UUID uuid, String name) {
        return session != null && session.validFor(base, uuid, name) ? session : null;
    }

    synchronized CompletableFuture<PresenceAuthClient.Session> get(String base, UUID uuid, String name, Authenticate authenticate) {
        var cached = current(base, uuid, name);
        if (cached != null) return CompletableFuture.completedFuture(cached);
        var key = new Key(base, uuid, name.toLowerCase(Locale.ROOT));
        if (pending != null && key.equals(pendingKey)) return pending.copy();
        var result = new CompletableFuture<PresenceAuthClient.Session>();
        pending = result;
        pendingKey = key;
        executor.execute(() -> {
            try {
                var verified = authenticate.run();
                synchronized (this) {
                    if (pending != result) throw new IOException("Remote request cancelled.");
                    if (verified == null || !verified.validFor(base, uuid, name))
                        throw new IOException("Invalid authenticated service session.");
                    session = verified;
                    pending = null;
                    pendingKey = null;
                }
                result.complete(verified);
            } catch (Exception error) {
                synchronized (this) {
                    if (pending == result) { pending = null; pendingKey = null; }
                }
                result.completeExceptionally(error);
            }
        });
        // A cancelled caller must not cancel authentication for the other channels.
        return result.copy();
    }

    synchronized void reset() { session = null; pending = null; pendingKey = null; }
    synchronized void invalidate(PresenceAuthClient.Session rejected) { if (session == rejected) reset(); }
}
