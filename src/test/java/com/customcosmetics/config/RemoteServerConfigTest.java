package com.customcosmetics.config;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import com.google.gson.JsonParser;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class RemoteServerConfigTest {
    @TempDir Path directory;
    private RemoteServerConfig.Endpoint previous;
    @BeforeEach void snapshot() { previous = RemoteServerConfig.current(); }
    @AfterEach void restore() {
        ConfigManager.syncServerIp = previous.host(); ConfigManager.syncServerPort = previous.port(); ConfigManager.syncServerHttps = previous.https();
        ConfigManager.syncServerSni = previous.sni(); ConfigManager.syncUdpHost = previous.udpHost(); ConfigManager.syncUdpPort = previous.udpPort(); ConfigManager.syncBlockingTransport = previous.transport();
    }
    @ParameterizedTest @ValueSource(strings={"", "  ", "{}", "{\"syncServerIp\":\"\",\"syncServerPort\":null,\"syncServerSni\":\"\"}"})
    void emptyOverridesUseTheBundledDomain(String json) { assertEquals(RemoteServerConfig.DEFAULT,RemoteServerConfig.parse(json)); }
    @Test void initializationCreatesABlankTemplateAndNeverRewritesUserValues() throws Exception {
        var path = directory.resolve("customremoteserver.json"); RemoteServerConfig.init(path);
        var object = JsonParser.parseString(Files.readString(path)).getAsJsonObject(); assertEquals(7,object.size());
        assertTrue(object.entrySet().stream().allMatch(entry -> entry.getValue().getAsString().isEmpty()));
        assertEquals(RemoteServerConfig.DEFAULT,RemoteServerConfig.current());
        String saved = "{\"syncServerIp\":\"localhost\",\"syncServerPort\":25577,\"syncServerHttps\":false,\"syncUdpHost\":\"127.0.0.1\",\"syncUdpPort\":30000}";
        Files.writeString(path,saved); RemoteServerConfig.init(path); assertEquals(saved,Files.readString(path));
        assertEquals("localhost",ConfigManager.syncServerIp); assertEquals(25577,ConfigManager.syncServerPort);
        assertFalse(ConfigManager.syncServerHttps); assertTrue(ConfigManager.syncServerSni); assertEquals(30000,ConfigManager.syncUdpPort);
    }
    @Test void partialOverridesNormalizeNamesAndPreserveExplicitBooleanFlags() {
        var parsed = RemoteServerConfig.parse("{\"syncServerIp\":\" Cosmetics.Example. \",\"syncServerSni\":false,\"syncUdpPort\":\"30000\"}");
        assertEquals("cosmetics.example",parsed.host()); assertFalse(parsed.sni()); assertTrue(parsed.https());
        assertEquals("cc.toryhi.com",parsed.udpHost()); assertEquals(30000,parsed.udpPort());
    }
    @Test void udpCanBeSelectedExplicitlyAndUnknownTransportsAreRejected() {
        assertEquals("udp",RemoteServerConfig.parse("{\"syncBlockingTransport\":\"udp\"}").transport());
        assertEquals("auto",RemoteServerConfig.parse("{}").transport());
        assertEquals("wss",RemoteServerConfig.parse("{\"syncBlockingTransport\":\"wss\"}").transport());
        assertThrows(IllegalArgumentException.class,()->RemoteServerConfig.parse("{\"syncBlockingTransport\":\"tcp\"}"));
    }
    @Test void editingAndClearingTheFileChangesTheEffectiveConnectionImmediately() throws Exception {
        var path = directory.resolve("customremoteserver.json"); RemoteServerConfig.init(path);
        Files.writeString(path,"{\"syncServerPort\":30001,\"syncUdpPort\":30002}"); assertTrue(RemoteServerConfig.reload(true));
        assertEquals(30001,ConfigManager.syncServerPort); assertEquals(30002,ConfigManager.syncUdpPort);
        assertFalse(RemoteServerConfig.reload(true)); Files.writeString(path,""); assertTrue(RemoteServerConfig.reload(true));
        assertEquals(RemoteServerConfig.DEFAULT,RemoteServerConfig.current());
    }
    @Test void malformedEditsDoNotApplyPartOfANewEndpoint() throws Exception {
        var path = directory.resolve("customremoteserver.json"); RemoteServerConfig.init(path);
        Files.writeString(path,"{\"syncServerPort\":30001,\"syncUdpPort\":70000}"); assertFalse(RemoteServerConfig.reload(true));
        assertEquals(RemoteServerConfig.DEFAULT,RemoteServerConfig.current());
    }
    @Test void anExplicitProtocolOverrideTakesPriorityOverTheBuiltInProtocol() throws Exception {
        var path = directory.resolve("customremoteserver.json"); RemoteServerConfig.init(path);
        Files.writeString(path,"{\"syncServerHttps\":false}"); assertTrue(RemoteServerConfig.reload(true));
        assertEquals("http://cc.toryhi.com:443",com.customcosmetics.sync.SyncEndpoint.baseUrl());
    }
    @ParameterizedTest @ValueSource(strings={"[]", "{", "{\"syncServerPort\":70000}","{\"syncUdpPort\":0}","{\"syncServerIp\":\"https://example.com/path\"}","{\"syncServerHttps\":\"maybe\"}","{\"syncServerSni\":[]}"})
    void rejectsInvalidOverrides(String json) { assertThrows(RuntimeException.class,() -> RemoteServerConfig.parse(json)); }
}
