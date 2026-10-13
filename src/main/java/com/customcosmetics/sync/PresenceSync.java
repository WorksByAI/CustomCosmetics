/*
 * Contains code adapted from Baity: https://github.com/raueyhs/Baity
 * Copyright (C) 2025 raueyhs
 * Modifications Copyright (C) 2026 CustomCosmetics contributors
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Modified for CustomCosmetics; notices updated 2026-10-11. See LICENSE and NOTICE.
 */
package com.customcosmetics.sync;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.customcosmetics.config.CosmeticsConfigDir;
import com.customcosmetics.config.ConfigManager;
import com.customcosmetics.config.RemoteServerConfig;
import com.customcosmetics.gui.module.Module;
import com.customcosmetics.gui.module.ModuleManager;
import com.customcosmetics.utils.MessageUtils;
import com.customcosmetics.utils.NickRenderUtils;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.net.SocketTimeoutException;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.List;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

@Environment(EnvType.CLIENT)
public final class PresenceSync {
    private static final Logger LOGGER = LoggerFactory.getLogger("CustomCosmetics/PresenceSync");
    private static final SyncCadence CADENCE = new SyncCadence();
    private static final StartupSyncNotice STARTUP_NOTICE = new StartupSyncNotice();
    private static final long REPORT_CHANGE_DEBOUNCE_MS = 3_000L;
    private static final int CONNECT_TIMEOUT_MS = 10_000;
    private static final int READ_TIMEOUT_MS = 10_000;
    private static final int FETCH_READ_TIMEOUT_MS = 10_000;
    private static final int NETWORK_RETRY_COUNT = 0;
    private static final long NETWORK_RETRY_BACKOFF_MS = 350L;
    private static final long NETWORK_WARN_THROTTLE_MS = 60_000L;
    private static final long SOFT_STALE_AFTER_MS = 3L * 24L * 60L * 60L * 1000L;
    private static final long HARD_EXPIRE_AFTER_MS = 3L * 24L * 60L * 60L * 1000L;
    private static final Gson CACHE_GSON = new GsonBuilder().setPrettyPrinting().create();

    private static final AtomicBoolean FETCHING = new AtomicBoolean(false);
    private static final AtomicLong ENDPOINT_VERSION = new AtomicLong();
    private static final AtomicBoolean REPORTING = new AtomicBoolean(false);

    private static volatile long nextReportAllowedAt = 0L;
    private static volatile String lastReportedSignature = "";
    private static volatile UUID lastSeenLocalPlayerUuid = null;
    private static volatile PresenceAuthClient.Session authSession;
    private static final PresenceAuthClient AUTH = new PresenceAuthClient(url -> openHttpConnection(url, 0));
    private static final AtomicLong LAST_FETCH_EXCEPTION_WARN_AT = new AtomicLong(0L);
    private static final AtomicLong LAST_REPORT_EXCEPTION_WARN_AT = new AtomicLong(0L);
    private static final AtomicLong LAST_REGISTER_EXCEPTION_WARN_AT = new AtomicLong(0L);

    private static final ManualSyncResult MANUAL_RESULT = new ManualSyncResult();
    private static Object activeWorldConnection;
    private static boolean wasAutoSyncActive;

    public record RemoteNickname(UUID uuid, String name, ChromaProfile profile) {}
    private record RemoteSnapshot(Map<UUID, RemoteUserState> users, Map<String, ChromaProfile> profiles,
                                  List<RemoteNickname> nicknames, boolean hasSmol) {}
    private static volatile RemoteSnapshot remoteSnapshot = new RemoteSnapshot(Map.of(), Map.of(), List.of(), false);

    private PresenceSync() {
    }

    public static void init() {
        System.setProperty("java.net.preferIPv4Stack", "true");
        CADENCE.reset();
        nextReportAllowedAt = 0L;
        lastReportedSignature = "";
        lastSeenLocalPlayerUuid = null;
        STARTUP_NOTICE.leaveWorld();
        activeWorldConnection = null;
        wasAutoSyncActive = false;
        loadCacheFromDisk();
        if (ConfigManager.syncEnabled) {
            CompletableFuture.runAsync(PresenceSync::runPresenceConnectivityProbe);
        }
    }

    public static void runPresenceConnectivityProbe() {
        String fetchUrl = resolveFetchUrl();
        if (fetchUrl == null || fetchUrl.isBlank()) {
            return;
        }
        PresenceProxyResolver.establishSession(toHealthUrl(fetchUrl.trim()));
    }

    public static void tick() {
        if (RemoteServerConfig.reload(false)) onServerAddressChanged();
        handleImmediateSyncTriggers();
    }

    static HttpURLConnection openActionConnection(String url) throws Exception {
        return openHttpConnection(url, 0);
    }

    static PresenceAuthClient.Session actionSession() {
        var client = Minecraft.getInstance();
        var account = client.getUser();
        var session = authSession;
        return client.player != null && session != null && session.uuid().equals(client.player.getUUID())
            && session.validFor(serviceBaseUrl(), account.getProfileId(), account.getName()) ? session : null;
    }

    public static int manualSyncCooldownSeconds() {
        return CADENCE.manualCooldownSeconds(System.nanoTime());
    }

    public static boolean syncOnce() {
        if (RemoteServerConfig.reload(true)) onServerAddressChanged();
        if (!CADENCE.tryManualSync(System.nanoTime())) return false;
        long now = System.currentTimeMillis();
        CADENCE.noteRead(now);
        nextReportAllowedAt = 0L;
        MANUAL_RESULT.begin(ENDPOINT_VERSION.get());
        startReadThenWrite(now, true, true);
        return true;
    }

    public static void onClickGuiClosed() {
        attemptReport(System.currentTimeMillis(), false, false);
    }

    public static void onServerAddressChanged() {
        ENDPOINT_VERSION.incrementAndGet();
        MANUAL_RESULT.cancel();
        PresenceProxyResolver.resetSession();
        authSession = null;
        lastReportedSignature = "";
        nextReportAllowedAt = 0;
        CADENCE.reset();
        remoteSnapshot = new RemoteSnapshot(Map.of(), Map.of(), List.of(), false);
        NickRenderUtils.invalidateLocalTargetsCache();
        STARTUP_NOTICE.addressChanged();
    }

