package com.customcosmetics.sync;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletionException;
import javax.net.ssl.SSLHandshakeException;

import static com.customcosmetics.sync.SyncFailure.*;
import static org.junit.jupiter.api.Assertions.*;

class SyncFailureTest {
    @ParameterizedTest
    @CsvSource({
        "401, invalid_or_expired_token, INVALID_SYNC_TOKEN",
        "401, authentication_required, AUTH_REQUIRED",
        "401, identity_not_verified, ACCOUNT_NOT_VERIFIED",
        "403, identity_mismatch, ACCOUNT_MISMATCH",
        "403, uploads_blocked, UPLOADS_BLOCKED",
        "403, settings_locked, SETTINGS_LOCKED",
        "401, expired_or_used_challenge, CHALLENGE_EXPIRED",
        "503, identity_provider_unavailable, LOGIN_SERVICE_UNAVAILABLE",
        "503, identity_provider_invalid_response, LOGIN_SERVICE_UNAVAILABLE",
        "400, invalid_name, INVALID_ACCOUNT_NAME",
        "400, invalid_payload, INVALID_DATA",
        "400, invalid_json, INVALID_DATA",
        "400, invalid_proof, INVALID_DATA",
        "429, rate_limited, RATE_LIMITED",
        "500, internal_error, SERVER_ERROR"
    })
    void mapsBackendErrorsToSpecificReasons(int status, String code, Reason reason) {
        SyncFailure failure = fromHttp(Stage.UPLOAD, status, code);
        assertEquals(reason, failure.reason());
        assertEquals(status, failure.httpStatus());
        assertTrue(failure.description().contains("HTTP " + status));
    }

    @ParameterizedTest
    @ValueSource(ints = {401, 403})
    void distinguishesMinecraftLoginFromTheSyncServiceToken(int status) {
        assertEquals(Reason.INVALID_MINECRAFT_SESSION, fromHttp(Stage.MINECRAFT_AUTH, status, "").reason());
        assertEquals(Reason.INVALID_SYNC_TOKEN, fromHttp(Stage.UPLOAD, status, "invalid_or_expired_token").reason());
    }

    @ParameterizedTest
    @CsvSource({
        "301, REDIRECTED", "302, REDIRECTED", "307, REDIRECTED", "400, HTTP_ERROR",
        "401, AUTH_REQUIRED", "403, ACCESS_DENIED", "404, INVALID_SERVER_ADDRESS",
        "429, RATE_LIMITED", "500, SERVER_ERROR", "502, SERVER_ERROR", "503, SERVER_ERROR"
    })
    void unknownErrorBodiesUseOnlyTheHttpStatus(int status, Reason reason) {
        var failure = fromHttp(Stage.DOWNLOAD, status, "secret token=do-not-display");
        assertEquals(reason, failure.reason());
        assertFalse(failure.description().contains("do-not-display"));
    }

    @Test
    void unwrapsNetworkErrorsAndNeverDisplaysExceptionSecrets() {
        assertEquals(Reason.ADDRESS_NOT_FOUND, fromException(new CompletionException(new UnknownHostException("secret")), Stage.DOWNLOAD).reason());
        assertEquals(Reason.TIMEOUT, fromException(new SocketTimeoutException("secret"), Stage.DOWNLOAD).reason());
        assertEquals(Reason.CONNECTION_FAILED, fromException(new ConnectException("secret"), Stage.DOWNLOAD).reason());
        assertEquals(Reason.CONNECTION_FAILED, fromException(new CompletionException(new IOException("secret")), Stage.DOWNLOAD).reason());
        assertEquals(Reason.TLS_FAILED, fromException(new SSLHandshakeException("secret"), Stage.DOWNLOAD).reason());
        var known = of(Reason.INVALID_SYNC_TOKEN, Stage.UPLOAD);
        assertEquals(known, fromException(new CompletionException(new SyncRequestException(known)), Stage.SERVER_AUTH));
        assertFalse(fromException(new IOException("secret accessToken"), Stage.DOWNLOAD).description().contains("accessToken"));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", " ", "0"})
    void offlineCredentialsFailBeforeSendingAnyMojangRequest(String token) {
        var error = assertThrows(SyncRequestException.class,
            () -> PresenceAuthClient.joinMojang(java.util.UUID.randomUUID(), token, "test"));
        assertEquals(Reason.INVALID_MINECRAFT_SESSION, error.failure().reason());
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "{\"error\":\"invalid_or_expired_token\",\"token\":\"secret-value\"}",
        "<html>secret-value</html>", "not JSON secret-value"
    })
    void readsRealErrorResponsesWithoutDisplayingBodies(String body) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/report", exchange -> {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(401, bytes.length);
            try (var stream = exchange.getResponseBody()) { stream.write(bytes); }
            exchange.close();
        });
        server.start();
        HttpURLConnection connection = (HttpURLConnection) URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/report")
            .toURL().openConnection(Proxy.NO_PROXY);
        try {
            int status = connection.getResponseCode();
            var failure = fromResponse(connection, Stage.UPLOAD, status);
            assertEquals(body.startsWith("{") ? Reason.INVALID_SYNC_TOKEN : Reason.AUTH_REQUIRED, failure.reason());
            assertFalse(failure.description().contains("secret-value"));
        } finally { connection.disconnect(); server.stop(0); }
    }
}
