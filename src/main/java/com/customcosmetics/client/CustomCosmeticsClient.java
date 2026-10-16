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
    public static CosmeticsScreen.Page openGuiPageNextTick=CosmeticsScreen.Page.MAIN;
    public static void requestGui(CosmeticsScreen.Page page){openGuiPageNextTick=page;openGuiNextTick=true;}

    @Override
    public void onInitializeClient() {
        CosmeticsConfigDir.init();
        ConfigManager.loadConfig();
        com.customcosmetics.features.cape.CapeCosmetics.init();
        com.customcosmetics.features.nickname.NicknamePresets.init();
        com.customcosmetics.features.items.ItemCosmetics.init();
        com.customcosmetics.features.map.MapEditor.init();
        com.customcosmetics.features.skins.LocalSkins.init();
        PresenceSync.init();
        com.customcosmetics.features.party.PartyFeatures.init();
        com.customcosmetics.features.damage.DamageSplash.init();
        com.customcosmetics.update.UpdateChecker.init();
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, access) -> SyncCommands.register(dispatcher));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (com.customcosmetics.sync.RemoteAccess.online()) com.customcosmetics.features.animations.PresenceWorld.init();
            ConfigManager.flushPendingSave();
            com.customcosmetics.features.map.MapEditor.tick(client);
            com.customcosmetics.features.damage.DamageSplash.tick(client);
            com.customcosmetics.features.cape.CapeCosmetics.tick();
            com.customcosmetics.features.items.ItemCosmetics.tick();
            PresenceSync.tick();
            com.customcosmetics.features.party.PartyFeatures.tick(client);
            com.customcosmetics.update.UpdateChecker.tick(client);
            SwordBlocking.tick(client);
            com.customcosmetics.sync.RealtimeSync.tick(client);
            com.customcosmetics.features.skins.LocalSkins.tick();
            com.customcosmetics.sync.SyncDebug.tick();
            com.customcosmetics.features.animations.ViewModelAnimations.tick(client);
            com.customcosmetics.features.blocking.VisualBlockhit.tick(client);
            if (openGuiNextTick) {
                var page=openGuiPageNextTick;openGuiPageNextTick=CosmeticsScreen.Page.MAIN;
                com.customcosmetics.compatibility.ClientUiAccess.setScreen(client, new CosmeticsScreen(null, page));
                openGuiNextTick = false;
            }
        });
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            com.customcosmetics.features.cape.CapeCosmetics.close();
            com.customcosmetics.features.skins.LocalSkins.close();
            com.customcosmetics.gui.BlockingPreview.close();
            com.customcosmetics.sync.BlockingSync.disconnect();
            com.customcosmetics.sync.PartySync.disconnect();
            ConfigManager.saveConfig();
            com.customcosmetics.features.items.ItemCosmetics.save();
        });
        LOGGER.info("CustomCosmetics initialized");
    }
}
