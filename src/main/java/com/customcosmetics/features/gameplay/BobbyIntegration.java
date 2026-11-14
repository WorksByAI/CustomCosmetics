// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.features.gameplay;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** Shares Bobby's configuration with existing installations and updates the integrated client on save. */
public final class BobbyIntegration implements net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint {
    private static final Path CONFIG = FabricLoader.getInstance().getConfigDir().resolve("bobby.conf");
    private static final String[] KEYS = {"enabled", "dynamic-multi-world", "no-block-entities", "taint-fake-chunks",
        "unload-delay-secs", "delete-unused-regions-after-days", "max-render-distance", "view-distance-overwrite"};
    private static final int[] DEFAULTS = {1, 1, 1, 0, 60, -1, 32, 0};
    private static java.util.function.Consumer<int[]> settingsChanged = values -> {};

    public static void init() {
        if (!FabricLoader.getInstance().isModLoaded("bobby")) {
            initializeDefaults();
            com.customcosmetics.features.motionblur.IntegratedClientPorts.launch("bobby", "Bobby");
        }
    }

    @Override public void onPreLaunch() {
        if (FabricLoader.getInstance().isModLoaded("bobby")) initializeDefaults();
    }

    private static void initializeDefaults() {
        if (Files.exists(CONFIG)) return;
        try {
            Files.createDirectories(CONFIG.getParent());
            Files.writeString(CONFIG, "dynamic-multi-world=true\n", java.nio.file.StandardOpenOption.CREATE_NEW);
        } catch (java.nio.file.FileAlreadyExistsException ignored) {
            // An existing or concurrently created config owns the user's setting.
        } catch (IOException failure) {
            org.slf4j.LoggerFactory.getLogger("CustomSkyBlock").warn("Could not initialize Bobby's multi-world default", failure);
        }
    }

    public static void onSave(java.util.function.Consumer<int[]> listener) { settingsChanged = listener; }

    public static synchronized int[] read() {
        int[] values = DEFAULTS.clone();
        if (!Files.isRegularFile(CONFIG)) return values;
        values[1] = 0;
        try {
            var text = Files.readString(CONFIG);
            for (int i = 0; i < KEYS.length; i++) {
                var match = entry(KEYS[i]).matcher(text);
                if (!match.find()) continue;
                String value = match.group(2);
                values[i] = value.equals("true") ? 1 : value.equals("false") ? 0 : Integer.parseInt(value);
            }
        } catch (IOException | NumberFormatException invalid) {
            throw new IllegalStateException("Cannot read Bobby settings", invalid);
        }
        return values;
    }

    private static java.util.regex.Pattern entry(String key) {
        return java.util.regex.Pattern.compile("(?m)^([ \\t]*" + key + "[ \\t]*[=:][ \\t]*)(true|false|-?[0-9]+)(?=[ \\t,#}\\r\\n]|$)");
    }

    public static synchronized void save(int[] values) {
        try {
            Files.createDirectories(CONFIG.getParent());
            String text = Files.isRegularFile(CONFIG) ? Files.readString(CONFIG) : "";
            for (int i = 0; i < KEYS.length; i++) {
                String value = i < 4 ? Boolean.toString(values[i] != 0) : Integer.toString(values[i]);
                var existing = entry(KEYS[i]).matcher(text);
                if (existing.find()) text = existing.replaceFirst(match -> java.util.regex.Matcher.quoteReplacement(match.group(1) + value));
                else text = text.stripTrailing() + "\n" + KEYS[i] + "=" + value + "\n";
            }
            Path temporary = Files.createTempFile(CONFIG.getParent(), "csb-bobby-", ".conf.tmp");
            try {
                Files.writeString(temporary, text, StandardCharsets.UTF_8);
                Files.move(temporary, CONFIG, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } finally { Files.deleteIfExists(temporary); }
            settingsChanged.accept(values);
        } catch (IOException failure) {
            throw new IllegalStateException("Cannot save Bobby settings", failure);
        }
    }

}
