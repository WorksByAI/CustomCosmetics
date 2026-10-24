package com.customcosmetics.sync;

import com.customcosmetics.config.ConfigManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class SyncEndpointTest {
    @Test void unchangedConfigurationReusesUrlAndEveryEndpointFieldInvalidatesIt() {
        String oldHost=ConfigManager.syncServerIp;int oldPort=ConfigManager.syncServerPort;boolean oldHttps=ConfigManager.syncServerHttps;
        try {
            ConfigManager.syncServerIp="Example.Com";ConfigManager.syncServerPort=443;ConfigManager.syncServerHttps=true;
            String first=SyncEndpoint.baseUrl();
            assertSame(first,SyncEndpoint.baseUrl());
            ConfigManager.syncServerIp=new String("Example.Com");assertSame(first,SyncEndpoint.baseUrl());
            ConfigManager.syncServerPort=8443;assertEquals("https://example.com:8443",SyncEndpoint.baseUrl());
            ConfigManager.syncServerHttps=false;assertEquals("http://example.com:8443",SyncEndpoint.baseUrl());
            ConfigManager.syncServerIp="next.example";assertEquals("http://next.example:8443",SyncEndpoint.baseUrl());
            ConfigManager.syncServerIp=null;assertEquals("",SyncEndpoint.baseUrl());
        } finally {ConfigManager.syncServerIp=oldHost;ConfigManager.syncServerPort=oldPort;ConfigManager.syncServerHttps=oldHttps;}
    }
    @ParameterizedTest
    @CsvSource({
        "192.168.0.3, 192.168.0.3",
        "192.168.000.003, 192.168.0.3",
        "0.0.0.0, 0.0.0.0",
        "255.255.255.255, 255.255.255.255",
        "cosmetics.example, cosmetics.example",
        "'  Cosmetics.Example  ', cosmetics.example",
        "cosmetics.example., cosmetics.example",
        "my-cosmetics.example, my-cosmetics.example",
        "localhost, localhost",
        "例子.测试, xn--fsqu00a.xn--0zwm56d"
    })
    void normalizesIpv4AndDnsNames(String input, String expected) {
        assertEquals(expected, SyncEndpoint.normalizeHost(input));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   "})
    void allowsClearingTheServerAddress(String input) {
        assertEquals("", SyncEndpoint.normalizeHost(input));
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "256.168.0.3", "192.168.0", "127.1", "2130706433",
        "http://cosmetics.example", "https://cosmetics.example", "cosmetics.example:25577",
        "user@cosmetics.example", "cosmetics.example/users.json", "cosmetics.example?token=secret",
        "cosmetics.example#fragment", "cosmetics example", "bad_host.example",
        "-bad.example", "bad-.example", "bad..example", ".example", ".", "::1", "[::1]"
    })
    void rejectsMalformedHostsAndUrlComponents(String input) {
        assertNull(SyncEndpoint.normalizeHost(input));
    }

    @Test
    void acceptsLongDomainsAndEnforcesDnsLengthLimits() {
        String longest = "a".repeat(63) + "." + "b".repeat(63) + "." + "c".repeat(63) + "." + "d".repeat(61);
        assertEquals(253, longest.length());
        assertEquals(longest, SyncEndpoint.normalizeHost(longest));
        assertNull(SyncEndpoint.normalizeHost(longest + "d"));
        assertNull(SyncEndpoint.normalizeHost("a".repeat(64) + ".example"));
    }

    @ParameterizedTest
    @CsvSource({
        "' Cosmetics.Example ', 25577, http://cosmetics.example:25577",
        "localhost, 1, http://localhost:1",
        "例子.测试, 65535, http://xn--fsqu00a.xn--0zwm56d:65535",
        "192.168.000.003, 25577, http://192.168.0.3:25577"
    })
    void buildsConfiguredEndpointsAcceptedByTheRequestGuard(String host, int port, String expected) {
        String oldHost = ConfigManager.syncServerIp;
        int oldPort = ConfigManager.syncServerPort;
        boolean oldHttps = ConfigManager.syncServerHttps;
        try {
            ConfigManager.syncServerIp = host;
            ConfigManager.syncServerPort = port;
            ConfigManager.syncServerHttps = false;
            String base = SyncEndpoint.baseUrl();
            assertEquals(expected, base);
            assertDoesNotThrow(() -> PresenceAuthClient.validateBaseUrl(base));
            assertDoesNotThrow(() -> PresenceAuthClient.validateRequestUrl(base + "/users.json"));
        } finally {
            ConfigManager.syncServerIp = oldHost;
            ConfigManager.syncServerPort = oldPort;
            ConfigManager.syncServerHttps = oldHttps;
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "bad_host.example", "http://cosmetics.example", "256.168.0.3"})
    void emptyOrInvalidConfiguredHostDisablesRequests(String host) {
        String oldHost = ConfigManager.syncServerIp;
        try {
            ConfigManager.syncServerIp = host;
            assertEquals("", SyncEndpoint.baseUrl());
        } finally { ConfigManager.syncServerIp = oldHost; }
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, 65536})
    void invalidSavedPortsUseTheSelectedProtocolsDefault(int port) {
        int oldPort = ConfigManager.syncServerPort;
        boolean oldHttps = ConfigManager.syncServerHttps;
        try {
            ConfigManager.syncServerPort = port;
            ConfigManager.syncServerHttps = false;
            assertEquals(80, ConfigManager.getSyncServerPort());
            ConfigManager.syncServerHttps = true;
            assertEquals(443, ConfigManager.getSyncServerPort());
        } finally {
            ConfigManager.syncServerPort = oldPort;
            ConfigManager.syncServerHttps = oldHttps;
        }
    }

    @ParameterizedTest
    @CsvSource({
        "cc.toryhi.com, 443, https://cc.toryhi.com:443",
        "' Cosmetics.Example ', 8443, https://cosmetics.example:8443",
        "例子.测试, 443, https://xn--fsqu00a.xn--0zwm56d:443"
    })
    void buildsHttpsEndpointsAcceptedForAllSyncOperations(String host, int port, String expected) {
        String oldHost = ConfigManager.syncServerIp;
        int oldPort = ConfigManager.syncServerPort;
        boolean oldHttps = ConfigManager.syncServerHttps;
        try {
            ConfigManager.syncServerIp = host;
            ConfigManager.syncServerPort = port;
            ConfigManager.syncServerHttps = true;
            String base = SyncEndpoint.baseUrl();
            assertEquals(expected, base);
            assertDoesNotThrow(() -> PresenceAuthClient.validateBaseUrl(base));
            for (String path : new String[]{"/health", "/users.json", "/auth/challenge", "/register", "/report", "/auth/logout"}) {
                assertDoesNotThrow(() -> PresenceAuthClient.validateRequestUrl(base + path));
            }
        } finally {
            ConfigManager.syncServerIp = oldHost;
            ConfigManager.syncServerPort = oldPort;
            ConfigManager.syncServerHttps = oldHttps;
        }
    }

    @Test
    void changingProtocolChangesTheAuthenticationOrigin() {
        String oldHost = ConfigManager.syncServerIp;
        int oldPort = ConfigManager.syncServerPort;
        boolean oldHttps = ConfigManager.syncServerHttps;
        try {
            ConfigManager.syncServerIp = "cosmetics.example";
            ConfigManager.syncServerPort = 443;
            ConfigManager.syncServerHttps = false;
            var player = java.util.UUID.fromString("00000000-0000-0000-0000-000000000001");
            var session = new PresenceAuthClient.Session("test", player, "TestPlayer",
                System.currentTimeMillis() + 3_600_000, SyncEndpoint.baseUrl());
            ConfigManager.syncServerHttps = true;
            assertFalse(session.validFor(SyncEndpoint.baseUrl(), player, "TestPlayer"));
            ConfigManager.syncServerHttps = false;
            assertTrue(session.validFor(SyncEndpoint.baseUrl(), player, "TestPlayer"));
        } finally {
            ConfigManager.syncServerIp = oldHost;
            ConfigManager.syncServerPort = oldPort;
            ConfigManager.syncServerHttps = oldHttps;
        }
    }

    @ParameterizedTest
    @CsvSource({
        "cc.toryhi.com, 443, false, false",
        "cc.toryhi.com, 24443, false, false",
        "' CC.TORYHI.COM. ', 443, false, false",
        "cc.toryhi.com, 80, false, false",
        "custom.example, 443, false, false",
        "192.168.0.3, 443, false, false",
        "192.168.0.3, 25577, false, false",
        "custom.example, 8443, true, true"
    })
    void respectsTheExplicitProtocolOnEveryHost(String host, int port, boolean requested, boolean expected) {
        String oldHost = ConfigManager.syncServerIp; int oldPort = ConfigManager.syncServerPort; boolean oldHttps = ConfigManager.syncServerHttps;
        try { ConfigManager.syncServerIp = host; ConfigManager.syncServerPort = port; ConfigManager.syncServerHttps = requested;
            assertTrue(SyncEndpoint.baseUrl().startsWith(expected ? "https://" : "http://"));
        } finally { ConfigManager.syncServerIp = oldHost; ConfigManager.syncServerPort = oldPort; ConfigManager.syncServerHttps = oldHttps; }
    }

    @Test
    void explicitHttpIsNotSilentlyRewrittenForThePublicHost() {
        String oldHost = ConfigManager.syncServerIp;
        int oldPort = ConfigManager.syncServerPort;
        boolean oldHttps = ConfigManager.syncServerHttps;
        try {
            ConfigManager.syncServerIp = "cc.toryhi.com";
            ConfigManager.syncServerPort = 443;
            ConfigManager.syncServerHttps = false;
            String base = SyncEndpoint.baseUrl();
            assertEquals("http://cc.toryhi.com:443", base);
            assertDoesNotThrow(() -> PresenceAuthClient.validateBaseUrl(base));
            assertDoesNotThrow(() -> PresenceAuthClient.validateRequestUrl(base + "/users.json"));
        } finally {
            ConfigManager.syncServerIp = oldHost;
            ConfigManager.syncServerPort = oldPort;
            ConfigManager.syncServerHttps = oldHttps;
        }
    }
}