    private static void attemptReport(long now, boolean forceUpload, boolean forceRemoteSync) {
        if (!forceRemoteSync && !ConfigManager.syncEnabled) return;
        String base = serviceBaseUrl();
        if (base.isBlank()) {
            if (forceRemoteSync) completeManualResult(ENDPOINT_VERSION.get(), StartupSyncNotice.Operation.WRITE, false,
                SyncFailure.of(SyncFailure.Reason.NO_SERVER_ADDRESS, SyncFailure.Stage.UPLOAD));
            return;
        }
        if (!REPORTING.compareAndSet(false, true)) return;
        LocalUserState state = snapshotLocalState();
        if (state == null) {
            REPORTING.set(false);
            if (forceRemoteSync) completeManualResult(ENDPOINT_VERSION.get(), StartupSyncNotice.Operation.WRITE, false,
                SyncFailure.of(SyncFailure.Reason.NO_ACTIVE_WORLD, SyncFailure.Stage.UPLOAD));
            return;
        }
        String signature = state.signature();
        if (!forceUpload && (signature.equals(lastReportedSignature) || now < nextReportAllowedAt)) {
            REPORTING.set(false); return;
        }
        nextReportAllowedAt = now + REPORT_CHANGE_DEBOUNCE_MS;
        long endpointVersion = ENDPOINT_VERSION.get();
        // Capture account credentials once. They are used only by the fixed Mojang HTTPS join endpoint.
        var account = Minecraft.getInstance().getUser();
        StartupSyncNotice.Ticket startupTicket = forceRemoteSync ? null
            : STARTUP_NOTICE.beginAutomatic(StartupSyncNotice.Operation.WRITE);
        CompletableFuture.runAsync(() -> {
            boolean success = false;
            boolean cancelled = false;
            SyncFailure failure = SyncFailure.of(SyncFailure.Reason.UNKNOWN, SyncFailure.Stage.UPLOAD);
            try {
                PresenceAuthClient.Session session = authSession;
                if (session == null || !session.validFor(base, account.getProfileId(), account.getName())) {
                    session = AUTH.authenticate(base, account.getProfileId(), account.getName(),
                        hash -> PresenceAuthClient.joinMojang(account.getProfileId(), account.getAccessToken(), hash));
                }
                if (endpointVersion != ENDPOINT_VERSION.get() || !serviceBaseUrl().equals(base) || !Minecraft.getInstance().getUser().getProfileId().equals(account.getProfileId())
                    || (!forceRemoteSync && !ConfigManager.syncEnabled)) { cancelled = true; return; }
                authSession = session;
                success = reportLocalState(base + "/report", state, forceRemoteSync, session);
                if (success) {
                    Minecraft.getInstance().submit(() -> {
                        if (endpointVersion != ENDPOINT_VERSION.get()) return;
                        lastReportedSignature = signature;
                        updateCacheForSelfFromLocalState(state);
                    }).join();
                }
            } catch (Exception e) {
                failure = SyncFailure.fromException(e, SyncFailure.Stage.SERVER_AUTH);
                logThrottledWarn(LAST_REGISTER_EXCEPTION_WARN_AT, "[PresenceSync] authentication/upload failed: {}", failure.description());
            } finally {
                if (!cancelled && endpointVersion == ENDPOINT_VERSION.get()) {
                    if (!success) nextReportAllowedAt = System.currentTimeMillis() + 10_000;
                    STARTUP_NOTICE.complete(startupTicket, success, failure, System.currentTimeMillis());
                    completeManualResult(endpointVersion, StartupSyncNotice.Operation.WRITE, success, failure);
                }
                REPORTING.set(false);
            }
        });
    }

    private static void updateCacheForSelfFromLocalState(LocalUserState local) {
        try {
            String nowIso = java.time.Instant.now().toString();
            Map<UUID, RemoteUserState> snapshot = new HashMap<>(remoteSnapshot.users());
            snapshot.put(local.uuid(), new RemoteUserState(
                    local.uuid(),
                    local.name(),
                    local.smolEnabled(),
                    local.smolLimbSwingSpeed(),
                    local.nickTweaksEnabled(),
                    local.chromaEnabled(),
                    local.chromaPalette(),
                    local.chromaSpeed(),
                    local.chromaSize(),
                    local.chromaAmount(),
                    local.chromaLightness(),
                    local.gradientStart(),
                    local.gradientEnd(),
                    local.boldSelf(),
                    local.customNickColorEnabled(),
                    local.nickChanger(),
                    nowIsoToEpochMs(nowIso),
                    false,
                    local.gradientStyle(),
                    local.shimmerSpeed(), local.shimmerColor(), local.shimmerHighlightColor()
            ));

            JsonObject root = new JsonObject();
            JsonObject usersObj = new JsonObject();

            for (RemoteUserState rs : snapshot.values()) {
                usersObj.add(rs.uuid().toString(), buildUserJsonFromRemote(rs, nowIso));
            }

            root.addProperty("authVersion", 2);
            root.add("users", usersObj);
            saveCacheToDisk(root.toString());
        } catch (Exception ignored) {
        }
    }

    private static long nowIsoToEpochMs(String iso) {
        try {
            return java.time.Instant.parse(iso).toEpochMilli();
        } catch (Exception ignored) {
            return -1L;
        }
    }

