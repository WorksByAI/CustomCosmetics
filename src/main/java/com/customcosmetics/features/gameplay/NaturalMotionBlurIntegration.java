// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.features.gameplay;

import com.customcosmetics.client.CustomCosmeticsClient;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.screens.Screen;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public final class NaturalMotionBlurIntegration implements net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint {
    private static final String MOD_ID = "naturalmotionblur";
    private static final String CONFIG_CLASS = "net.natural.motionblur.config.ConfigManager";

    public static boolean available() {
        return FabricLoader.getInstance().isModLoaded(MOD_ID);
    }

    @Override public void onPreLaunch() {
        if (!available()) return;
        var configDir = FabricLoader.getInstance().getConfigDir();
        var target = configDir.resolve("naturalmotionblur.json");
        if (Files.exists(target)) return;
        try {
            Files.createDirectories(configDir);
            Files.writeString(target, migratedSettings(configDir.resolve("customcosmetics/config.json")).toString(),
                StandardOpenOption.CREATE_NEW);
        } catch (java.nio.file.FileAlreadyExistsException ignored) {
            // Keep an existing upstream configuration.
        } catch (IOException error) {
            org.slf4j.LoggerFactory.getLogger("CustomSkyBlock").warn("Could not initialize Natural Motion Blur", error);
        }
    }

    static JsonObject migratedSettings(Path oldConfig) {
        JsonObject result = new JsonObject();
        result.addProperty("enabled", false);
        if (!Files.isRegularFile(oldConfig)) return result;
        try {
            var old = JsonParser.parseString(Files.readString(oldConfig)).getAsJsonObject().getAsJsonObject("settings");
            if (old == null) return result;
            copy(old, result, "motionBlurEnabled", "enabled");
            copy(old, result, "motionBlurStrength", "motionBlurStrength");
            copy(old, result, "motionBlurRefreshRate", "refreshRateScaling");
            if (old.has("motionBlurWeighted") && old.get("motionBlurWeighted").isJsonPrimitive())
                result.addProperty("blurProfile", old.get("motionBlurWeighted").getAsBoolean() ? "WEIGHTED" : "UNIFORM");
        } catch (IOException | RuntimeException error) {
            org.slf4j.LoggerFactory.getLogger("CustomSkyBlock").warn("Could not migrate CustomSkyBlock motion blur settings", error);
        }
        return result;
    }

    private static void copy(JsonObject old, JsonObject result, String source, String target) {
        if (old.has(source) && old.get(source).isJsonPrimitive()) result.add(target, old.get(source));
    }

    public static Screen open(Screen parent) {
        try {
            return (Screen) Class.forName(CONFIG_CLASS).getMethod("createConfigScreen", Screen.class).invoke(null, parent);
        } catch (ReflectiveOperationException | LinkageError error) {
            CustomCosmeticsClient.LOGGER.warn("Could not open Natural Motion Blur configuration", error);
            return parent;
        }
    }
}
