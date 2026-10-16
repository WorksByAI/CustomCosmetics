package com.customcosmetics.update;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class GitHubUpdateCheckTest {
    @Test void miniMeBetaDoesNotNotifyStableUsersButCanUpgradeToTheNextStableRelease() {
        String beta="{\"draft\":false,\"prerelease\":true,\"tag_name\":\"v1.4.3b1\"}";
        assertTrue(GitHubUpdateCheck.newerRelease(beta,"1.4.2").isEmpty());
        assertTrue(GitHubUpdateCheck.newerRelease(release("v1.4.2"),"1.4.3-beta.1").isEmpty());
        assertEquals(Optional.of("1.4.3"),GitHubUpdateCheck.newerRelease(release("v1.4.3"),"1.4.3-beta.1"));
    }
    @Test void release142NotifiesPriorStableAndTestClientsAndStaysSilentOn142() {
        for(String installed:new String[]{"1.4.0","1.4.1","1.4.1-dev.1","1.4.2-dev.1"})
            assertEquals(Optional.of("1.4.2"),GitHubUpdateCheck.newerRelease(release("v1.4.2"),installed));
        assertTrue(GitHubUpdateCheck.newerRelease(release("v1.4.2"),"1.4.2").isEmpty());
    }
    @Test void release141Notifies140UsersAndStaysSilentOn141() {
        assertEquals(Optional.of("1.4.1"), GitHubUpdateCheck.newerRelease(release("v1.4.1"), "1.4.0"));
        assertTrue(GitHubUpdateCheck.newerRelease(release("v1.4.1"), "1.4.1").isEmpty());
    }
    @Test void partyReleaseUpdatesPreviousStableAndTestBuildsWithoutNotifyingTheCurrentRelease() {
        for(String installed:new String[]{"1.2.1","1.3.0-dev.4","1.3.0-dev.5"})
            assertEquals(Optional.of("1.3.0"),GitHubUpdateCheck.newerRelease(release("v1.3.0"),installed));
        assertTrue(GitHubUpdateCheck.newerRelease(release("v1.3.0"),"1.3.0").isEmpty());
    }
    @Test void segmentedReleaseNotifiesOlderClientsAndStaysSilentOnTheCurrentVersion() {
        assertEquals(Optional.of("1.1.1"),GitHubUpdateCheck.newerRelease(release("v1.1.1"),"1.1.0"));
        assertTrue(GitHubUpdateCheck.newerRelease(release("v1.1.1"),"1.1.1").isEmpty());
    }
    private static String release(String tag) {
        return "{\"draft\":false,\"prerelease\":false,\"tag_name\":\"" + tag + "\"}";
    }
    @ParameterizedTest
    @CsvSource({"v1.0.1,1.0.0", "V2.0.0,1.9.9", "1.10.0,1.9.9", "1.0.0,1.0.0-beta.2"})
    void newerStableVersionsUseSemanticOrdering(String tag, String installed) {
        assertTrue(GitHubUpdateCheck.newerRelease(release(tag), installed).isPresent());
    }
    @Test void installingTheCurrentStableReleaseDoesNotCreateAnUpdateNotice() {
        assertEquals(Optional.of("1.0.1"), GitHubUpdateCheck.newerRelease(release("v1.0.1"), "1.0.0"));
        assertTrue(GitHubUpdateCheck.newerRelease(release("v1.0.1"), "1.0.1").isEmpty());
    }
    @ParameterizedTest
    @ValueSource(strings={"v1.0.0", "v0.9.9", "1.0.0+build.9", "1.1.0-beta.1", "latest", "https://evil.example", "1.0", "2.0.0\nclick me"})
    void equalOlderPrereleaseAndInvalidTagsStaySilent(String tag) {
        assertTrue(GitHubUpdateCheck.newerRelease(release(tag), "1.0.0").isEmpty());
    }
    @ParameterizedTest
    @ValueSource(strings={"null", "[]", "<html>unavailable</html>", "{}", "{\"draft\":true,\"prerelease\":false,\"tag_name\":\"v2.0.0\"}",
        "{\"draft\":false,\"prerelease\":true,\"tag_name\":\"v2.0.0\"}",
        "{\"draft\":\"false\",\"prerelease\":false,\"tag_name\":\"v2.0.0\"}",
        "{\"draft\":false,\"prerelease\":false,\"tag_name\":2}"})
    void badResponsesAndNonStableReleasesCannotCreateANotice(String json) {
        assertTrue(GitHubUpdateCheck.newerRelease(json, "1.0.0").isEmpty());
    }
    @Test void checkRunsOnceOffTheCallingThreadAndQueuesOnlyOneWorldNotice() {
        var check = new GitHubUpdateCheck("1.0.0");
        var tasks = new ArrayList<Runnable>(); var calls = new AtomicInteger();
        check.start(() -> { calls.incrementAndGet(); return Optional.of("1.0.1"); }, tasks::add);
        check.start(() -> { fail("Second startup must not check again"); return Optional.empty(); }, tasks::add);
        assertEquals(1, tasks.size()); assertEquals(0, calls.get()); assertNull(check.poll(true));
        tasks.getFirst().run(); assertEquals(1, calls.get());
        assertNull(check.poll(false)); assertNull(check.poll(false));
        assertEquals("1.0.1", check.poll(true)); assertNull(check.poll(true));
        assertNull(check.poll(false)); assertNull(check.poll(true)); // Reconnecting does not repeat it.
    }
    @Test void unavailableCheckDoesNotCrashOrProduceChatMessages() {
        var check = new GitHubUpdateCheck("1.0.0");
        assertDoesNotThrow(() -> check.start(() -> { throw new IllegalStateException("Network unavailable"); }, Runnable::run));
        assertNull(check.poll(true));
        assertTrue(GitHubUpdateCheck.newerRelease(release("2.0.0"), "development").isEmpty());
    }
    @Test void httpCheckUsesNoCredentialsBoundsResponsesAndNeverFollowsRedirects() throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        var redirected = new AtomicInteger(); var requested = new AtomicInteger();
        server.createContext("/latest", exchange -> {
            requested.incrementAndGet();
            assertEquals("GET", exchange.getRequestMethod());
            assertNull(exchange.getRequestHeaders().getFirst("Authorization"));
            assertNull(exchange.getRequestHeaders().getFirst("Cookie"));
            assertEquals("application/vnd.github+json", exchange.getRequestHeaders().getFirst("Accept"));
            assertEquals("2026-03-10", exchange.getRequestHeaders().getFirst("X-GitHub-Api-Version"));
            byte[] response = release("v1.0.1").getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length); exchange.getResponseBody().write(response); exchange.close();
        });
        server.createContext("/redirect", exchange -> {
            exchange.getResponseHeaders().set("Location", "/target"); exchange.sendResponseHeaders(301,-1); exchange.close();
        });
        server.createContext("/target", exchange -> { redirected.incrementAndGet(); exchange.sendResponseHeaders(200,-1); exchange.close(); });
        for (int status : new int[]{404,429,500}) server.createContext("/" + status, exchange -> { exchange.sendResponseHeaders(status,-1); exchange.close(); });
        server.createContext("/large", exchange -> {
            byte[] bytes = new byte[70_000]; exchange.sendResponseHeaders(200,bytes.length); exchange.getResponseBody().write(bytes); exchange.close();
        });
        server.start();
        try {
            String base = "http://127.0.0.1:" + server.getAddress().getPort();
            assertEquals(Optional.of("1.0.1"), GitHubUpdateCheck.fetch(URI.create(base + "/latest"), "1.0.0"));
            assertEquals(1, requested.get());
            for (String path : new String[]{"/redirect","/404","/429","/500","/large"})
                assertTrue(GitHubUpdateCheck.fetch(URI.create(base + path),"1.0.0").isEmpty(),path);
            assertEquals(0, redirected.get());
        } finally { server.stop(0); }
    }
}