    private static JsonObject buildUserJsonFromRemote(RemoteUserState state, String nowIso) {
        JsonObject userObj = new JsonObject();
        userObj.addProperty("name", state.name());

        JsonObject features = new JsonObject();

        JsonObject nickTweaks = new JsonObject();
        nickTweaks.addProperty("enabled", state.nickTweaksEnabled());
        nickTweaks.addProperty("boldEnabled", state.boldEnabled());
        nickTweaks.addProperty("nickChanger", state.nickChanger());

        if (state.nickTweaksEnabled()) {
            nickTweaks.addProperty("chromaEnabled", state.chromaEnabled());
            nickTweaks.addProperty("customNickColorEnabled", state.customNickColorEnabled());

            if (state.chromaEnabled()) {
                JsonObject chroma = new JsonObject();
                chroma.addProperty("enabled", true);
                chroma.addProperty("speed", state.chromaSpeed());
                chroma.addProperty("size", state.chromaSize());
                chroma.addProperty("chroma", state.chromaAmount());
                chroma.addProperty("lightness", state.chromaLightness());
                JsonArray palette = new JsonArray();
                for (int color : state.chromaPalette()) {
                    palette.add(String.format("#%06X", color & 0xFFFFFF));
                }
                chroma.add("palette", palette);
                nickTweaks.add("chroma", chroma);
            } else if (state.customNickColorEnabled()) {
                JsonObject solid = new JsonObject();
                solid.addProperty("customColorStart", String.format("#%06X", state.gradientStart() & 0xFFFFFF));
                solid.addProperty("customColorEnd", String.format("#%06X", state.gradientEnd() & 0xFFFFFF));
                solid.addProperty("style", state.gradientStyle());
                solid.addProperty("shimmerSpeed", state.shimmerSpeed());
                solid.addProperty("shimmerColor", String.format("#%06X", state.shimmerColor() & 0xFFFFFF));
                solid.addProperty("shimmerHighlightColor", String.format("#%06X", state.shimmerHighlightColor() & 0xFFFFFF));
                nickTweaks.add("solid", solid);
            }
        }

        features.add("nickTweaks", nickTweaks);

        JsonObject smolPeople = new JsonObject();
        smolPeople.addProperty("enabled", state.smolPeopleEnabled());
        smolPeople.addProperty("limbSwingSpeed", state.smolLimbSwingSpeed());
        features.add("smolPeople", smolPeople);

        userObj.add("features", features);

        JsonObject meta = new JsonObject();
        meta.addProperty("protocol", 1);
        meta.addProperty("reportedAt", nowIso);
        meta.addProperty("lastSeenAt", nowIso);
        userObj.add("meta", meta);

        return userObj;
    }

    private static void handleImmediateSyncTriggers() {
        Minecraft client = Minecraft.getInstance();
        LocalPlayer player = client.player;
        boolean inWorld = client.level != null && player != null;
        Object connection = client.getConnection();
        if (inWorld && connection != activeWorldConnection) {
            activeWorldConnection = connection;
            STARTUP_NOTICE.enterWorld();
            ENDPOINT_VERSION.incrementAndGet();
            MANUAL_RESULT.cancel();
            CADENCE.reset();
            nextReportAllowedAt = 0;
        } else if (connection == null && activeWorldConnection != null) {
            activeWorldConnection = null;
            STARTUP_NOTICE.leaveWorld();
            ENDPOINT_VERSION.incrementAndGet();
            MANUAL_RESULT.cancel();
        }

        if (inWorld) {
            UUID currentUuid = player.getUUID();
            boolean switchedAccount = lastSeenLocalPlayerUuid != null && !lastSeenLocalPlayerUuid.equals(currentUuid);
            if (switchedAccount || lastSeenLocalPlayerUuid == null) {
                nextReportAllowedAt = 0L;
                if (switchedAccount) {
                    lastReportedSignature = "";
                    authSession = null;
                    CADENCE.reset();
                }
            }
            lastSeenLocalPlayerUuid = currentUuid;
        }

        boolean active = inWorld && ConfigManager.syncEnabled && !serviceBaseUrl().isBlank();
        if (!active && wasAutoSyncActive) lastReportedSignature = "";
        wasAutoSyncActive = active;
        SyncCadence.Due due = CADENCE.tick(System.currentTimeMillis(), active,
            ConfigManager.getSyncRefreshIntervalSeconds() * 1000L);
        if (due.read()) requestRemoteRefresh(false);
        if (due.checkWrite() || due.heartbeat())
            attemptReport(System.currentTimeMillis(), due.heartbeat(), false);
        if (inWorld) {
            long version = ENDPOINT_VERSION.get();
            if (MANUAL_RESULT.needs(version, StartupSyncNotice.Operation.READ)) requestRemoteRefresh(true);
            if (MANUAL_RESULT.needs(version, StartupSyncNotice.Operation.WRITE)) attemptReport(System.currentTimeMillis(), true, true);
        }
        StartupSyncNotice.Result result = inWorld ? STARTUP_NOTICE.poll(System.currentTimeMillis()) : null;
        if (result != null) {
            if (!result.success()) {
                MessageUtils.sendSyncResult(false, ConfigManager.syncNotifications, result.failure());
            } else if (ConfigManager.syncNotifications) {
                MessageUtils.sendSyncResult(true, true);
            }
        }
    }

    private static void completeManualResult(long endpointVersion, StartupSyncNotice.Operation operation, boolean success, SyncFailure failure) {
        ManualSyncResult.Result result = MANUAL_RESULT.complete(endpointVersion, operation, success, success ? null : failure);
        if (result == null) return;
        Minecraft.getInstance().execute(() -> {
            if (endpointVersion == ENDPOINT_VERSION.get())
                MessageUtils.sendSyncResult(result.success(), false, result.failure());
        });
    }

    private static void startReadThenWrite(long now, boolean forceUpload, boolean forceRemoteSync) {
        requestRemoteRefresh(forceRemoteSync);
        attemptReport(now, forceUpload, forceRemoteSync);
    }

    private static void requestRemoteRefresh(boolean forceRemoteSync) {
        if (!forceRemoteSync && !ConfigManager.syncEnabled) return;
        String url = resolveFetchUrl();
        if (url.isBlank()) {
            if (forceRemoteSync) completeManualResult(ENDPOINT_VERSION.get(), StartupSyncNotice.Operation.READ, false,
                SyncFailure.of(SyncFailure.Reason.NO_SERVER_ADDRESS, SyncFailure.Stage.DOWNLOAD));
            return;
        }
        if (!FETCHING.compareAndSet(false, true)) return;
        long endpointVersion = ENDPOINT_VERSION.get();
        StartupSyncNotice.Ticket startupTicket = forceRemoteSync ? null
            : STARTUP_NOTICE.beginAutomatic(StartupSyncNotice.Operation.READ);
        CompletableFuture.runAsync(() -> {
            boolean success = false;
            SyncFailure failure = SyncFailure.of(SyncFailure.Reason.UNKNOWN, SyncFailure.Stage.DOWNLOAD);
            try { success = fetchAndReplace(url, forceRemoteSync, endpointVersion); }
            catch (Exception e) { failure = SyncFailure.fromException(e, SyncFailure.Stage.DOWNLOAD); }
            finally {
                STARTUP_NOTICE.complete(startupTicket, success, failure, System.currentTimeMillis());
                completeManualResult(endpointVersion, StartupSyncNotice.Operation.READ, success, failure);
                FETCHING.set(false);
                if (endpointVersion != ENDPOINT_VERSION.get()) Minecraft.getInstance().execute(CADENCE::reset);
            }
        });
    }

