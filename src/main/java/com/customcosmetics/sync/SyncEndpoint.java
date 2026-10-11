package com.customcosmetics.sync;

import com.customcosmetics.config.ConfigManager;
import java.net.IDN;
import java.net.URI;
import java.util.Locale;

/** Built-in connection defaults may be replaced by the separate override file. */
public final class SyncEndpoint {
    public static final int MAX_HOST_LENGTH = 253;

    private SyncEndpoint() {}

    /** Normalizes host text without resolving DNS or accepting URL components. */
    public static String normalizeHost(String value) {
        if (value == null || value.isBlank()) return "";
        try {
            String host = IDN.toASCII(value.trim(), IDN.USE_STD3_ASCII_RULES).toLowerCase(Locale.ROOT);
            if (host.endsWith(".")) host = host.substring(0, host.length() - 1);
            if (host.isEmpty() || host.length() > MAX_HOST_LENGTH) return null;
            // Do not reinterpret invalid IPv4 or abbreviated numeric addresses as DNS names.
            if (host.matches("[0-9.]+")) return normalizeIp(host);
            return URI.create("http://" + host + ":1").getHost() == null ? null : host;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public static String normalizeIp(String value) {
        if (value == null || value.isBlank()) return "";
        String[] parts = value.trim().split("\\.", -1);
        if (parts.length != 4) return null;
        StringBuilder ip = new StringBuilder();
        for (String part : parts) {
            if (!part.matches("[0-9]{1,3}")) return null;
            int octet = Integer.parseInt(part);
            if (octet > 255) return null;
            if (!ip.isEmpty()) ip.append('.');
            ip.append(octet);
        }
        return ip.toString();
    }

    public static String baseUrl() {
        String host = normalizeHost(ConfigManager.syncServerIp);
        if (host == null || host.isEmpty()) return "";
        int port = ConfigManager.getSyncServerPort();
        return (ConfigManager.syncServerHttps ? "https://" : "http://") + host + ":" + port;
    }
}
