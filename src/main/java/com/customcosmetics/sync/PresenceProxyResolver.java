/*
 * Contains code adapted from Baity: https://github.com/raueyhs/Baity
 * Copyright (C) 2025 raueyhs
 * Modifications Copyright (C) 2026 CustomCosmetics contributors
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Modified for CustomCosmetics; notices updated 2026-10-11. See LICENSE and NOTICE.
 */
package com.customcosmetics.sync;

import com.customcosmetics.config.ConfigManager;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

@Environment(EnvType.CLIENT)
public final class PresenceProxyResolver {
    private static final Logger LOGGER = LoggerFactory.getLogger("CustomCosmetics/PresenceProxy");
    private static final int CONNECT_TIMEOUT_MS = 10_000;
    private static final int READ_TIMEOUT_MS = 10_000;
    private static final int[] PROBE_PORTS = new int[]{7892, 7891, 7890};

    public enum ProxySource {
        DIRECT,
        MANUAL,
        JVM,
        SYSTEM,
        PROBE
    }

    public record ResolvedRoute(ProxySource source, Proxy proxy, String host, int port) {
        public static ResolvedRoute direct() {
            return new ResolvedRoute(ProxySource.DIRECT, null, "", 0);
        }

        public static ResolvedRoute of(ProxySource source, String host, int port) {
            if (host == null || host.isBlank() || port <= 0) {
                return direct();
            }
            InetSocketAddress addr = new InetSocketAddress(host.trim(), port);
            Proxy proxy = new Proxy(Proxy.Type.HTTP, addr);
            return new ResolvedRoute(source, proxy, host.trim(), port);
        }
    }

    private static volatile ResolvedRoute sessionRoute = ResolvedRoute.direct();
    private static volatile boolean sessionReady;

    private PresenceProxyResolver() {
    }

    public static void resetSession() {
        sessionRoute = ResolvedRoute.direct();
        sessionReady = false;
    }

    public static boolean establishSession(String healthUrl) {
        resetSession();
        if (healthUrl == null || healthUrl.isBlank()) {
            sessionReady = true;
            return false;
        }
        String mode = ConfigManager.syncProxySource;
        if ("none".equalsIgnoreCase(mode)) {
            sessionReady = true;
            return healthCheck(healthUrl,ResolvedRoute.direct());
        }

        if (hasManualProxyInConfig()) {
            ResolvedRoute manual = manualRouteFromConfig();
            if (healthCheck(healthUrl, manual)) {
                return adoptSessionRoute(manual);
            }
            if ("manual".equalsIgnoreCase(mode) && !ConfigManager.syncProxyFallbackDirect) {
                sessionRoute = manual; sessionReady = true; return false;
            }
        }

        if (healthCheck(healthUrl, ResolvedRoute.direct())) {
            return adoptSessionRoute(ResolvedRoute.direct());
        }
        if (!"auto".equalsIgnoreCase(mode)) { sessionReady = true; return false; }

        ResolvedRoute jvm = readJvmProxyRoute();
        if (jvm.source() == ProxySource.JVM && healthCheck(healthUrl, jvm)) {
            return adoptSessionRoute(jvm);
        }

        ResolvedRoute system = readSystemProxyRoute(healthUrl);
        if (system.source() == ProxySource.SYSTEM && healthCheck(healthUrl, system)) {
            return adoptSessionRoute(system);
        }

        for (int port : PROBE_PORTS) {
            ResolvedRoute probe = ResolvedRoute.of(ProxySource.PROBE, "127.0.0.1", port);
            if (!healthCheck(healthUrl, probe)) {
                continue;
            }
            return adoptSessionRoute(probe);
        }

        sessionRoute = ResolvedRoute.direct();
        sessionReady = true;
        LOGGER.warn("[PresenceProxy] no reachable route for health check");
        return false;
    }

    private static boolean adoptSessionRoute(ResolvedRoute route) {
        sessionRoute = route;
        sessionReady = true;
        LOGGER.info("[PresenceProxy] session route={} {}:{}", route.source(), route.host(), route.port());
        return true;
    }

    public static HttpURLConnection openConnection(String url, int attempt) throws Exception {
        URI uri = URI.create(url);
        ResolvedRoute route = routeForAttempt(attempt);
        HttpURLConnection conn = (HttpURLConnection) (route.proxy() == null
                ? uri.toURL().openConnection(Proxy.NO_PROXY)
                : uri.toURL().openConnection(route.proxy()));
        SyncTls.configure(conn);
        if (route.proxy() != null) {
            applyProxyAuthIfConfigured(conn,route);
        }
        return conn;
    }
    static ResolvedRoute webSocketRoute() { return routeForAttempt(0); }