    static String serviceBaseUrl() {
        return SyncEndpoint.baseUrl();
    }

    private static String resolveFetchUrl() {
        String base = serviceBaseUrl();
        return base.isBlank() ? "" : base + "/users.json";
    }

    private static String resolveReportUrl() {
        String base = serviceBaseUrl();
        return base.isBlank() ? "" : base + "/report";
    }

    public static boolean isSmolEnabledFor(UUID uuid) {
        if (uuid == null) return false;
        RemoteUserState state = remoteSnapshot.users().get(uuid);
        return state != null && state.smolPeopleEnabled();
    }

    public static Boolean getRemoteSmolPreference(UUID uuid) {
        if (uuid == null) return null;
        RemoteUserState state = remoteSnapshot.users().get(uuid);
        if (state == null) return null;
        return state.smolPeopleEnabled();
    }

    public static Double getRemoteSmolLimbSwingSpeed(UUID uuid) {
        RemoteUserState state = uuid == null ? null : remoteSnapshot.users().get(uuid);
        return state == null ? null : state.smolLimbSwingSpeed();
    }

    private static double normalizeSmolSpeed(double speed) {
        return Double.isFinite(speed) ? clamp(speed, 0.5, 5.0) : 2.5;
    }

    public static boolean hasRemoteSmolUser() {
        return remoteSnapshot.hasSmol();
    }

    public static ChromaProfile getChromaProfileByName(String name) {
        if (name == null || name.isBlank()) return null;
        return remoteSnapshot.profiles().get(name.toLowerCase(Locale.ROOT));
    }

    public static List<RemoteNickname> remoteNicknames() {
        return remoteSnapshot.nicknames();
    }

    private static boolean fetchAndReplace(String url, boolean forceRemoteSync, long endpointVersion) throws SyncRequestException {
        for (int attempt = 0; attempt <= NETWORK_RETRY_COUNT; attempt++) {
            HttpURLConnection connection = null;
            try {
                connection = openHttpConnection(url, attempt);
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
                connection.setReadTimeout(FETCH_READ_TIMEOUT_MS);
                connection.setUseCaches(false);
                connection.setRequestProperty("Accept", "application/json");
                LocalPlayer player = Minecraft.getInstance().player;
                if (player != null) {
                    connection.setRequestProperty("x-customcosmetics-uuid", player.getUUID().toString());
                }

                int code = connection.getResponseCode();
                if (code >= 200 && code < 300) {
                    try (InputStream stream = connection.getInputStream()) {
                        String json = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
                        try {
                            var payload = JsonParser.parseString(json);
                            if (!payload.isJsonObject() || !payload.getAsJsonObject().has("authVersion")
                                    || payload.getAsJsonObject().get("authVersion").getAsInt() != 2
                                    || !payload.getAsJsonObject().has("users") || !payload.getAsJsonObject().get("users").isJsonObject())
                                throw new SyncRequestException(SyncFailure.of(SyncFailure.Reason.INVALID_RESPONSE, SyncFailure.Stage.DOWNLOAD));
                        } catch (RuntimeException e) {
                            throw new SyncRequestException(SyncFailure.of(SyncFailure.Reason.INVALID_RESPONSE, SyncFailure.Stage.DOWNLOAD));
                        }
                        Minecraft.getInstance().submit(() -> {
                            if (endpointVersion != ENDPOINT_VERSION.get() || !url.equals(resolveFetchUrl())) return;
                            applyPayload(json, false, forceRemoteSync);
                            saveCacheToDisk(json);
                        }).join();
                        LOGGER.info("[PresenceSync] fetch ok, code={}, bytes={}, attempt={}", code, json.length(), attempt + 1);
                        return true;
                    }
                }
                LOGGER.warn("[PresenceSync] fetch failed, code={}, url={}, attempt={}", code, url, attempt + 1);
                if (attempt < NETWORK_RETRY_COUNT && (code == 429 || code >= 500)) {
                    sleepRetryBackoff();
                    continue;
                }
                throw new SyncRequestException(SyncFailure.fromResponse(connection, SyncFailure.Stage.DOWNLOAD, code));
            } catch (Exception e) {
                logThrottledWarn(LAST_FETCH_EXCEPTION_WARN_AT, "[PresenceSync] fetch exception, attempt={}, err={}", attempt + 1,
                    SyncFailure.fromException(e, SyncFailure.Stage.DOWNLOAD).description());
                if (attempt < NETWORK_RETRY_COUNT && shouldRetryException(e)) {
                    sleepRetryBackoff();
                    continue;
                }
                throw new SyncRequestException(SyncFailure.fromException(e, SyncFailure.Stage.DOWNLOAD));
            } finally {
                if (connection != null) connection.disconnect();
            }
        }
        return false;
    }

