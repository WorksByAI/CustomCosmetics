package com.customcosmetics.client;

import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.customcosmetics.config.ConfigManager;
import com.customcosmetics.config.CosmeticsConfigDir;
import com.customcosmetics.features.blocking.SwordBlocking;
import com.customcosmetics.gui.CosmeticsScreen;
import com.customcosmetics.sync.PresenceSync;
import com.customcosmetics.sync.SyncCommands;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

public final class CustomCosmeticsClient implements ClientModInitializer {
    public static final String MOD_ID = "customcosmetics";
    public static final Logger LOGGER = LoggerFactory.getLogger("CustomCosmetics");
    public static boolean openGuiNextTick;

    @Override
    public void onInitializeClient() {
        CosmeticsConfigDir.init();
        ConfigManager.loadConfig();
        com.customcosmetics.features.items.ItemCosmetics.init();
        PresenceSync.init();
        com.customcosmetics.update.UpdateChecker.init();
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, access) -> SyncCommands.register(dispatcher));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            com.customcosmetics.features.animations.PresenceWorld.init();
            ConfigManager.flushPendingSave();
            com.customcosmetics.features.items.ItemCosmetics.tick();
            PresenceSync.tick();
            com.customcosmetics.update.UpdateChecker.tick(client);
            SwordBlocking.tick(client);
            com.customcosmetics.features.animations.ViewModelAnimations.tick(client);
            com.customcosmetics.features.blocking.VisualBlockhit.tick(client);
            if (openGuiNextTick) {
                com.customcosmetics.compatibility.ClientUiAccess.setScreen(client, new CosmeticsScreen(null, CosmeticsScreen.Page.MAIN));
                openGuiNextTick = false;
            }
        });
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            ConfigManager.saveConfig();
            com.customcosmetics.features.items.ItemCosmetics.save();
        });
        LOGGER.info("CustomCosmetics initialized");
    }
}
