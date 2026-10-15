package com.customcosmetics.sync;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import com.customcosmetics.features.blocking.BlockHand;
import java.security.GeneralSecurityException;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class UdpPacketTest {
    @Test void peerKeysHaveTheirOwnProtocolDomainAndOppositeDirections()throws Exception {
        var a=new UdpPacket(new byte[16],new byte[32],true,4);var b=new UdpPacket(new byte[16],new byte[32],false,4);
        byte[] packet=a.encode(1,1,new byte[]{7});assertArrayEquals(new byte[]{7},b.decode(packet).body());
        assertThrows(GeneralSecurityException.class,()->new UdpPacket(new byte[16],new byte[32],false,3).decode(packet));
        assertThrows(GeneralSecurityException.class,()->a.decode(packet));
    }
    @Test void reorderedPlayerStreamsRemainIndependentAndCannotResurrectAnOldStart() {
        var latest = new LatestUdpPoses(); UUID a = new UUID(1, 1), b = new UUID(2, 2);
        var pose = new BlockingTransport.Pose(com.customcosmetics.features.blocking.BlockHand.MAIN, 750);
        latest.accept(10, Map.of(a, pose), 0);
        latest.accept(12, Map.of(a, new BlockingTransport.Pose(com.customcosmetics.features.blocking.BlockHand.NONE, 0)), 0);
        assertEquals(Set.of(b), latest.accept(11, Map.of(a, pose, b, pose), 0).keySet());
        assertTrue(latest.accept(13, Map.of(), 751).isEmpty());
        latest.clear(); assertEquals(Set.of(a), latest.accept(1, Map.of(a, pose), 0).keySet());
    }
    @Test void independentFlowsAndDirectionsAuthenticateTheWholeHeaderAndBody() throws Exception {
        var client = new UdpPacket(new byte[16], new byte[32], true);
        var server = new UdpPacket(new byte[16], new byte[32], false);
        var state = client.encode(1, 1, new byte[]{1, 2, 3});
        assertArrayEquals(new byte[]{1, 2, 3}, server.decode(state).body());
        assertThrows(GeneralSecurityException.class, () -> client.decode(state));
        assertFalse(Arrays.equals(state, client.encode(2, 1, new byte[]{1, 2, 3})));
        assertArrayEquals(new byte[]{4}, client.decode(server.encode(2, 7, new byte[]{4})).body());
        for (int index : new int[]{0, 4, 5, 21, 29, state.length - 1}) {
            byte[] tampered = state.clone(); tampered[index] ^= 1;
            assertThrows(GeneralSecurityException.class, () -> server.decode(tampered));
        }
        assertThrows(GeneralSecurityException.class, () -> server.decode(new byte[44]));
        assertThrows(GeneralSecurityException.class, () -> client.encode(1, 0, new byte[0]));
        assertThrows(GeneralSecurityException.class, () -> client.encode(1, 1, new byte[1156]));
        assertEquals(1200, client.encode(1, 1, new byte[1155]).length);
    }
    @Test void staleWorldOrMalformedSnapshotsCannotApplyButUninterestedPlayersAreFiltered() {
        UUID a = new UUID(1, 2), b = new UUID(3, 4);
        var body = snapshot(5, record(a, 1, "skyblock:HYPERION"), record(b, 2, "skyblock:ASPECT_OF_THE_VOID"));
        var result = UdpPresenceClient.parseSnapshot(body, 5, Set.of(a), 100);
        assertEquals(Set.of(a), result.keySet()); assertEquals(850, result.get(a).expiresAt());
        assertEquals("skyblock:HYPERION", result.get(a).itemId());
        assertThrows(IllegalArgumentException.class, () -> UdpPresenceClient.parseSnapshot(body, 6, Set.of(a, b), 100));
        assertThrows(IllegalArgumentException.class, () -> UdpPresenceClient.parseSnapshot(Arrays.copyOf(body, body.length - 1), 5, Set.of(a), 100));
        assertEquals(32 + UdpInterest.BYTES, UdpPresenceClient.scopeBytes(new UdpPresenceClient.Scope("", java.util.stream.LongStream.range(0, 1000).mapToObj(i -> new UUID(0, i)).collect(java.util.stream.Collectors.toSet()))).length);
    }
    @Test void invalidIdsOldRecordsTrailingDataAndOversizedSnapshotsAreRejected() {
        UUID a = new UUID(1,2);
        for (String id : new String[]{"", "minecraft:BOW", "skyblock:hyperion", "skyblock:<script>", "skyblock:" + "A".repeat(129)}) {
            var body = snapshot(5, record(a, 1, id));
            assertThrows(IllegalArgumentException.class, () -> UdpPresenceClient.parseSnapshot(body, 5, Set.of(a), 0), id);
        }
        var old = ByteBuffer.allocate(28).putLong(5).put((byte)1).putLong(1).putLong(2).put((byte)1).putShort((short)750).array();
        assertThrows(IllegalArgumentException.class, () -> UdpPresenceClient.parseSnapshot(old, 5, Set.of(a), 0));
        var valid = snapshot(5, record(a, 1, "minecraft:iron_sword"));
        assertThrows(IllegalArgumentException.class, () -> UdpPresenceClient.parseSnapshot(Arrays.copyOf(valid, valid.length + 1), 5, Set.of(a), 0));
        assertThrows(IllegalArgumentException.class, () -> UdpPresenceClient.parseSnapshot(new byte[1156], 0, Set.of(a), 0));
        var stop = UdpPresenceClient.parseSnapshot(snapshot(5, record(a, 0, "")), 5, Set.of(a), 100).get(a);
        assertEquals(BlockHand.NONE, stop.hand()); assertEquals("", stop.itemId());
    }
    @Test void senderStateBindsTheHandAndCanonicalItemAndStopsCarryNoItem() {
        var state = new UdpPresenceClient.State(BlockHand.MAIN, "skyblock:HYPERION");
        var buffer = ByteBuffer.wrap(UdpPresenceClient.stateBytes(7, state));
        assertEquals(7, buffer.getLong()); assertEquals(1, buffer.get()); assertEquals(17, buffer.get());
        assertEquals("skyblock:HYPERION", StandardCharsets.US_ASCII.decode(buffer).toString());
        assertEquals(10, UdpPresenceClient.stateBytes(7, new UdpPresenceClient.State(BlockHand.NONE, "skyblock:HYPERION")).length);
        assertThrows(IllegalArgumentException.class, () -> new UdpPresenceClient.State(BlockHand.MAIN, ""));
        assertThrows(IllegalArgumentException.class, () -> new UdpPresenceClient.State(BlockHand.OFF, "skyblock:hyperion"));
    }
    private static byte[] record(UUID uuid, int hand, String id) {
        byte[] item = id.getBytes(StandardCharsets.US_ASCII);
        return ByteBuffer.allocate(20 + item.length).putLong(uuid.getMostSignificantBits()).putLong(uuid.getLeastSignificantBits())
            .put((byte)hand).putShort((short)(hand == 0 ? 0 : 750)).put((byte)item.length).put(item).array();
    }
    private static byte[] snapshot(long control, byte[]... records) {
        var bytes = ByteBuffer.allocate(9 + Arrays.stream(records).mapToInt(r -> r.length).sum()).putLong(control).put((byte)records.length);
        for (var record : records) bytes.put(record);
        return bytes.array();
    }
}