    private static LocalUserState snapshotLocalState() {
        Minecraft client = Minecraft.getInstance();
        LocalPlayer player = client.player;
        if (player == null) return null;

        String playerName = client.getUser().getName();
        if (playerName == null || playerName.isBlank()) return null;

        Module chromaModule = ModuleManager.getModuleByName("NickTweaks");
        boolean nickTweaksEnabled = chromaModule != null && chromaModule.isEnabled();
        boolean chromaEnabled = ConfigManager.nickTweaksChromaEnabled;
        boolean smolEnabled = ConfigManager.smolpeopleMode;
        double chromaSize = Math.max(0.1, Math.min(12.0, ConfigManager.nickTweaksChromaSize));
        double chromaAmount = Math.max(0.0, Math.min(0.4, ConfigManager.nickTweaksChromaChroma));
        double chromaLightness = Math.max(0.2, Math.min(1.0, ConfigManager.nickTweaksChromaLightness));
        int[] palette = generatePalette(chromaAmount, chromaLightness);
        double speed = chromaEnabled ? Math.max(0.0, Math.min(8.0, ConfigManager.nickTweaksChromaSpeed)) : 0.0;

        int gradientStart = ConfigManager.nickTweaksGradientStartColor & 0xFFFFFF;
        int gradientEnd = ConfigManager.nickTweaksGradientEndColor & 0xFFFFFF;
        boolean boldSelf = ConfigManager.nickTweaksBoldSelf;
        boolean customNickColorEnabled = ConfigManager.nickTweaksCustomNickColorEnabled;
        String nickChanger = com.customcosmetics.config.NicknameLimit.limit(ConfigManager.nickTweaksNickChanger);
        LocalUserState state = new LocalUserState(
            client.getUser().getProfileId(),
            playerName,
            nickTweaksEnabled,
            chromaEnabled,
            smolEnabled,
            normalizeSmolSpeed(ConfigManager.smolLimbSwingSpeed),
            palette,
            speed,
            chromaSize,
            chromaAmount,
            chromaLightness,
            gradientStart,
            gradientEnd,
            boldSelf,
            customNickColorEnabled,
            nickChanger,
            com.customcosmetics.features.nickname.NicknameGradient.normalizeStyle(ConfigManager.nickTweaksGradientStyle),
            Double.isFinite(ConfigManager.nickTweaksShimmerSpeed) ? clamp(ConfigManager.nickTweaksShimmerSpeed, 0, 4) : 1,
            ConfigManager.nickTweaksShimmerColor & 0xFFFFFF, ConfigManager.nickTweaksShimmerHighlightColor & 0xFFFFFF
        );
        return state;
    }

    private static boolean reportLocalState(String url, LocalUserState state, boolean forceRemoteSync, PresenceAuthClient.Session session) throws SyncRequestException {
        JsonObject root = new JsonObject();
        root.addProperty("version", 1);

        JsonObject user = new JsonObject();
        user.addProperty("uuid", state.uuid().toString());
        user.addProperty("name", state.name());

        JsonObject features = new JsonObject();

        JsonObject nickTweaks = new JsonObject();
        nickTweaks.addProperty("enabled", state.nickTweaksEnabled());
        nickTweaks.addProperty("boldEnabled", state.boldSelf());
        nickTweaks.addProperty("nickChanger", state.nickChanger());
        if (state.nickTweaksEnabled()) {
            nickTweaks.addProperty("chromaEnabled", state.chromaEnabled());
            nickTweaks.addProperty("customNickColorEnabled", state.customNickColorEnabled());
            if (state.chromaEnabled()) {
                JsonObject chroma = new JsonObject();
                chroma.addProperty("enabled", true);
                chroma.addProperty("speed", state.chromaSpeed());
                chroma.addProperty("size", state.chromaSize());
                chroma.addProperty("chroma", state.chromaAmount());
                chroma.addProperty("lightness", state.chromaLightness());
                JsonArray palette = new JsonArray();
                for (int color : state.chromaPalette()) {
                    palette.add(String.format("#%06X", color & 0xFFFFFF));
                }
                chroma.add("palette", palette);
                nickTweaks.add("chroma", chroma);
            } else if (state.customNickColorEnabled()) {
                JsonObject solid = new JsonObject();
                solid.addProperty("customColorStart", String.format("#%06X", state.gradientStart() & 0xFFFFFF));
                solid.addProperty("customColorEnd", String.format("#%06X", state.gradientEnd() & 0xFFFFFF));
                solid.addProperty("style", state.gradientStyle());
                solid.addProperty("shimmerSpeed", state.shimmerSpeed());
                solid.addProperty("shimmerColor", String.format("#%06X", state.shimmerColor() & 0xFFFFFF));
                solid.addProperty("shimmerHighlightColor", String.format("#%06X", state.shimmerHighlightColor() & 0xFFFFFF));
                nickTweaks.add("solid", solid);
            }
        }
        features.add("nickTweaks", nickTweaks);

        JsonObject smol = new JsonObject();
        smol.addProperty("enabled", state.smolEnabled());
        smol.addProperty("limbSwingSpeed", state.smolLimbSwingSpeed());
        features.add("smolPeople", smol);
        user.add("features", features);

        JsonObject meta = new JsonObject();
        meta.addProperty("protocol", 1);
        String nowIso = java.time.Instant.now().toString();
        meta.addProperty("reportedAt", nowIso);
        meta.addProperty("lastSeenAt", nowIso);
        user.add("meta", meta);
        root.add("user", user);
        byte[] payload = root.toString().getBytes(StandardCharsets.UTF_8);

        for (int attempt = 0; attempt <= NETWORK_RETRY_COUNT; attempt++) {
            HttpURLConnection connection = null;
            try {
                connection = openHttpConnection(url, attempt);
                connection.setRequestMethod("POST");
                connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
                connection.setReadTimeout(READ_TIMEOUT_MS);
                connection.setUseCaches(false);
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                connection.setRequestProperty("Accept", "application/json");
                if (session == null || !url.equals(session.baseUrl() + "/report")
                    || !session.validFor(serviceBaseUrl(), state.uuid(), state.name()))
                    throw new SyncRequestException(SyncFailure.of(SyncFailure.Reason.INVALID_SYNC_TOKEN, SyncFailure.Stage.UPLOAD));
                connection.setRequestProperty("Authorization", "Bearer " + session.token());

                connection.getOutputStream().write(payload);
                connection.getOutputStream().flush();

                int code = connection.getResponseCode();
                if (code >= 200 && code < 300) {
                    try (InputStream responseBody = connection.getInputStream()) {
                        responseBody.transferTo(java.io.OutputStream.nullOutputStream());
                    }
                    LOGGER.info("[PresenceSync] report ok, code={}, uuid={}, attempt={}", code, state.uuid(), attempt + 1);
                    return true;
                }
                LOGGER.warn("[PresenceSync] report failed, code={}, uuid={}, attempt={}", code, state.uuid(), attempt + 1);
                if (code == 401 || code == 403) {
                    if (authSession == session) authSession = null;
                    ConfigManager.requestSave();
                    throw new SyncRequestException(SyncFailure.fromResponse(connection, SyncFailure.Stage.UPLOAD, code));
                }
                if (attempt < NETWORK_RETRY_COUNT && (code == 429 || code >= 500)) {
                    sleepRetryBackoff();
                    continue;
                }
                throw new SyncRequestException(SyncFailure.fromResponse(connection, SyncFailure.Stage.UPLOAD, code));
            } catch (Exception e) {
                logThrottledWarn(LAST_REPORT_EXCEPTION_WARN_AT, "[PresenceSync] report exception, uuid={}, attempt={}, err={}", state.uuid(), attempt + 1,
                    SyncFailure.fromException(e, SyncFailure.Stage.UPLOAD).description());
                if (attempt < NETWORK_RETRY_COUNT && shouldRetryException(e)) {
                    sleepRetryBackoff();
                    continue;
                }
                throw new SyncRequestException(SyncFailure.fromException(e, SyncFailure.Stage.UPLOAD));
            } finally {
                if (connection != null) connection.disconnect();
            }
        }
        return false;
    }

