package com.customcosmetics.sync;

import com.google.gson.JsonParser;
import java.io.IOException;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.IdentityHashMap;
import javax.net.ssl.SSLException;

/** Safe, specific failure descriptions; server bodies and credentials are never displayed. */
public record SyncFailure(Reason reason, Stage stage, int httpStatus) {
    public enum Stage { DOWNLOAD, UPLOAD, SERVER_AUTH, MINECRAFT_AUTH, OTHER }
    public enum Reason {
        INVALID_MINECRAFT_SESSION("invalid Minecraft session; sign in again"),
        INVALID_SYNC_TOKEN("invalid or expired sync token"),
        AUTH_REQUIRED("authentication required"),
        ACCOUNT_NOT_VERIFIED("Minecraft account could not be verified"),
        ACCOUNT_MISMATCH("account identity mismatch"),
        CHALLENGE_EXPIRED("authentication challenge expired; try again"),
        LOGIN_SERVICE_UNAVAILABLE("Minecraft login service unavailable"),
        INVALID_ACCOUNT_NAME("invalid Minecraft account name"),
        INVALID_SERVER_ADDRESS("invalid server address"),
        NO_SERVER_ADDRESS("server address is empty"),
        NO_ACTIVE_WORLD("no active world"),
        INVALID_RESPONSE("invalid server response"),
        INVALID_DATA("server rejected the cosmetics data"),
        ADDRESS_NOT_FOUND("server address could not be resolved"),
        CONNECTION_FAILED("could not connect to the server"),
        TIMEOUT("connection timed out"),
        TLS_FAILED("TLS connection failed"),
        ACCESS_DENIED("access denied"),
        UPLOADS_BLOCKED("uploads disabled by the server administrator"),
        SETTINGS_LOCKED("appearance settings locked by the server administrator"),
        RATE_LIMITED("too many requests; try again later"),
        REDIRECTED("server redirected the request; check the connection settings in config.json"),
        SERVER_ERROR("server error"),
        HTTP_ERROR("request rejected"),
        UNKNOWN("unknown error");

        private final String text;
        Reason(String text) { this.text = text; }
    }

    public static SyncFailure of(Reason reason, Stage stage) { return new SyncFailure(reason, stage, 0); }
    public boolean rateLimited() { return httpStatus == 429 || reason == Reason.RATE_LIMITED; }
    public String description() {
        return reason.text + (httpStatus > 0 ? " (HTTP " + httpStatus + ")" : "");
    }

    public static SyncFailure fromResponse(HttpURLConnection connection, Stage stage, int status) {
        String code = "";
        try (var stream = connection.getErrorStream()) {
            if (stream != null) {
                byte[] body = stream.readNBytes(8193);
                if (body.length <= 8192) {
                    var json = JsonParser.parseString(new String(body, StandardCharsets.UTF_8));
                    if (json.isJsonObject()) {
                        var error = json.getAsJsonObject().get("error");
                        if (error != null && error.isJsonPrimitive() && error.getAsJsonPrimitive().isString())
                            code = error.getAsString();
                    }
                }
            }
        } catch (IOException | RuntimeException ignored) { /* HTML and malformed responses use HTTP status only. */ }
        return fromHttp(stage, status, code);
    }

    public static SyncFailure fromHttp(Stage stage, int status, String code) {
        Reason reason;
        if (stage == Stage.MINECRAFT_AUTH && (status == 401 || status == 403)) {
            reason = Reason.INVALID_MINECRAFT_SESSION;
        } else {
            reason = switch (code == null ? "" : code) {
                case "invalid_or_expired_token" -> Reason.INVALID_SYNC_TOKEN;
                case "authentication_required" -> Reason.AUTH_REQUIRED;
                case "identity_not_verified" -> Reason.ACCOUNT_NOT_VERIFIED;
                case "identity_mismatch" -> Reason.ACCOUNT_MISMATCH;
                case "uploads_blocked" -> Reason.UPLOADS_BLOCKED;
                case "settings_locked" -> Reason.SETTINGS_LOCKED;
                case "expired_or_used_challenge" -> Reason.CHALLENGE_EXPIRED;
                case "identity_provider_unavailable", "identity_provider_invalid_response" -> Reason.LOGIN_SERVICE_UNAVAILABLE;
                case "invalid_name" -> Reason.INVALID_ACCOUNT_NAME;
                case "invalid_payload", "invalid_json", "invalid_proof" -> Reason.INVALID_DATA;
                case "rate_limited" -> Reason.RATE_LIMITED;
                case "internal_error" -> Reason.SERVER_ERROR;
                default -> status >= 300 && status < 400 ? Reason.REDIRECTED
                    : status == 401 ? Reason.AUTH_REQUIRED : status == 403 ? Reason.ACCESS_DENIED
                    : status == 404 ? Reason.INVALID_SERVER_ADDRESS : status == 429 ? Reason.RATE_LIMITED
                    : status >= 500 ? (stage == Stage.MINECRAFT_AUTH ? Reason.LOGIN_SERVICE_UNAVAILABLE : Reason.SERVER_ERROR)
                    : Reason.HTTP_ERROR;
            };
        }
        return new SyncFailure(reason, stage, status);
    }

    public static SyncFailure fromException(Throwable error, Stage stage) {
        var seen = Collections.newSetFromMap(new IdentityHashMap<Throwable, Boolean>());
        boolean connectionFailure = false;
        for (Throwable cause = error; cause != null && seen.add(cause); cause = cause.getCause()) {
            if (cause instanceof SyncRequestException request) return request.failure();
            if (cause instanceof UnknownHostException) return of(Reason.ADDRESS_NOT_FOUND, stage);
            if (cause instanceof SocketTimeoutException) return of(Reason.TIMEOUT, stage);
            if (cause instanceof SSLException) return of(Reason.TLS_FAILED, stage);
            if (cause instanceof ConnectException) return of(Reason.CONNECTION_FAILED, stage);
            if (cause instanceof com.google.gson.JsonParseException) return of(Reason.INVALID_RESPONSE, stage);
            if (cause instanceof IOException) connectionFailure = true;
        }
        return of(connectionFailure ? Reason.CONNECTION_FAILED : Reason.UNKNOWN, stage);
    }
}