    private static boolean healthCheck(String url, ResolvedRoute route) {
        HttpURLConnection connection = null;
        try {
            URI uri = URI.create(url);
            connection = (HttpURLConnection) (route.proxy() == null
                    ? uri.toURL().openConnection(Proxy.NO_PROXY)
                    : uri.toURL().openConnection(route.proxy()));
            SyncTls.configure(connection);
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setUseCaches(false);
            connection.setRequestProperty("Accept", "application/json");
            connection.setInstanceFollowRedirects(false);
            if (route.proxy() != null) {
                applyProxyAuthIfConfigured(connection,route);
            }
            int code = connection.getResponseCode();
            if (isReachableStatus(code)) {
                try (InputStream responseBody = connection.getInputStream()) {
                    responseBody.transferTo(java.io.OutputStream.nullOutputStream());
                }
                return true;
            }
        } catch (Exception ex) {
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
        return false;
    }

    private static ResolvedRoute routeForAttempt(int attempt) {
        if ("none".equalsIgnoreCase(ConfigManager.syncProxySource)) return ResolvedRoute.direct();
        if (attempt > 0 && ConfigManager.syncProxyFallbackDirect) {
            return ResolvedRoute.direct();
        }
        if (!sessionReady) {
            if ("auto".equalsIgnoreCase(ConfigManager.syncProxySource)
                    || "manual".equalsIgnoreCase(ConfigManager.syncProxySource) && ConfigManager.syncProxyFallbackDirect) {
                establishSession(SyncEndpoint.baseUrl() + "/health"); return sessionRoute;
            }
            if (hasManualProxyInConfig()) {
                return manualRouteFromConfig();
            }
            return sessionReady ? sessionRoute : ResolvedRoute.direct();
        }
        return sessionRoute;
    }

    private static boolean hasManualProxyInConfig() {
        String host = ConfigManager.syncProxyHost == null ? "" : ConfigManager.syncProxyHost.trim();
        return !host.isEmpty() && ConfigManager.syncProxyPort > 0;
    }

    private static ResolvedRoute manualRouteFromConfig() {
        return ResolvedRoute.of(ProxySource.MANUAL, ConfigManager.syncProxyHost, ConfigManager.syncProxyPort);
    }

    private static ResolvedRoute readJvmProxyRoute() {
        String host = System.getProperty("https.proxyHost");
        String portStr = System.getProperty("https.proxyPort");
        if (host == null || host.isBlank()) {
            host = System.getProperty("http.proxyHost");
            portStr = System.getProperty("http.proxyPort");
        }
        int port = parsePort(portStr);
        if (host == null || host.isBlank() || port <= 0) {
            return ResolvedRoute.direct();
        }
        return ResolvedRoute.of(ProxySource.JVM, host, port);
    }

    private static ResolvedRoute readSystemProxyRoute(String urlForUri) {
        try {
            ProxySelector selector = ProxySelector.getDefault();
            if (selector == null) {
                return ResolvedRoute.direct();
            }
            URI uri = URI.create(urlForUri);
            List<Proxy> proxies = selector.select(uri);
            if (proxies == null) {
                return ResolvedRoute.direct();
            }
            for (Proxy proxy : proxies) {
                if (proxy == null || proxy.type() != Proxy.Type.HTTP) {
                    continue;
                }
                if (!(proxy.address() instanceof InetSocketAddress addr)) {
                    continue;
                }
                String host = addr.getHostString();
                int port = addr.getPort();
                if (host != null && !host.isBlank() && port > 0) {
                    return ResolvedRoute.of(ProxySource.SYSTEM, host, port);
                }
            }
        } catch (Exception ignored) {
        }
        return ResolvedRoute.direct();
    }

    private static int parsePort(String portStr) {
        if (portStr == null || portStr.isBlank()) {
            return 0;
        }
        try {
            return Integer.parseInt(portStr.trim());
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private static void applyProxyAuthIfConfigured(HttpURLConnection conn, ResolvedRoute route) {
        if (!route.host().equalsIgnoreCase(ConfigManager.syncProxyHost == null ? "" : ConfigManager.syncProxyHost.strip())
                || route.port() != ConfigManager.syncProxyPort) return;
        String raw = ConfigManager.syncProxyAuth == null ? "" : ConfigManager.syncProxyAuth.trim();
        if (raw.isEmpty()) {
            return;
        }
        String token = Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
        conn.setRequestProperty("Proxy-Authorization", "Basic " + token);
    }

    private static boolean isReachableStatus(int code) {
        if (code == 407) {
            return false;
        }
        return code >= 200 && code < 500;
    }
}
