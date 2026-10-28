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
    private record TickStep(ModErrors.Key key,java.util.function.Consumer<net.minecraft.client.Minecraft> action){}
    private static java.util.function.Supplier<net.minecraft.client.gui.screens.Screen> requestedScreen;
    public static void requestGui(CosmeticsScreen.Page page){requestedScreen=null;openGuiPageNextTick=page;openGuiNextTick=true;}
    public static void requestScreen(java.util.function.Supplier<net.minecraft.client.gui.screens.Screen> screen){requestedScreen=screen;openGuiNextTick=true;}

    @Override
    public void onInitializeClient() {
        CosmeticsConfigDir.init();
        ConfigManager.loadConfig();
        com.customcosmetics.gui.UiLocale.refresh();
        ModErrors.run(ModErrors.Key.CAPE, com.customcosmetics.features.cape.CapeCosmetics::init);
        ModErrors.run(ModErrors.Key.STARTUP, com.customcosmetics.features.nickname.NicknamePresets::init);
        ModErrors.run(ModErrors.Key.ITEMS, com.customcosmetics.features.items.ItemCosmetics::init);
        ModErrors.run(ModErrors.Key.ACCESSORIES, com.customcosmetics.features.accessoryhelper.AccessoryTooltipHelper::init);
        ModErrors.run(ModErrors.Key.MAP, com.customcosmetics.features.map.MapEditor::init);
        ModErrors.run(ModErrors.Key.SNAPLOOK, com.customcosmetics.features.camera.Snaplook::init);
        ModErrors.run(ModErrors.Key.SKINS, com.customcosmetics.features.skins.LocalSkins::init);
        ModErrors.run(ModErrors.Key.SYNC, PresenceSync::init);
        ModErrors.run(ModErrors.Key.PARTY, com.customcosmetics.features.party.PartyFeatures::init);
        ModErrors.run(ModErrors.Key.DAMAGE, com.customcosmetics.features.damage.DamageSplash::init);
        ModErrors.run(ModErrors.Key.UPDATES, com.customcosmetics.update.UpdateChecker::init);
        ModErrors.run(ModErrors.Key.WAYPOINTS, com.customcosmetics.features.waypoint.Waypoints::init);
        ModErrors.run(ModErrors.Key.SHORTCUTS, com.customcosmetics.features.shortcuts.Shortcuts::init);
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, access) -> ModErrors.attempt(ModErrors.Key.COMMANDS,()->SyncCommands.register(dispatcher)));
        // Callback objects are built once; ordinary ticks do not allocate guard lambdas.
        TickStep[] steps={
            new TickStep(ModErrors.Key.CREATURE,com.customcosmetics.features.creature.CreatureModels::tick),
            new TickStep(ModErrors.Key.WORLD,c->{if(com.customcosmetics.sync.RemoteAccess.online())com.customcosmetics.features.animations.PresenceWorld.init();}),
            new TickStep(ModErrors.Key.CONFIG,c->ConfigManager.flushPendingSave()),
            new TickStep(ModErrors.Key.SIZE,com.customcosmetics.features.smolpeople.SmolPlayers::tick),
            new TickStep(ModErrors.Key.MAP,com.customcosmetics.features.map.MapEditor::tick),
            new TickStep(ModErrors.Key.FREECAM,com.customcosmetics.features.map.Freecam::tick),
            new TickStep(ModErrors.Key.SNAPLOOK,com.customcosmetics.features.camera.Snaplook::tick),
            new TickStep(ModErrors.Key.DAMAGE,com.customcosmetics.features.damage.DamageSplash::tick),
            new TickStep(ModErrors.Key.CAPE,c->com.customcosmetics.features.cape.CapeCosmetics.tick()),
            new TickStep(ModErrors.Key.ITEMS,c->com.customcosmetics.features.items.ItemCosmetics.tick()),
            new TickStep(ModErrors.Key.SYNC,c->PresenceSync.tick()),
            new TickStep(ModErrors.Key.PARTY,com.customcosmetics.features.party.PartyFeatures::tick),
            new TickStep(ModErrors.Key.UPDATES,com.customcosmetics.update.UpdateChecker::tick),
            new TickStep(ModErrors.Key.BLOCKING,SwordBlocking::tick),
            new TickStep(ModErrors.Key.REALTIME,com.customcosmetics.sync.RealtimeSync::tick),
            new TickStep(ModErrors.Key.SKINS,c->com.customcosmetics.features.skins.LocalSkins.tick()),
            new TickStep(ModErrors.Key.DEBUG,c->com.customcosmetics.sync.SyncDebug.tick()),
            new TickStep(ModErrors.Key.ANIMATIONS,com.customcosmetics.features.animations.ViewModelAnimations::tick),
            new TickStep(ModErrors.Key.BLOCKHIT,com.customcosmetics.features.blocking.VisualBlockhit::tick)
        };
        ClientTickEvents.END_CLIENT_TICK.register(client->{
            for(var step:steps)ModErrors.tick(step.key(),client,step.action());
            if(openGuiNextTick){
                var page=openGuiPageNextTick;openGuiPageNextTick=CosmeticsScreen.Page.MAIN;var screen=requestedScreen;requestedScreen=null;openGuiNextTick=false;
                ModErrors.attempt(ModErrors.Key.GUI_OPEN,()->com.customcosmetics.compatibility.ClientUiAccess.setScreen(client,screen==null?new CosmeticsScreen(null,page):screen.get()));
            }
            ModErrors.flush(client);
        });
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            ModErrors.cleanup(ModErrors.Key.SNAPLOOK,()->com.customcosmetics.features.camera.Snaplook.stop(client));
            ModErrors.cleanup(ModErrors.Key.FREECAM,()->com.customcosmetics.features.map.Freecam.stop(client));
            ModErrors.cleanup(ModErrors.Key.MAP,com.customcosmetics.features.map.MapEditor::flushRooms);
            ModErrors.cleanup(ModErrors.Key.CAPE,com.customcosmetics.features.cape.CapeCosmetics::close);
            ModErrors.cleanup(ModErrors.Key.SKINS,com.customcosmetics.features.skins.LocalSkins::close);
            ModErrors.cleanup(ModErrors.Key.PREVIEW,com.customcosmetics.gui.BlockingPreview::close);
            ModErrors.cleanup(ModErrors.Key.BLOCKING,com.customcosmetics.sync.BlockingSync::disconnect);
            ModErrors.cleanup(ModErrors.Key.PARTY,com.customcosmetics.sync.PartySync::disconnect);
            ModErrors.cleanup(ModErrors.Key.CONFIG,ConfigManager::saveConfig);
            ModErrors.cleanup(ModErrors.Key.CONFIG,ConfigManager::awaitPendingSaves);
            ModErrors.cleanup(ModErrors.Key.ITEMS,com.customcosmetics.features.items.ItemCosmetics::save);
            ModErrors.cleanup(ModErrors.Key.ITEMS,com.customcosmetics.features.items.ItemCosmetics::flush);
        });
        LOGGER.info("CustomCosmetics initialized");
    }
}
