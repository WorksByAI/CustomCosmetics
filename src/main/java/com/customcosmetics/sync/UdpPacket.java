// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.sync;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Arrays;

/** Two independent latest-state flows, authenticated with direction-specific AES-GCM keys. */
public final class UdpPacket {
    public static final int MAX_SIZE = 1200, HEADER = 29, STATE = 1, CONTROL = 2;
    public record Message(int flow, long sequence, byte[] body) {}
    private final byte[] id, outgoing, incoming;
    public UdpPacket(byte[] id, byte[] master, boolean client) throws GeneralSecurityException {
        if (id.length != 16 || master.length != 32) throw new GeneralSecurityException("Invalid UDP key");
        this.id = id.clone();
        outgoing = derive(master, client ? "c2s" : "s2c"); incoming = derive(master, client ? "s2c" : "c2s");
    }
    private static byte[] derive(byte[] master, String direction) throws GeneralSecurityException {
        var mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(new byte[32], "HmacSHA256")); byte[] prk = mac.doFinal(master);
        mac.init(new SecretKeySpec(prk, "HmacSHA256"));
        mac.update(("customcosmetics:udp:v2:" + direction).getBytes(StandardCharsets.UTF_8)); return mac.doFinal(new byte[]{1});
    }
    public byte[] encode(int flow, long sequence, byte[] body) throws GeneralSecurityException {
        if ((flow != STATE && flow != CONTROL) || sequence <= 0 || body.length + HEADER + 16 > MAX_SIZE)
            throw new GeneralSecurityException("Invalid UDP packet");
        byte[] header = ByteBuffer.allocate(HEADER).putInt(0x43435032).put((byte) flow).put(id).putLong(sequence).array();
        byte[] encrypted = crypt(Cipher.ENCRYPT_MODE, outgoing, header, body);
        return ByteBuffer.allocate(header.length + encrypted.length).put(header).put(encrypted).array();
    }
    public Message decode(byte[] data) throws GeneralSecurityException {
        if (data.length < HEADER + 16 || data.length > MAX_SIZE) throw new GeneralSecurityException("Invalid UDP packet size");
        var buffer = ByteBuffer.wrap(data);
        int magic = buffer.getInt(), flow = Byte.toUnsignedInt(buffer.get()); byte[] receivedId = new byte[16]; buffer.get(receivedId);
        long sequence = buffer.getLong();
        if (magic != 0x43435032 || (flow != STATE && flow != CONTROL) || sequence <= 0 || !Arrays.equals(id, receivedId))
            throw new GeneralSecurityException("Invalid UDP header");
        byte[] header = Arrays.copyOf(data, HEADER);
        return new Message(flow, sequence, crypt(Cipher.DECRYPT_MODE, incoming, header, Arrays.copyOfRange(data, HEADER, data.length)));
    }
    private static byte[] crypt(int mode, byte[] key, byte[] header, byte[] data) throws GeneralSecurityException {
        byte[] nonce = ByteBuffer.allocate(12).putInt(header[4]).put(header, 21, 8).array();
        var cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(mode, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, nonce)); cipher.updateAAD(header);
        return cipher.doFinal(data);
    }
}
