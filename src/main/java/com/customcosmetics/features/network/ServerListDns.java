// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.features.network;

import javax.naming.NamingException;
import javax.naming.directory.Attributes;
import javax.naming.directory.BasicAttributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import java.net.InetAddress;
import java.util.Hashtable;

public final class ServerListDns {
    public static final ScopedValue<Boolean> PING_LOOKUP = ScopedValue.newInstance();
    private static final String SRV_TIMEOUT_MILLIS = "500";

    private ServerListDns() {}

    public static Attributes querySrv(DirContext shared, String host, String query, String[] types) throws NamingException {
        try {
            InetAddress.ofLiteral(host);
            return new BasicAttributes(true);
        } catch (IllegalArgumentException hostname) {
            // SRV redirects apply to hostnames.
        }
        var environment = new Hashtable<Object, Object>(shared.getEnvironment());
        environment.put("com.sun.jndi.dns.timeout.initial", SRV_TIMEOUT_MILLIS);
        environment.put("com.sun.jndi.dns.timeout.retries", "1");
        // Each lookup has its own DNS client and cannot hold up another list entry.
        var context = new InitialDirContext(environment);
        try {
            return context.getAttributes(query, types);
        } finally {
            context.close();
        }
    }
}
