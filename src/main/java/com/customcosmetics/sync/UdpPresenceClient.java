// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.sync;

import com.customcosmetics.features.blocking.BlockHand;
import com.customcosmetics.features.blocking.BlockingRules;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Consumer;

/** One UDP socket; state is disposable, control retries only the latest cached datagram. */
public final class UdpPresenceClient implements BlockingPresence {
    public static final int MAX_RECORDS = 59, TTL_MS = 750;
    public static final int MAX_ITEM_ID_BYTES = 138;
    public record State(BlockHand hand, String itemId) {
        public State {
            if (hand == null || itemId == null || hand != BlockHand.NONE &&
                    (itemId.isEmpty() || !BlockingRules.normalize(itemId).equals(itemId)))
                throw new IllegalArgumentException("Invalid blocking item identity");
            if (hand == BlockHand.NONE) itemId = "";
        }
    }
    public record Scope(String world, Set<UUID> players) { public Scope { players = Set.copyOf(players); } }
    public record Session(String id, String key, int port, long expiresAt, boolean readOnly) {
        @Override public String toString() { return "UDP session [port=" + port + ", readOnly=" + readOnly + "]"; }
    }
    private final UdpPacket codec;
    private final Session session;
    private final Consumer<Map<UUID, BlockingTransport.Pose>> receiver;
    private volatile Scope desired = new Scope("", Set.of());
    private volatile State state = new State(BlockHand.NONE, "");
    private volatile boolean closed;
    private volatile DatagramSocket socket;
    private volatile String status = "Connecting";
    private volatile long latency;
    private final Thread worker;
    public UdpPresenceClient(String host, int port, Session session, Consumer<Map<UUID, BlockingTransport.Pose>> receiver) throws Exception {
        this.session = session; this.receiver = receiver;
        codec = new UdpPacket(HexFormat.of().parseHex(session.id()), HexFormat.of().parseHex(session.key()), true);
        worker = new Thread(() -> run(host, port), "CustomCosmetics-UDP"); worker.setDaemon(true); worker.start();
    }
    public void update(String world, Set<UUID> players, BlockHand hand, String itemId) {
        if (!world.isEmpty() && !world.matches("[a-f0-9]{64}")) throw new IllegalArgumentException("Invalid world hash");
        desired = new Scope(world, players); setPose(hand, itemId);
    }
    public boolean closed() { return closed; }
    public void setPose(BlockHand hand, String itemId) { state = new State(hand, itemId); }
    public String status() { return status; }
    public long latencyMs() { return latency; }
    private static long now() { return System.nanoTime() / 1_000_000; }
    private void run(String host, int port) {
        try (var connection = new DatagramSocket()) {
            socket = connection; connection.connect(new InetSocketAddress(host, port)); connection.setSoTimeout(20);
            Scope current = null;
            byte[] controlPacket = null;
            long control = 0, state = 0, acknowledged = 0, lastControlSent = 0, nextRetry = 0, nextControl = 0,
                nextState = 0, lastReceived = now(), incomingControl = 0;
            var visible = new LatestUdpPoses();
            int retry = 0, members = 0, stopCopies = 0;
            State previous = new State(BlockHand.NONE, "");
            while (!closed && session.expiresAt() > System.currentTimeMillis()) {
                long time = now(); Scope wanted = desired;
                if (!wanted.equals(current) || (acknowledged == control && time >= nextControl)) {
                    boolean changed = !wanted.equals(current);
                    current = wanted; control++;
                    controlPacket = codec.encode(UdpPacket.CONTROL, control, scopeBytes(current));
                    acknowledged = 0; nextRetry = 0; retry = 0; nextControl = time + 5000;
                    if (changed) { visible.clear(); receiver.accept(Map.of()); }
                }
                if (acknowledged != control && time >= nextRetry) {
                    send(connection, controlPacket); lastControlSent = time;
                    nextRetry = time + Math.min(1500, 250L << Math.min(retry++, 3));
                }
                State effective = !session.readOnly() && members > 1 && !current.world().isEmpty() && !current.players().isEmpty()
                    ? this.state : new State(BlockHand.NONE, "");
                if (acknowledged == control && (!effective.equals(previous) || time >= nextState && (effective.hand() != BlockHand.NONE || stopCopies > 0))) {
                    if (effective.hand() == BlockHand.NONE && previous.hand() != BlockHand.NONE) stopCopies = 3;
                    send(connection, codec.encode(UdpPacket.STATE, ++state, stateBytes(control, effective)));
                    previous = effective; nextState = time + 100;
                    if (effective.hand() == BlockHand.NONE) stopCopies--;
                }
                try {
                    var packet = new DatagramPacket(new byte[UdpPacket.MAX_SIZE + 1], UdpPacket.MAX_SIZE + 1); connection.receive(packet);
                    var message = codec.decode(Arrays.copyOf(packet.getData(), packet.getLength()));
                    if (message.flow() == UdpPacket.CONTROL) {
                        if (message.sequence() <= incomingControl) continue;
                        incomingControl = message.sequence(); var body = ByteBuffer.wrap(message.body());
                        if (body.remaining() == 1 && body.get(0) == 2) { status = "Session expired"; break; }
                        if (body.remaining() != 11 || body.get() != 1 || body.getLong() != control) continue;
                        acknowledged = control; int count = Short.toUnsignedInt(body.getShort());
                        if (count != members) { members = count; nextState = 0; }
                        if (retry == 1) latency = Math.max(0, now() - lastControlSent);
                        status = members > 1 ? "Live UDP" : "Idle UDP"; lastReceived = now();
                    } else {
                        var poses = parseSnapshot(message.body(), control, current.players(), now() - Math.min(latency / 2, 250));
                        lastReceived = now(); receiver.accept(visible.accept(message.sequence(), poses, now()));
                    }
                } catch (SocketTimeoutException ignored) { /* The loop also handles outgoing latest state. */ }
                catch (java.security.GeneralSecurityException | IllegalArgumentException ignored) { /* Drop tampered/truncated datagrams. */ }
                if (now() - lastReceived > 12000) { status = "UDP timeout"; break; }
            }
        } catch (Exception ignored) { if (!closed) status = "UDP unavailable"; }
        finally { closed = true; receiver.accept(Map.of()); }
    }
    static byte[] scopeBytes(Scope scope) {
        var buffer = ByteBuffer.allocate(32 + UdpInterest.BYTES);
        buffer.put(scope.world().isEmpty() ? new byte[32] : HexFormat.of().parseHex(scope.world())).put(UdpInterest.encode(scope.players()));
        return buffer.array();
    }
    static byte[] stateBytes(long control, State state) {
        byte[] id = state.itemId().getBytes(StandardCharsets.US_ASCII);
        byte value = (byte) (state.hand() == BlockHand.MAIN ? 1 : state.hand() == BlockHand.OFF ? 2 : 0);
        return ByteBuffer.allocate(10 + id.length).putLong(control).put(value).put((byte) id.length).put(id).array();
    }
    static Map<UUID, BlockingTransport.Pose> parseSnapshot(byte[] bytes, long scope, Set<UUID> interests, long receivedAt) {
        var buffer = ByteBuffer.wrap(bytes);
        if (bytes.length < 9 || bytes.length > UdpPacket.MAX_SIZE - UdpPacket.HEADER - 16 || buffer.getLong() != scope)
            throw new IllegalArgumentException("Stale or oversized UDP world snapshot");
        int count = Byte.toUnsignedInt(buffer.get());
        if (count > MAX_RECORDS) throw new IllegalArgumentException("Invalid UDP snapshot");
        var poses = new HashMap<UUID, BlockingTransport.Pose>();
        for (int i = 0; i < count; i++) {
            if (buffer.remaining() < 20) throw new IllegalArgumentException("Truncated UDP record");
            UUID id = new UUID(buffer.getLong(), buffer.getLong()); int value = Byte.toUnsignedInt(buffer.get()); int remaining = Short.toUnsignedInt(buffer.getShort());
            int length = Byte.toUnsignedInt(buffer.get());
            if (length > MAX_ITEM_ID_BYTES || length > buffer.remaining()) throw new IllegalArgumentException("Invalid UDP item length");
            byte[] item = new byte[length]; buffer.get(item); String itemId = new String(item, StandardCharsets.US_ASCII);
            if (value == 0 ? length != 0 : itemId.isEmpty() || !BlockingRules.normalize(itemId).equals(itemId))
                throw new IllegalArgumentException("Invalid UDP item identity");
            if (interests.contains(id) && (value == 0 && remaining == 0 || value >= 1 && value <= 2 && remaining > 0 && remaining <= TTL_MS))
                poses.put(id, new BlockingTransport.Pose(value == 0 ? BlockHand.NONE : value == 1 ? BlockHand.MAIN : BlockHand.OFF, receivedAt + remaining, itemId));
        }
        if (buffer.hasRemaining()) throw new IllegalArgumentException("Trailing UDP snapshot data");
        return Map.copyOf(poses);
    }
    private static void send(DatagramSocket socket, byte[] data) throws java.io.IOException { socket.send(new DatagramPacket(data, data.length)); }
    @Override public void close() { closed = true; RemoteAccess.release(this); var connection = socket; if (connection != null) connection.close(); }
}
