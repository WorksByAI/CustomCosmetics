// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.sync;

import com.customcosmetics.config.ConfigManager;
import com.customcosmetics.features.animations.PresenceWorld;
import com.customcosmetics.features.blocking.BlockHand;
import net.minecraft.client.Minecraft;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/** Independent cosmetic WSS by default, with the previous encrypted UDP transport retained. */
public final class BlockingSync {
    private static final java.util.concurrent.ExecutorService NETWORK = Executors.newSingleThreadExecutor(r -> {
        Thread worker = new Thread(r, "CustomCosmetics-blocking-auth"); worker.setDaemon(true); return worker;
    });
    private static final BlockingTransport TRANSPORT = new BlockingTransport(PresenceSync::openActionConnection);
    private static final AtomicLong GENERATION = new AtomicLong();
    private static final AtomicBoolean CONNECTING = new AtomicBoolean();
    private static volatile Map<UUID, BlockingTransport.Pose> remote = Map.of();
    private static BlockingPresence live;
    private static String endpoint = "";
    private static PresenceAuthClient.Session authenticated;
    private static long nextConnect;
    private BlockingSync() {}
    private static long now() { return System.nanoTime() / 1_000_000; }
    public static void localHandChanged(BlockHand hand, String itemId) { if(RealtimeSync.enabled())RealtimeSync.block(hand,itemId);else if (live != null && !live.closed()) live.setPose(hand, itemId); }
    public static BlockingTransport.Pose pose(UUID uuid) {
        if (!RemoteAccess.online()) return null;
        if(RealtimeSync.enabled())return RealtimeSync.pose(uuid);
        var pose = remote.get(uuid);
        return pose != null && pose.expiresAt() > now() ? pose : null;
    }
    public static void disconnect() {
        GENERATION.incrementAndGet(); if (live != null) live.close();
        live = null; remote = Map.of(); authenticated = null; endpoint = ""; nextConnect = 0;
        RealtimeSync.disconnect();
    }
    public static BlockHand hand(UUID uuid) {
        var pose = pose(uuid);
        return pose != null ? pose.hand() : BlockHand.NONE;
    }
    public static void tick(BlockHand local, String itemId, Object worldIdentity) {
        if(RealtimeSync.enabled()){
            if(live!=null){GENERATION.incrementAndGet();live.close();live=null;remote=Map.of();endpoint="";}
            RealtimeSync.block(local,itemId);return;
        }
        String base = PresenceSync.serviceBaseUrl(), host = SyncEndpoint.normalizeHost(ConfigManager.syncUdpHost);
        boolean useUdp = "udp".equals(ConfigManager.syncBlockingTransport);
        boolean active = RemoteAccess.online() && worldIdentity != null && !base.isBlank() && (!useUdp || host != null && !host.isBlank())
            && com.customcosmetics.config.FeatureModules.enabled(com.customcosmetics.config.FeatureModules.Feature.ANIMATIONS) && ConfigManager.swordBlockingEnabled && ConfigManager.swordBlockingSyncEnabled && ConfigManager.syncEnabled;
        String key = active ? base + "|" + ConfigManager.syncBlockingTransport + "|" + host + ":" + ConfigManager.syncUdpPort : "";
        var auth = active ? PresenceSync.actionSession() : null;
        if (!endpoint.equals(key) || authenticated != auth) {
            GENERATION.incrementAndGet();
            if (live != null) live.close();
            live = null; remote = Map.of(); authenticated = auth; endpoint = key; nextConnect = 0;
        }
        if (!active) return;
        var client = Minecraft.getInstance();
        var scope = PresenceWorld.read(client);
        if (live != null && !live.closed()) { live.update(scope.world(), scope.players(), local, itemId); return; }
        remote = Map.of();
        if (now() < nextConnect || !CONNECTING.compareAndSet(false, true)) return;
        long generation = GENERATION.get(); nextConnect = now() + 5000;
        NETWORK.execute(() -> {
            try {
                if (generation != GENERATION.get()) return;
                var session = useUdp ? TRANSPORT.udpSession(base, auth) : null;
                java.util.function.Consumer<Map<UUID, BlockingTransport.Pose>> receiver =
                    poses -> { if (generation == GENERATION.get()) remote = poses; };
                BlockingPresence candidate = RemoteAccess.channel(() -> useUdp ? new UdpPresenceClient(host, ConfigManager.syncUdpPort, session, receiver)
                    : new WebSocketPresenceClient(base, auth, receiver));
                client.execute(() -> {
                    if (generation != GENERATION.get()) { candidate.close(); return; }
                    live = candidate;
                });
            } catch (Exception unavailable) { /* Connection retries are bounded; poses expire without persistence. */ }
            finally { CONNECTING.set(false); }
        });
    }
}
