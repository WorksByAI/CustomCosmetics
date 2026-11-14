// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.features.motionblur;

import com.customcosmetics.client.CustomCosmeticsClient;
import com.customcosmetics.gui.CosmeticsScreen;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.brigadier.arguments.FloatArgumentType;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.network.chat.Component;

public final class MotionBlurPort {
    private static final FabricLoader LOADER = FabricLoader.getInstance();
    private static final Path CONFIG = LOADER.getConfigDir().resolve("naturalmotionblur.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static BlurSettings config = new BlurSettings();

    private MotionBlurPort() {}

    public static BlurSettings getConfig() { return config; }

    public static boolean integrated() {
        return !LOADER.isModLoaded("naturalmotionblur") && !LOADER.isModLoaded("vulkanmod");
    }

    public static void init() {
        if (!integrated()) return;
        load();
        IntegratedClientPorts.launch("motionblur", "NaturalMotionBlurMod");
        var key = KeyMappingHelper.registerKeyMapping(new KeyMapping("Toggle Motion Blur",
            InputConstants.UNKNOWN.getValue(), KeyMapping.Category.MISC));
        ClientTickEvents.START_CLIENT_TICK.register(client -> {
            while (key.consumeClick()) {
                config.enabled = !config.enabled;
                save();
            }
        });
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registry) -> {
            for (String command : new String[]{"motionblur", "mb"}) dispatcher.register(ClientCommands.literal(command)
                .executes(context -> {
                    CustomCosmeticsClient.requestGui(CosmeticsScreen.Page.MOTION_BLUR);
                    return 1;
                })
                .then(ClientCommands.argument("strength", FloatArgumentType.floatArg(0, 2))
                    .executes(context -> {
                        config.motionBlurStrength = FloatArgumentType.getFloat(context, "strength");
                        save();
                        context.getSource().sendFeedback(Component.literal("Motion blur strength set to " + config.motionBlurStrength));
                        return 1;
                    })));
        });
    }

    private static void load() {
        try {
            if (Files.isRegularFile(CONFIG)) {
                var saved = GSON.fromJson(Files.readString(CONFIG), BlurSettings.class);
                if (saved != null) config = saved;
            } else {
                Path legacy = CONFIG.getParent().resolve("customcosmetics/config.json");
                if (Files.isRegularFile(legacy)) {
                    var old = JsonParser.parseString(Files.readString(legacy)).getAsJsonObject().getAsJsonObject("settings");
                    if (old != null) {
                        if (old.has("motionBlurEnabled")) config.enabled = old.get("motionBlurEnabled").getAsBoolean();
                        if (old.has("motionBlurStrength")) config.motionBlurStrength = old.get("motionBlurStrength").getAsFloat();
                        if (old.has("motionBlurRefreshRate")) config.refreshRateScaling = old.get("motionBlurRefreshRate").getAsBoolean();
                        if (old.has("motionBlurWeighted") && old.get("motionBlurWeighted").getAsBoolean())
                            config.blurProfile = BlurSettings.BlurProfile.WEIGHTED;
                    }
                }
            }
        } catch (IOException | RuntimeException failure) {
            CustomCosmeticsClient.LOGGER.warn("Could not read motion blur settings", failure);
        }
        if (config.blurAlgorithm == null) config.blurAlgorithm = BlurSettings.BlurAlgorithm.VELOCITY_BASED;
        if (config.blurProfile == null) config.blurProfile = BlurSettings.BlurProfile.UNIFORM;
        config.recordingOverlayTargetFPS = Math.clamp(config.recordingOverlayTargetFPS, 24, 240);
        config.motionBlurStrength = Float.isFinite(config.motionBlurStrength) ? Math.clamp(config.motionBlurStrength, 0, 2) : 1;
    }

    public static void save() {
        try {
            Files.createDirectories(CONFIG.getParent());
            Path pending = Files.createTempFile(CONFIG.getParent(), "csb-motionblur-", ".tmp");
            try {
                Files.writeString(pending, GSON.toJson(config));
                Files.move(pending, CONFIG, java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            } finally { Files.deleteIfExists(pending); }
        } catch (IOException failure) {
            CustomCosmeticsClient.LOGGER.warn("Could not save motion blur settings", failure);
        }
    }
}
