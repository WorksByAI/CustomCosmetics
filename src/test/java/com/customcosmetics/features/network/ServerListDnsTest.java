// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.features.network;

import org.junit.jupiter.api.Test;

import javax.naming.CommunicationException;
import javax.naming.Context;
import javax.naming.directory.InitialDirContext;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.Hashtable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class ServerListDnsTest {
    private static final String[] SRV = { "SRV" };

    @Test void aDroppedQueryDoesNotBlockAnotherServersRedirect() throws Exception {
        var loopback = InetAddress.ofLiteral("127.0.0.1");
        try (var server = new DatagramSocket(0, loopback);
             var workers = Executors.newVirtualThreadPerTaskExecutor()) {
            server.setSoTimeout(2500);
            var environment = new Hashtable<String, String>();
            environment.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.dns.DnsContextFactory");
            environment.put(Context.PROVIDER_URL, "dns://127.0.0.1:" + server.getLocalPort());
            environment.put("com.sun.jndi.dns.timeout.initial", "4000");
            var shared = new InitialDirContext(environment);
            try {
                var dropped = workers.submit(() -> ServerListDns.querySrv(shared, "slow.test", "_minecraft._tcp.slow.test", SRV));
                var first = receive(server);
                var responding = workers.submit(() -> ServerListDns.querySrv(shared, "fast.test", "_minecraft._tcp.fast.test", SRV));
                var second = receive(server);
                assertNotEquals(first.getPort(), second.getPort(), "Concurrent queries must use separate DNS clients");
                assertFalse(dropped.isDone(), "The working query must reach DNS while the first is still waiting");

                var answer = srvReply(second);
                server.send(new DatagramPacket(answer, answer.length, second.getSocketAddress()));
                var attributes = responding.get(2, TimeUnit.SECONDS);
                assertEquals("0 0 25575 target.test.", attributes.get("SRV").get());
                var failure = assertThrows(ExecutionException.class, () -> dropped.get(2, TimeUnit.SECONDS));
                assertInstanceOf(CommunicationException.class, failure.getCause());
                assertEquals("4000", shared.getEnvironment().get("com.sun.jndi.dns.timeout.initial"));
            } finally {
                shared.close();
            }
        }
    }

    @Test void literalAddressesNeedNoSrvLookup() throws Exception {
        for (String host : new String[]{ "127.0.0.1", "::1" }) {
            assertEquals(0, ServerListDns.querySrv(null, host, "", SRV).size());
        }
    }

    private static DatagramPacket receive(DatagramSocket server) throws IOException {
        var request = new DatagramPacket(new byte[512], 512);
        server.receive(request);
        return request;
    }

    private static byte[] srvReply(DatagramPacket request) throws IOException {
        var response = new ByteArrayOutputStream();
        try (var out = new DataOutputStream(response)) {
            out.write(request.getData(), 0, 2); // Transaction ID
            out.writeShort(0x8180);
            out.writeShort(1);
            out.writeShort(1);
            out.writeInt(0);
            out.write(request.getData(), 12, request.getLength() - 12);
            out.writeShort(0xC00C); // Owner name points to the question.
            out.writeShort(33); // SRV
            out.writeShort(1); // IN
            out.writeInt(60);
            out.writeShort(19);
            out.writeShort(0);
            out.writeShort(0);
            out.writeShort(25575);
            out.writeByte(6);
            out.writeBytes("target");
            out.writeByte(4);
            out.writeBytes("test");
            out.writeByte(0);
        }
        return response.toByteArray();
    }
}
