// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.sync;

import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.util.Collection;
import java.util.UUID;

/** A fixed-size prefilter avoids truncating loaded players; the client still checks the exact UUID set. */
public final class UdpInterest {
    public static final int BYTES = 512;
    private UdpInterest() {}
    public static byte[] encode(Collection<UUID> players) {
        byte[] bits = new byte[BYTES];
        for (var id : players) for (int index : indexes(id)) bits[index / 8] |= (byte) (1 << (index % 8));
        return bits;
    }
    static int[] indexes(UUID id) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(ByteBuffer.allocate(16)
                .putLong(id.getMostSignificantBits()).putLong(id.getLeastSignificantBits()).array());
            var data = ByteBuffer.wrap(digest); int[] result = new int[4];
            for (int i = 0; i < 4; i++) result[i] = Short.toUnsignedInt(data.getShort()) % (BYTES * 8);
            return result;
        } catch (java.security.NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
    }
}