    private static boolean shouldRetryException(Exception e) {
        Throwable t = e;
        while (t != null) {
            if (t instanceof SocketTimeoutException) return true;
            t = t.getCause();
        }
        return false;
    }

    private static HttpURLConnection openHttpConnection(String url, int attempt) throws Exception {
        return PresenceAuthClient.openServiceConnection(url,
            requestUrl -> PresenceProxyResolver.openConnection(requestUrl, attempt));
    }

    private static void sleepRetryBackoff() {
        try {
            Thread.sleep(NETWORK_RETRY_BACKOFF_MS);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    private static void logThrottledWarn(AtomicLong gate, String pattern, Object... args) {
        long now = System.currentTimeMillis();
        long last = gate.get();
        if (now - last < NETWORK_WARN_THROTTLE_MS) return;
        if (gate.compareAndSet(last, now)) {
            LOGGER.warn(pattern, args);
        }
    }

    static String toHealthUrl(String anyUrl) {
        if (anyUrl == null || anyUrl.isBlank()) return "";
        String u = anyUrl.trim();
        if (u.endsWith("/users.json")) return u.substring(0, u.length() - "/users.json".length()) + "/health";
        if (u.endsWith("/report")) return u.substring(0, u.length() - "/report".length()) + "/health";
        if (u.endsWith("/")) return u + "health";
        return u + "/health";
    }

    private static int[] generatePalette(double chroma, double lightness) {
        int count = 6;
        int[] colors = new int[count];
        float saturation = (float) (chroma / 0.4);
        for (int i = 0; i < count; i++) {
            float hue = (float) i / (float) count;
            colors[i] = Mth.hsvToRgb(hue, saturation, (float) lightness);
        }
        return colors;
    }

    private static void applyPayload(String json, boolean overrideOwnState, boolean forceRemoteSync) {
        JsonElement rootElement = JsonParser.parseString(json);
        if (!rootElement.isJsonObject()) return;

        JsonObject root = rootElement.getAsJsonObject();
        if (!root.has("authVersion") || root.get("authVersion").getAsInt() != 2) throw new IllegalArgumentException("Unverified sync protocol");
        JsonObject users = root.getAsJsonObject("users");
        if (users == null) return;
        long now = System.currentTimeMillis();

        Map<UUID, RemoteUserState> newUsers = new ConcurrentHashMap<>();
        Map<String, ChromaProfile> newChromaByName = new ConcurrentHashMap<>();
        List<RemoteNickname> newNicknames = new ArrayList<>();

        for (Map.Entry<String, JsonElement> entry : users.entrySet()) {
            UUID uuid;
            try {
                uuid = UUID.fromString(entry.getKey());
            } catch (Exception ignored) {
                continue;
            }

            if (!entry.getValue().isJsonObject()) continue;
            JsonObject userObj = entry.getValue().getAsJsonObject();
            String name = getAsString(userObj, "name", "");
            if (name.isBlank()) continue;


            JsonObject features = userObj.getAsJsonObject("features");

            JsonObject smolObj = features == null ? null : features.getAsJsonObject("smolPeople");
            boolean smolEnabled = getAsBoolean(smolObj, "enabled", false);

            JsonObject nickTweaksObj = features == null ? null : features.getAsJsonObject("nickTweaks");
            boolean nickTweaksEnabled = getAsBoolean(nickTweaksObj, "enabled", false);

            boolean chromaEnabled = false;
            boolean boldSelf = false;
            boolean customNickColorEnabled = false;
            String nickChanger = "";
            double speed = 0.0;
            double chromaSize = 3.1;
            double chromaAmount = 0.2;
            double chromaLightness = 0.8;
            int gradientStart = 0xFF4D4D;
            int gradientEnd = 0xC299FF;
            String gradientStyle = "character";
            double shimmerSpeed = 1;
            int shimmerColor = 0xFFCF64, shimmerHighlightColor = 0xFFF0BB;
            int[] palette = new int[0];

            if (nickTweaksEnabled) {
                boldSelf = getAsBoolean(nickTweaksObj, "boldEnabled", false);
                nickChanger = getAsString(nickTweaksObj, "nickChanger", "");
                chromaEnabled = getAsBoolean(nickTweaksObj, "chromaEnabled", false);
                customNickColorEnabled = getAsBoolean(nickTweaksObj, "customNickColorEnabled", false);

                if (chromaEnabled) {
                    JsonObject chromaObj = nickTweaksObj == null ? null : nickTweaksObj.getAsJsonObject("chroma");
                    speed = clamp(getAsDouble(chromaObj, "speed", 1.0), 0.0, 8.0);
                    chromaSize = clamp(getAsDouble(chromaObj, "size", 3.1), 0.1, 12.0);
                    chromaAmount = clamp(getAsDouble(chromaObj, "chroma", 0.2), 0.0, 0.4);
                    chromaLightness = clamp(getAsDouble(chromaObj, "lightness", 0.8), 0.2, 1.0);
                    palette = parsePalette(chromaObj == null ? null : chromaObj.getAsJsonArray("palette"));
                    if (palette.length == 0) {
                        palette = generatePalette(chromaAmount, chromaLightness);
                    }
                } else if (customNickColorEnabled) {
                    JsonObject solidObj = nickTweaksObj == null ? null : nickTweaksObj.getAsJsonObject("solid");
                    gradientStart = parseHexColor(solidObj, "customColorStart", 0xFF4D4D);
                    gradientEnd = parseHexColor(solidObj, "customColorEnd", 0xC299FF);
                    gradientStyle = com.customcosmetics.features.nickname.NicknameGradient.normalizeStyle(getAsString(solidObj, "style", "character"));
                    double rawShimmerSpeed = getAsDouble(solidObj, "shimmerSpeed", 1);
                    shimmerSpeed = Double.isFinite(rawShimmerSpeed) ? clamp(rawShimmerSpeed, 0, 4) : 1;
                    shimmerColor = parseHexColor(solidObj, "shimmerColor", 0xFFCF64);
                    shimmerHighlightColor = parseHexColor(solidObj, "shimmerHighlightColor", 0xFFF0BB);
                    palette = new int[]{gradientStart, gradientEnd};
                }
            }

            JsonObject metaObj = userObj.getAsJsonObject("meta");
            long lastSeenEpochMs = parseIsoEpochMs(getAsString(metaObj, "lastSeenAt", ""));
            if (lastSeenEpochMs <= 0L) {
                lastSeenEpochMs = parseIsoEpochMs(getAsString(metaObj, "reportedAt", ""));
            }
            boolean stale = lastSeenEpochMs > 0L && (now - lastSeenEpochMs) > SOFT_STALE_AFTER_MS;
            if (lastSeenEpochMs > 0L && (now - lastSeenEpochMs) > HARD_EXPIRE_AFTER_MS) {
                continue;
            }

            RemoteUserState state = new RemoteUserState(
                uuid,
                name,
                smolEnabled,
                normalizeSmolSpeed(getAsDouble(smolObj, "limbSwingSpeed", 2.5)),
                nickTweaksEnabled,
                chromaEnabled,
                palette,
                speed,
                chromaSize,
                chromaAmount,
                chromaLightness,
                gradientStart,
                gradientEnd,
                boldSelf,
                customNickColorEnabled,
                nickChanger,
                lastSeenEpochMs,
                stale,
                gradientStyle,
                shimmerSpeed, shimmerColor, shimmerHighlightColor
            );
            newUsers.put(uuid, state);
            if (nickTweaksEnabled) {
                String nameLower = name.toLowerCase(Locale.ROOT);
                newChromaByName.put(
                    nameLower,
                    new ChromaProfile(chromaEnabled, palette, speed, chromaSize, chromaAmount, chromaLightness, gradientStart, gradientEnd, boldSelf, customNickColorEnabled, nickChanger, gradientStyle, shimmerSpeed, shimmerColor, shimmerHighlightColor)
                );
                newNicknames.add(new RemoteNickname(uuid, name, newChromaByName.get(nameLower)));
            }
        }

        remoteSnapshot = new RemoteSnapshot(Map.copyOf(newUsers), Map.copyOf(newChromaByName),
            List.copyOf(newNicknames), newUsers.values().stream().anyMatch(RemoteUserState::smolPeopleEnabled));
        NickRenderUtils.invalidateLocalTargetsCache();

        if (overrideOwnState) {
            tryPushLocalIfRemoteDiffers(forceRemoteSync);
        }
    }

    private static void applyPayload(String json, boolean overrideOwnState) {
        applyPayload(json, overrideOwnState, false);
    }

    private static void tryPushLocalIfRemoteDiffers(boolean forceRemoteSync) {
        Minecraft client = Minecraft.getInstance();
        if (client == null) return;
        LocalPlayer player = client.player;
        if (player == null) return;
        RemoteUserState remote = remoteSnapshot.users().get(player.getUUID());
        LocalUserState local = snapshotLocalState();
        if (local == null) return;
        if (remote == null) {
            attemptReport(System.currentTimeMillis(), false, forceRemoteSync);
            return;
        }
        String remoteSig = buildRemoteLikeSignature(remote);
        if (!remoteSig.equals(local.signature())) {
            attemptReport(System.currentTimeMillis(), false, forceRemoteSync);
        }
    }

    private static String buildRemoteLikeSignature(RemoteUserState r) {
        return (r.uuid() + "|" + r.name()
            + "|" + r.nickTweaksEnabled() + "|" + r.chromaEnabled() + "|" + r.smolPeopleEnabled()
            + "|" + r.smolLimbSwingSpeed() + "|" + r.chromaSpeed() + "|" + r.chromaSize() + "|" + r.chromaAmount() + "|" + r.chromaLightness()
            + "|" + (r.gradientStart() & 0xFFFFFF) + "|" + (r.gradientEnd() & 0xFFFFFF)
            + "|" + r.boldEnabled() + "|" + r.customNickColorEnabled() + "|" + (r.nickChanger() == null ? "" : r.nickChanger())
            + "|" + r.gradientStyle() + "|" + r.shimmerSpeed() + "|" + r.shimmerColor() + "|" + r.shimmerHighlightColor()
            + "|" + java.util.Arrays.toString(r.chromaPalette() == null ? new int[0] : r.chromaPalette()));
    }


    private static void loadCacheFromDisk() {
        try {
            Path cache = PresenceCache.path(CosmeticsConfigDir.getCosmeticsConfigDir(), serviceBaseUrl());
            if (serviceBaseUrl().isBlank() || !Files.exists(cache)) return;
            String json = Files.readString(cache, StandardCharsets.UTF_8);
            if (json == null || json.isBlank()) return;
            applyPayload(json, false);
            saveCacheToDisk(json);
        } catch (Exception ignored) {
        }
    }

    private static void saveCacheToDisk(String json) {
        try {
            if (json == null || json.isBlank()) return;
            Path cache = PresenceCache.path(CosmeticsConfigDir.getCosmeticsConfigDir(), serviceBaseUrl());
            Path parent = cache.getParent();
            if (parent != null && !Files.exists(parent)) {
                Files.createDirectories(parent);
            }
            JsonObject cached = PresenceCache.clean(json);
            Files.writeString(cache, CACHE_GSON.toJson(cached), StandardCharsets.UTF_8);
        } catch (Exception ignored) {
        }
    }

    private static String formatPrettyCacheJson(String json) {
        return CACHE_GSON.toJson(JsonParser.parseString(json));
    }

    private static boolean isMinifiedCacheJson(String json) {
        if (json == null || json.isBlank()) {
            return false;
        }
        return !json.contains("\n") && !json.contains("\r");
    }

    private static int[] parsePalette(JsonArray array) {
        if (array == null || array.isEmpty()) return new int[0];
        ArrayList<Integer> colors = new ArrayList<>();
        for (JsonElement element : array) {
            if (!element.isJsonPrimitive()) continue;
            String hex = element.getAsString();
            if (hex == null || !hex.matches("^#([A-Fa-f0-9]{6})$")) continue;
            try {
                colors.add(Integer.parseInt(hex.substring(1), 16));
            } catch (Exception ignored) {
            }
        }
        int[] out = new int[colors.size()];
        for (int i = 0; i < colors.size(); i++) out[i] = colors.get(i);
        return out;
    }

    private static String getAsString(JsonObject obj, String key, String fallback) {
        if (obj == null || !obj.has(key)) return fallback;
        try {
            return obj.get(key).getAsString();
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static int parseHexColor(JsonObject obj, String key, int fallback) {
        String raw = getAsString(obj, key, "");
        if (!raw.matches("^#([A-Fa-f0-9]{6})$")) return fallback;
        try {
            return Integer.parseInt(raw.substring(1), 16);
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static boolean getAsBoolean(JsonObject obj, String key, boolean fallback) {
        if (obj == null || !obj.has(key)) return fallback;
        try {
            return obj.get(key).getAsBoolean();
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static double getAsDouble(JsonObject obj, String key, double fallback) {
        if (obj == null || !obj.has(key)) return fallback;
        try {
            return obj.get(key).getAsDouble();
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static long parseIsoEpochMs(String iso) {
        if (iso == null || iso.isBlank()) return -1L;
        try {
            return java.time.Instant.parse(iso).toEpochMilli();
        } catch (Exception ignored) {
            return -1L;
        }
    }

    public record ChromaProfile(
            boolean chromaEnabled,
            int[] palette,
            double speed,
            double chromaSize,
            double chromaAmount,
            double chromaLightness,
            int gradientStart,
            int gradientEnd,
            boolean boldSelf,
            boolean customNickColorEnabled,
            String nickChanger,
            String gradientStyle,
            double shimmerSpeed,
            int shimmerColor,
            int shimmerHighlightColor
    ) {
        public ChromaProfile(boolean chromaEnabled, int[] palette, double speed, double chromaSize, double chromaAmount, double chromaLightness, int gradientStart, int gradientEnd, boolean boldSelf, boolean customNickColorEnabled, String nickChanger, String gradientStyle, double shimmerSpeed) {
            this(chromaEnabled, palette, speed, chromaSize, chromaAmount, chromaLightness, gradientStart, gradientEnd, boldSelf, customNickColorEnabled, nickChanger, gradientStyle, shimmerSpeed, 0xFFCF64, 0xFFF0BB);
        }
        public ChromaProfile {
            shimmerColor &= 0xFFFFFF; shimmerHighlightColor &= 0xFFFFFF;
            if (palette == null) palette = new int[0];
            gradientStart &= 0xFFFFFF;
            gradientEnd &= 0xFFFFFF;
            if (nickChanger == null) nickChanger = "";
            gradientStyle = com.customcosmetics.features.nickname.NicknameGradient.normalizeStyle(gradientStyle);
            shimmerSpeed = Double.isFinite(shimmerSpeed) ? clamp(shimmerSpeed, 0, 4) : 1;
        }

        public int[] paletteView() {
            return palette.length == 0 ? new int[0] : palette.clone();
        }
    }

    public record RemoteUserState(
            UUID uuid,
            String name,
            boolean smolPeopleEnabled,
            double smolLimbSwingSpeed,
            boolean nickTweaksEnabled,
            boolean chromaEnabled,
            int[] chromaPalette,
            double chromaSpeed,
            double chromaSize,
            double chromaAmount,
            double chromaLightness,
            int gradientStart,
            int gradientEnd,
            boolean boldEnabled,
            boolean customNickColorEnabled,
            String nickChanger,
            long lastSeenEpochMs,
            boolean stale,
            String gradientStyle,
            double shimmerSpeed,
            int shimmerColor,
            int shimmerHighlightColor
    ) {
    }

    private record LocalUserState(
            UUID uuid,
            String name,
            boolean nickTweaksEnabled,
            boolean chromaEnabled,
            boolean smolEnabled,
            double smolLimbSwingSpeed,
            int[] chromaPalette,
            double chromaSpeed,
            double chromaSize,
            double chromaAmount,
            double chromaLightness,
            int gradientStart,
            int gradientEnd,
            boolean boldSelf,
            boolean customNickColorEnabled,
            String nickChanger,
            String gradientStyle,
            double shimmerSpeed,
            int shimmerColor,
            int shimmerHighlightColor
    ) {
        String signature() {
            return uuid + "|" + name + "|" + nickTweaksEnabled + "|" + chromaEnabled + "|" + smolEnabled + "|" + smolLimbSwingSpeed + "|" + chromaSpeed
                    + "|" + chromaSize + "|" + chromaAmount + "|" + chromaLightness
                    + "|" + gradientStart + "|" + gradientEnd + "|" + boldSelf + "|" + customNickColorEnabled + "|" + nickChanger
                    + "|" + gradientStyle + "|" + shimmerSpeed + "|" + shimmerColor + "|" + shimmerHighlightColor
                    + "|" + java.util.Arrays.toString(chromaPalette);
        }
    }
}
