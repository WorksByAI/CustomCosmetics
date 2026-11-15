// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.gametest;

import com.google.gson.Gson;
import com.google.gson.JsonParser;
import com.customcosmetics.compatibility.ClientInput;
import com.customcosmetics.compatibility.ClientUiAccess;
import com.customcosmetics.config.ConfigManager;
import com.customcosmetics.features.camera.CameraZoom;
import com.customcosmetics.features.gameplay.GameplayKeybinds;
import com.customcosmetics.features.gameplay.GameplayModule;
import com.customcosmetics.features.motionblur.BlurSettings;
import com.customcosmetics.features.motionblur.IntegratedClientPorts;
import com.customcosmetics.features.motionblur.MotionBlurPort;
import com.customcosmetics.gui.CosmeticsScreen;
import com.customcosmetics.gui.PinkUi;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public final class CustomSkyBlockGameTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        context.runOnClient(client -> {
            ConfigManager.offlineMode = true;
            ConfigManager.uiAnimationsEnabled = false;
            ConfigManager.gameplayPanelConfirmed = false;
            client.getWindow().setGuiScale(1);
            ClientUiAccess.setScreen(client, new CosmeticsScreen(null, CosmeticsScreen.Page.MAIN));
        });
        context.waitTicks(2);
        context.runOnClient(client -> {
            var cheating = widget(client, "Cheating", false);
            int fixedY = cheating.getY();
            int footerY = widget(client, "Settings", false).getY();
            check(cheating.getBottom() < footerY && footerY - cheating.getBottom() <= 16,
                "Cheating is outside the sidebar footer: " + fixedY + "–" + cheating.getBottom() + ", Settings " + footerY);
            check(!widget(client, "Nick Changer", true).visible, "Appearance should start collapsed");
            click(client, "Appearance", false);
            check(widget(client, "Cape", true).visible, "Appearance did not open");
            click(client, "Connections", false);
            check(!widget(client, "Nick Changer", true).visible, "Opening Connections did not collapse Appearance");
            check(widget(client, "Party Sharing", true).active, "Connections did not open");
            click(client, "SkyBlock", false);
            check(widget(client, "Tooltip Tweaks", true).visible, "SkyBlock did not open");
            click(client, "QOL", false);
            check(cheating.visible && cheating.active && cheating.getY() == fixedY,
                "Opening the long QOL list displaced the Cheating button");
            click(client, "Fonts", true);
            check(widget(client, "Use Game Font", false).visible, "Offline QOL did not open Fonts");
            ClientUiAccess.setScreen(client, new CosmeticsScreen(null, CosmeticsScreen.Page.ZOOM));
            check(widget(client, "Zoom Animation", false) instanceof PinkUi.Toggle, "Zoom animation toggle is missing");
            check(widget(client, "Scroll Below Initial Zoom", false) instanceof PinkUi.Toggle, "Zoom scroll limit toggle is missing");
            check(widget(client, "Zoom In (s):", false) instanceof PinkUi.Slider
                && widget(client, "Zoom Out (s):", false) instanceof PinkUi.Slider, "Zoom transition sliders are missing");
            ClientUiAccess.setScreen(client, new CosmeticsScreen(null, CosmeticsScreen.Page.TOOLTIP_TWEAKS));
            check(widget(client, "Remove Gear Score", false) instanceof PinkUi.Toggle, "Tooltip Tweaks page did not open");
            var missingEnchants = widget(client, "Missing Enchants", false);
            check(missingEnchants instanceof PinkUi.Toggle && widget(client, "Require Shift", false) instanceof PinkUi.Toggle,
                "Missing Enchants controls are missing from Tooltip Tweaks or remain in the sidebar");
            ClientUiAccess.setScreen(client, new CosmeticsScreen(null, CosmeticsScreen.Page.HIT_COLOR));
            check(ClientUiAccess.screen(client).children().stream().anyMatch(child -> child instanceof PinkUi.Toggle toggle
                && toggle.getMessage().getString().startsWith("Hit Color") && toggle.visible), "Offline QOL did not open Hit Color");
            click(client, "Cheating", false);
            check(!widget(client, "I understand", false).active, "Confirmation was immediately enabled");
            check(widget(client, "Cancel", false).active, "Cancellation should stay available");
        });
        screenshot(context, "csb-300-confirm");
        context.waitFor(client -> widget(client, "I understand", false).active, 150);
        context.runOnClient(client -> {
            click(client, "I understand", false);
            check(ConfigManager.gameplayPanelConfirmed, "Confirmation was not saved");
            var a = widget(client, "Auto Book Combine", false);
            var b = widget(client, "Auto Clicker", false);
            var c = widget(client, "Auto Experiments", false);
            check(a.getY() == b.getY() && b.getY() == c.getY() && a.getX() < b.getX() && b.getX() < c.getX(),
                "Modules were not laid out in three sorted columns");
            click(client, "Auto Clicker", false, ClientInput.MOUSE_RIGHT);
            check(widget(client, "Left Click Keybind", false) instanceof PinkUi.Action
                && widget(client, "Right Maximum CPS:", false) instanceof PinkUi.Slider, "Auto Clicker controls are missing");
            check(widget(client, "Search modules", false).visible && widget(client, "Auto Clicker", false).visible,
                "Settings replaced the module list");
            click(client, "Auto Clicker", false, ClientInput.MOUSE_RIGHT);
            click(client, "Auto Book Combine", false, ClientInput.MOUSE_RIGHT);
            check(widget(client, "Anvil Result Delay (ticks):", false) instanceof PinkUi.Slider,
                "Auto Book Combine result delay is missing");
            click(client, "Auto Book Combine", false, ClientInput.MOUSE_RIGHT);
            click(client, "Auto Experiments", false, ClientInput.MOUSE_RIGHT);
            for (String label : java.util.List.of("Min Click Delay (ms)", "Max Click Delay (ms)", "Serums Applied"))
                check(ClientUiAccess.screen(client).children().stream().anyMatch(child -> child instanceof PinkUi.Slider slider
                    && slider.getMessage().getString().startsWith(label)), "Auto Experiments is missing " + label);
            click(client, "Auto Close", false);
            click(client, "Get Max", false);
            check(ConfigManager.experimentAutoClose && ConfigManager.experimentGetMax,
                "Auto Experiments switches did not update the settings");
            ConfigManager.experimentAutoClose = ConfigManager.experimentGetMax = false;
            click(client, "Auto Experiments", false, ClientInput.MOUSE_RIGHT);
            var leap = (PinkUi.Field) widget(client, "Search modules", false);
            leap.setValue("Health and Mana");
            check(ClientUiAccess.screen(client).children().stream().noneMatch(child -> child instanceof PinkUi.Action action
                && action.getMessage().getString().equals("Health and Mana")), "Removed HUD still has a module card");
            for (String name : java.util.List.of("Auto Kick", "Auto Sell")) {
                leap = (PinkUi.Field) widget(client, "Search modules", false);
                leap.setValue(name);
                check(ClientUiAccess.screen(client).children().stream().noneMatch(child -> child instanceof PinkUi.Action action
                    && action.getMessage().getString().equals(name)), name + " still has a module card");
            }
            check(ClientUiAccess.screen(client).children().stream().filter(AbstractWidget.class::isInstance)
                .map(AbstractWidget.class::cast).noneMatch(widget -> List.of("Kuudra", "Slayer").contains(widget.getMessage().getString())),
                "Retired categories are still visible");
            for (var module : GameplayModule.values()) {
                if (module.category == GameplayModule.Category.DUNGEON) continue;
                ((PinkUi.Field) widget(client, "Search modules", false)).setValue(module.label);
                var card = widget(client, module.label, false);
                int collapsedCount = ClientUiAccess.screen(client).children().size();
                int expandedCount = -1;
                for (int i = 0; i < 2; i++) {
                    click(client, "⚙", false);
                    var keybind = widget(client, "Keybind", false);
                    check(keybind.getY() == widget(client, module.label, false).getY() + 46,
                        "Settings start with extra space for " + module.label);
                    int count = ClientUiAccess.screen(client).children().size();
                    check(expandedCount == -1 || count == expandedCount, "Settings duplicated for " + module.label);
                    expandedCount = count;
                    click(client, "⌃", false);
                    check(ClientUiAccess.screen(client).children().size() == collapsedCount
                        && widget(client, module.label, false).getY() == card.getY(),
                        "Closing settings left extra space for " + module.label);
                }
            }
            ((PinkUi.Field) widget(client, "Search modules", false)).setValue("Dojo");
            click(client, "⚙", false);
            check(widget(client, "Swiftness: Rotation Curve", false) instanceof PinkUi.Action
                && widget(client, "Control: Prediction (ticks):", false) instanceof PinkUi.Slider,
                "Dojo rotation and prediction settings are missing");
            ((PinkUi.Field) widget(client, "Search modules", false)).setValue("Mirrorverse");
            click(client, "⚙", false);
            check(widget(client, "Lava Parkour", false) instanceof PinkUi.Action
                && widget(client, "Punch Delay (ticks):", false) instanceof PinkUi.Slider,
                "Mirrorverse route and dance settings are missing");
            ((PinkUi.Field) widget(client, "Search modules", false)).setValue("Auto GFS");
            click(client, "Auto GFS", false, ClientInput.MOUSE_RIGHT);
            var refillMode = widget(client, "Refill Mode", false);
            int refillY = refillMode.getY();
            int refillHeight = widget(client, "Dungeon Only", false).getY() - refillY;
            int refillWidgetCount = ClientUiAccess.screen(client).children().size();
            for (int i = 0; i < 6; i++) {
                click(client, "Refill Mode", false);
                check(widget(client, "Refill Mode", false).getY() == refillY
                    && widget(client, "Dungeon Only", false).getY() == refillY + refillHeight
                    && ClientUiAccess.screen(client).children().size() == refillWidgetCount,
                    "Rebuilding Auto GFS settings changed their layout or duplicated widgets");
            }
            ((PinkUi.Field) widget(client, "Search modules", false)).setValue("Trevor Helper");
            click(client, "Trevor Helper", false, ClientInput.MOUSE_RIGHT);
            check(widget(client, "Auto Call", false) instanceof PinkUi.Action
                && widget(client, "Cooldown Timer", false) instanceof PinkUi.Action, "Trevor Helper controls are missing");
            ((PinkUi.Field) widget(client, "Search modules", false)).setValue("Render Optimiser");
            click(client, "Render Optimiser", false, ClientInput.MOUSE_RIGHT);
            check(widget(client, "Hide Fog", false) instanceof PinkUi.Action
                && widget(client, "Hide Player Armor", false) instanceof PinkUi.Action, "Render Optimiser controls are missing");
            ((PinkUi.Field) widget(client, "Search modules", false)).setValue("World Scanner");
            click(client, "World Scanner", false, ClientInput.MOUSE_RIGHT);
            check(widget(client, "Name Scale:", false) instanceof PinkUi.Slider
                && widget(client, "Show Number of Blocks", false) instanceof PinkUi.Action, "World Scanner controls are missing");
            var cheating = ClientUiAccess.screen(client);
            click(client, "Dungeon", false);
            check(ClientUiAccess.screen(client) == cheating && widget(client, "Auto Routes", false).visible,
                "Dungeon modules did not open in the CSB grid");
            ((PinkUi.Field) widget(client, "Search modules", false)).setValue("Auto Harp");
            var harp = com.customcosmetics.features.gameplay.GameplayModule.AUTO_HARP;
            if (!harp.enabled()) click(client, "Auto Harp", false);
            click(client, "Enabled Modules", false);
            check(widget(client, "Auto Harp", false).visible, "Enabled modules view omitted a module from another category");
            click(client, "Auto Harp", false);
            check(!harp.enabled() && ClientUiAccess.screen(client).children().stream()
                .noneMatch(widget -> widget instanceof PinkUi.Action action && action.getMessage().getString().equals("Auto Harp")),
                "Disabled module remained in the enabled modules view");
            verifyModuleNotification(client);
            click(client, "Dungeon", false);
            ((PinkUi.Field) widget(client, "Search modules", false)).setValue("Interactive Map");
            click(client, "Interactive Map", false, ClientInput.MOUSE_RIGHT);
            check(widget(client, "Keep chunks loaded", false) instanceof PinkUi.Action
                && widget(client, "Open key", false) instanceof PinkUi.Action,
                "Interactive Map lost its switch or keybind");
            click(client, "Close on", false);
            check(widget(client, "Close on", false).getMessage().getString().contains("Repress"),
                "CSB choice did not write to the Quoi setting");
            click(client, "Close on", false);
            click(client, "+ Visuals", false);
            check(widget(client, "Map scale", false) instanceof PinkUi.Slider, "Nested dungeon setting was lost");
            check(!widget(client, "HUD Editor", false).active, "Dungeon HUD Editor is clickable");
            verifyQuoiModules();
            verifyRouteImport();
        });
        context.waitTicks(6);
        screenshot(context, "csb-301-dungeon");
        screenshot(context, "csb-300-module-search");
        context.runOnClient(client -> {
            ClientUiAccess.screen(client).onClose();
            click(client, "Cheating", false);
            check(widget(client, "Auto Routes", false).visible, "Cheating screen did not restore the Dungeon page");
            ClientUiAccess.screen(client).onClose();
            expireCheatingSession();
            click(client, "Cheating", false);
            check(widget(client, "Auto Harp", false).visible, "Expired Cheating screen did not return to General");
            check(!widget(client, "HUD Editor", false).active, "HUD Editor is clickable");
            ClientUiAccess.setScreen(client, null);
            try {
                var dispatcher = new com.mojang.brigadier.CommandDispatcher<net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource>();
                com.customcosmetics.sync.SyncCommands.register(dispatcher);
                for (String alias : List.of("customskyblock", "csb")) {
                    check(dispatcher.execute(alias, (net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource) null) == 1,
                        "Missing settings command " + alias);
                }
                check(dispatcher.execute("csb cheating", (net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource) null) == 1,
                    "The /csb cheating alias was not executable");
            } catch (com.mojang.brigadier.exceptions.CommandSyntaxException error) {
                throw new AssertionError("/csb cheating failed", error);
            }
        });
        context.waitFor(client -> ClientUiAccess.screen(client) instanceof com.customcosmetics.gui.CheatingScreen, 30);
        context.runOnClient(client -> ClientUiAccess.screen(client).onClose());
        var originalBobby = com.customcosmetics.features.gameplay.BobbyIntegration.read();
        var testBobby = originalBobby.clone();
        testBobby[0] = 1;
        testBobby[7] = 6;
        context.runOnClient(client -> com.customcosmetics.features.gameplay.BobbyIntegration.save(testBobby));
        try (var world = context.worldBuilder().create()) {
            try {
            context.waitTicks(12);
            context.runOnClient(client -> {
                ClientUiAccess.setScreen(client, null);
                verifyCrystalHollowsScanner(client);
                verifyMouseRefocus(client);
                verifyLegacyHeldSkull(client);
                verifyQuoiItemId();
                check(com.customcosmetics.features.gameplay.InventoryAutomation.countEnderPearls(java.util.List.of(
                    new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.ENDER_PEARL, 7),
                    new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.ENDER_PEARL, 9))) == 16,
                    "Auto GFS missed pearls without a SkyBlock ID");
            });
            verifyInteractiveMap(context);
            verifyAutoRoutesTrigger(context);
            context.runOnClient(CustomSkyBlockGameTest::verifyQuoiLeapFixes);
            verifyNetworkChart(context);
            verifyDyeAndZoom(context);
            context.runOnClient(client -> {
                verifyTooltipContent();
                verifyDuplicateEnchantTooltip(client);
                verifyBobby(client);
                check(!MotionBlurPort.getConfig().enabled, "Motion Blur should be off by default");
                check(java.util.Arrays.stream(client.options.keyMappings).anyMatch(key -> key.getName().equals("Toggle Motion Blur")),
                    "Motion Blur keybind was not registered");
                ClientUiAccess.setScreen(client, new CosmeticsScreen(null, CosmeticsScreen.Page.MOTION_BLUR));
                check(widget(client, "Blur Algorithm", false).visible && widget(client, "Enable OBS Spout Output", false).visible,
                    "Motion Blur settings did not open in CustomSkyBlock");
                ClientUiAccess.setScreen(client, null);
                ConfigManager.spinHeadEnabled = true;
                client.player.setYRot(40);
                com.customcosmetics.compatibility.SwingAccess.swingMainHand(client.player);
            });
            for (int i = 1; i <= 8; i++) {
                float yaw = i * 16f;
                context.runOnClient(client -> client.player.setYRot(yaw));
                context.waitTicks(1);
            }
            for (var algorithm : BlurSettings.BlurAlgorithm.values()) {
                var before = new java.util.concurrent.atomic.AtomicLong();
                context.runOnClient(client -> {
                    before.set(blurPasses());
                    MotionBlurPort.getConfig().blurAlgorithm = algorithm;
                    MotionBlurPort.getConfig().enabled = true;
                });
                for (int i = 0; i < 8; i++) {
                    float yaw = (algorithm.ordinal() * 8 + i) * 18f;
                    context.runOnClient(client -> client.player.setYRot(yaw));
                    context.waitTicks(1);
                }
                context.runOnClient(client -> check(blurPasses() > before.get(), algorithm + " did not submit a GPU pass"));
                System.out.println("CSB_MOTION_BLUR_PASS " + algorithm);
            }
            context.runOnClient(client -> {
                MotionBlurPort.getConfig().enabled = false;
                ClientUiAccess.setScreen(client, new CosmeticsScreen(null, CosmeticsScreen.Page.PACK_DISABLER));
                check(widget(client, "Hide Hypixel SkyBlock Pack", false).visible, "Pack Disabler was gated while offline");
            });
            screenshot(context, "csb-300-pack-disabler");
            } catch (RuntimeException | AssertionError failure) {
                failure.printStackTrace();
                throw failure;
            }
        } finally {
            context.runOnClient(client -> com.customcosmetics.features.gameplay.BobbyIntegration.save(originalBobby));
        }
        System.out.println("CSB300_ACCORDION_CONFIRMATION_MODULE_GRID_HUD_MOTION_PACK_OK");
    }

    private static void expireCheatingSession() {
        try {
            var field = com.customcosmetics.gui.CheatingScreen.class.getDeclaredField("SESSION");
            field.setAccessible(true);
            var session = field.get(null);
            var time = session.getClass().getDeclaredField("lastInteraction");
            time.setAccessible(true);
            time.setLong(session, System.currentTimeMillis() - com.customcosmetics.gui.ScreenSession.IDLE_MILLIS);
        } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
    }

    private static void verifyQuoiItemId() {
        var tool = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND_PICKAXE);
        var data = new net.minecraft.nbt.CompoundTag();
        data.putString("id", "DUNGEONBREAKER");
        tool.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.of(data));
        try {
            var items = Class.forName("quoi.utils.skyblock.item.ItemUtils");
            var id = items.getMethod("getSkyblockId", net.minecraft.world.item.ItemStack.class)
                .invoke(items.getField("INSTANCE").get(null), tool);
            check("DUNGEONBREAKER".equals(id), "Quoi could not access SkyBlock item custom data");
        } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
    }

    private static void verifyQuoiLeapFixes(Minecraft client) {
        var inventory = client.player.getInventory();
        int selectedSlot = inventory.getSelectedSlot();
        var oldFirst = inventory.getItem(0);
        var oldLast = inventory.getItem(8);
        try {
            var island = Class.forName("quoi.api.skyblock.location.Island");
            var area = Class.forName("quoi.api.skyblock.location.Location").getDeclaredField("currentArea");
            var manager = Class.forName("quoi.utils.skyblock.player.LeapManager");
            var cooldownField = manager.getDeclaredField("cooldowns");
            var selectLeap = manager.getDeclaredMethod("selectLeapId", net.minecraft.world.entity.player.Inventory.class);
            var use = Minecraft.class.getDeclaredMethod("startUseItem");
            for (var member : List.of(area, cooldownField, selectLeap, use)) member.setAccessible(true);
            Object oldArea = area.get(null);
            var cooldowns = (Map<?, ?>) cooldownField.get(null);
            var instance = manager.getField("INSTANCE").get(null);
            try {
                area.set(null, island.getField("Dungeon").get(null));
                inventory.setItem(0, skyBlockPaper("INFINITE_SPIRIT_LEAP"));
                inventory.setItem(8, skyBlockPaper("SPIRIT_LEAP"));
                inventory.setSelectedSlot(0);
                check("INFINITE_SPIRIT_LEAP".equals(selectLeap.invoke(instance, inventory)), "Wrong initial leap preference");
                use.invoke(client);
                check(Integer.valueOf(47).equals(cooldowns.get("INFINITE_SPIRIT_LEAP")), "Native right click did not start its own cooldown");
                check("SPIRIT_LEAP".equals(selectLeap.invoke(instance, inventory)), "Available Spirit Leap in the ninth slot was skipped");
                var serverTick = Class.forName("quoi.api.events.TickEvent$Server");
                serverTick.getMethod("post").invoke(serverTick.getConstructor(int.class).newInstance(1));
                use.invoke(client);
                check(Integer.valueOf(46).equals(cooldowns.get("INFINITE_SPIRIT_LEAP")), "Repeated click restarted an active cooldown");
                inventory.setSelectedSlot(8);
                use.invoke(client);
                check(Integer.valueOf(107).equals(cooldowns.get("SPIRIT_LEAP"))
                    && Integer.valueOf(46).equals(cooldowns.get("INFINITE_SPIRIT_LEAP")), "Spirit Leap cooldowns were mixed");
                verifyTerminalLeapDelay();
                var abilities = Class.forName("quoi.module.impl.dungeon.DungeonAbilities");
                var triggers = abilities.getDeclaredField("ultTriggers");
                triggers.setAccessible(true);
                var goldor = ((Map<?, ?>) triggers.get(null)).get("[BOSS] Goldor: You have done it, you destroyed the factory…");
                var healer = Class.forName("quoi.api.skyblock.dungeon.enums.DungeonClass").getField("Healer").get(null);
                check(java.util.Set.of(healer).equals(goldor), "Goldor still triggers Tank or lost Healer");
            } finally {
                cooldowns.clear();
                area.set(null, oldArea);
            }
        } catch (ReflectiveOperationException error) {
            throw new AssertionError("Could not exercise the Quoi leap fixes", error);
        } finally {
            inventory.setItem(0, oldFirst);
            inventory.setItem(8, oldLast);
            inventory.setSelectedSlot(selectedSlot);
        }
        System.out.println("CSB_QUOI_LEAP_TERMINALS_GOLDOR_OK");
    }

    private static net.minecraft.world.item.ItemStack skyBlockPaper(String id) {
        var stack = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.PAPER);
        var tag = new net.minecraft.nbt.CompoundTag();
        tag.putString("id", id);
        stack.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.of(tag));
        return stack;
    }

    private static void verifyTerminalLeapDelay() throws ReflectiveOperationException {
        var type = Class.forName("quoi.module.impl.floor7.Terminals");
        var terminal = type.getField("INSTANCE").get(null);
        var toggle = type.getMethod("toggle");
        boolean wasEnabled = (boolean) type.getMethod("getEnabled").invoke(terminal);
        var setting = type.getMethod("getSettingByName", String.class).invoke(terminal, "Leap delay ticks");
        var read = setting.getClass().getMethod("read", com.google.gson.JsonElement.class);
        var saved = setting.getClass().getMethod("write").invoke(setting);
        var ticks = Class.forName("quoi.api.events.core.EventDispatcher").getDeclaredField("totalTicks");
        var waiting = type.getDeclaredMethod("waitingAfterLeap");
        ticks.setAccessible(true);
        waiting.setAccessible(true);
        int oldTicks = ticks.getInt(null);
        try {
            var config = Class.forName("quoi.config.Config");
            var migrate = config.getDeclaredMethod("migrateTerminalLeapDelay", com.google.gson.JsonElement.class);
            migrate.setAccessible(true);
            migrate.invoke(config.getField("INSTANCE").get(null),
                JsonParser.parseString("{\"Leap delay\":true,\"Leap delay time\":0.75}"));
            check(((com.google.gson.JsonElement) setting.getClass().getMethod("write").invoke(setting)).getAsInt() == 15,
                "Old seconds setting was not converted to ticks");
            migrate.invoke(config.getField("INSTANCE").get(null),
                JsonParser.parseString("[{\"Leap delay\":false},{\"Leap delay time\":0.75}]"));
            check(((com.google.gson.JsonElement) setting.getClass().getMethod("write").invoke(setting)).getAsInt() == 0,
                "Old disabled delay was enabled during migration");
            read.invoke(setting, new com.google.gson.JsonPrimitive(10));
            if (!wasEnabled) toggle.invoke(terminal);
            var teammate = Class.forName("quoi.api.skyblock.dungeon.enums.DungeonPlayer");
            var companion = teammate.getField("Companion").get(null);
            var target = companion.getClass().getMethod("getEMPTY").invoke(companion);
            var leap = Class.forName("quoi.api.events.SkyblockPlayerEvent$Leap");
            leap.getMethod("post").invoke(leap.getConstructor(teammate).newInstance(target));
            ticks.setInt(null, oldTicks + 9);
            check((boolean) waiting.invoke(terminal), "Terminal opened before the configured tick delay");
            ticks.setInt(null, oldTicks + 10);
            check(!(boolean) waiting.invoke(terminal), "Terminal did not unblock at the configured tick");
        } finally {
            type.getMethod("onDisable").invoke(terminal);
            if (!wasEnabled) toggle.invoke(terminal);
            read.invoke(setting, saved);
            ticks.setInt(null, oldTicks);
        }
    }

    private static void verifyInteractiveMap(ClientGameTestContext context) {
        int key = com.mojang.blaze3d.platform.InputConstants.getKey("key.keyboard.j").getValue();
        context.runOnClient(client -> {
            try {
                var location = Class.forName("quoi.api.skyblock.location.Location");
                var island = Class.forName("quoi.api.skyblock.location.Island");
                var area = location.getDeclaredField("currentArea");
                area.setAccessible(true);
                area.set(null, island.getField("Dungeon").get(null));

                var mapType = Class.forName("quoi.module.impl.dungeon.autoclear.impl.InteractiveMap");
                var map = mapType.getField("INSTANCE").get(null);
                var setting = mapType.getMethod("getSettingByName", String.class);
                var open = setting.invoke(map, "Open key");
                check((boolean) open.getClass().getMethod("bindFromMenu", int.class).invoke(open, key),
                    "Interactive Map rejected J as Open key");
                var close = setting.invoke(map, "Close on");
                close.getClass().getMethod("setIndex", int.class).invoke(close, 0);
                if (!(boolean) mapType.getMethod("getEnabled").invoke(map)) mapType.getMethod("toggle").invoke(map);
                sendKey(client, key, com.mojang.blaze3d.platform.InputConstants.PRESS);
            } catch (ReflectiveOperationException error) {
                throw new AssertionError("Interactive Map could not accept its configured hotkey", error);
            }
        });
        context.waitFor(client -> ClientUiAccess.screen(client) != null
            && ClientUiAccess.screen(client).getTitle().getString().equals("quoi clear map"), 30);
        context.waitTicks(2);
        context.runOnClient(client -> sendKey(client, key, com.mojang.blaze3d.platform.InputConstants.RELEASE));
        context.waitFor(client -> ClientUiAccess.screen(client) == null, 30);
        System.out.println("CSB_INTERACTIVE_MAP_HOTKEY_OK");
    }

    private static void verifyAutoRoutesTrigger(ClientGameTestContext context) {
        context.runOnClient(client -> {
            try {
                String tiles = "quoi.api.skyblock.dungeon.odonscanning.tiles.";
                var roomData = Class.forName(tiles + "RoomData");
                var roomType = Class.forName(tiles + "RoomType");
                var roomState = Class.forName(tiles + "RoomState");
                var rotation = Class.forName(tiles + "Rotations");
                var roomClass = Class.forName(tiles + "OdonRoom");
                String fixture = "Auto Routes test room";
                var data = roomData.getConstructor(String.class, roomType, List.class, int.class, int.class, int.class,
                    roomState, String.class).newInstance(fixture, roomType.getField("NORMAL").get(null),
                    List.of(1), 0, 0, 0, roomState.getField("DISCOVERED").get(null), "1x1");
                var room = roomClass.getConstructor(rotation, roomData, net.minecraft.core.BlockPos.class, java.util.Set.class)
                    .newInstance(rotation.getField("NORTH").get(null), data, net.minecraft.core.BlockPos.ZERO, new java.util.HashSet<>());
                roomClass.getMethod("setHighestBlock", Integer.class).invoke(room, 70);

                var routeType = Class.forName("quoi.module.impl.dungeon.autoclear.impl.AutoRoutes");
                var routes = routeType.getField("INSTANCE").get(null);
                var routeNodes = routeType.getMethod("getRouteNodes").invoke(routes);
                var nodeType = Class.forName("quoi.api.autoroutes.nodes.RotateNode");
                var node = nodeType.getConstructor().newInstance();
                var position = client.player.position();
                float yaw = client.player.getYRot();
                float pitch = client.player.getXRot();
                nodeType.getMethod("setYaw", float.class).invoke(node, 73f);
                nodeType.getMethod("setRelative", net.minecraft.world.phys.Vec3.class).invoke(node, position);
                routeNodes.getClass().getMethod("put", Object.class, Object.class)
                    .invoke(routeNodes, fixture, new java.util.ArrayList<>(List.of(node)));
                boolean wasEnabled = (boolean) routeType.getMethod("getEnabled").invoke(routes);
                if (!wasEnabled) routeType.getMethod("toggle").invoke(routes);
                var roomEnter = Class.forName("quoi.api.events.DungeonEvent$Room$Enter");
                try {
                    roomEnter.getMethod("post").invoke(roomEnter.getConstructor(roomClass).newInstance(room));
                    check(position.equals(nodeType.getMethod("getPos").invoke(node)), "Auto Routes did not cache the room's route");
                    var tick = Class.forName("quoi.api.events.TickEvent$Start");
                    tick.getMethod("post").invoke(tick.getConstructor().newInstance());
                    check((boolean) nodeType.getMethod("getTriggered").invoke(node) && Math.abs(client.player.getYRot() - 73) < .01,
                        "Auto Routes did not execute its rotation node");
                } finally {
                    roomEnter.getMethod("post").invoke(roomEnter.getConstructor(roomClass).newInstance(new Object[]{null}));
                    if (!wasEnabled) routeType.getMethod("toggle").invoke(routes);
                    routeNodes.getClass().getMethod("remove", Object.class).invoke(routeNodes, fixture);
                    client.player.setYRot(yaw);
                    client.player.setXRot(pitch);
                    var location = Class.forName("quoi.api.skyblock.location.Location");
                    var area = location.getDeclaredField("currentArea");
                    area.setAccessible(true);
                    area.set(null, Class.forName("quoi.api.skyblock.location.Island").getField("SinglePlayer").get(null));
                }
            } catch (ReflectiveOperationException error) {
                throw new AssertionError("Could not set up the Auto Routes room", error);
            }
        });
        System.out.println("CSB_AUTOROUTES_ROOM_TRIGGER_OK");
    }

    private static void verifyQuoiModules() {
        try {
            var klass = Class.forName("quoi.module.ModuleManager");
            var modules = (java.util.List<?>) klass.getMethod("getModules").invoke(klass.getField("INSTANCE").get(null));
            var moduleClass = Class.forName("quoi.module.Module");
            var label = moduleClass.getMethod("getName");
            var settings = moduleClass.getMethod("getSettings");
            var names = new java.util.HashSet<String>();
            for (var module : modules) {
                String name = (String) label.invoke(module);
                names.add(name);
                check(!((java.util.List<?>) settings.invoke(module)).isEmpty(), "Missing upstream key/settings for " + name);
            }
            check(names.size() == 21 && names.containsAll(java.util.List.of(
                "Auto Routes", "Terminals", "Barrier Boom", "Puzzle Solvers", "Auto Croesus", "Lights Device")),
                "Dungeon manifest mismatch " + names);
        } catch (ReflectiveOperationException error) {
            throw new AssertionError("Dungeon source module did not load", error);
        }
    }

    private static void verifyRouteImport() {
        try {
            var importer = Class.forName("quoi.api.autoroutes.converters.RouteImport");
            var instance = importer.getField("INSTANCE").get(null);
            var read = importer.getMethod("read", File.class);
            var config = Class.forName("quoi.config.ConfigSystem");
            var gson = (Gson) config.getMethod("getGson").invoke(config.getField("INSTANCE").get(null));
            Path input = Path.of("rsa-route-fixture.json");
            Files.writeString(input, """
                {"Empty":[],"Fixture":[
                  {"type":"etherwarp","localPos":{"x":-5,"y":69,"z":2},"localTarget":{"x":-11,"y":70,"z":1},
                   "radius":0.5,"start":true,"awaits":{"awaitSecrets":2,"awaitEWRaytrace":true}},
                  {"type":"aotv","localPos":{"x":-2,"y":69,"z":5},"rotationVec":{"x":1,"y":0,"z":0},
                   "radius":0.75,"start":false}
                ]}
                """);
            var routes = (Map<?, ?>) read.invoke(instance, input.toFile());
            String nativeJson = gson.toJson(routes);
            var nodes = JsonParser.parseString(nativeJson).getAsJsonObject().getAsJsonArray("Fixture");
            var ether = nodes.get(0).getAsJsonObject();
            var aotv = nodes.get(1).getAsJsonObject();
            check(ether.getAsJsonObject("relative").get("x").getAsDouble() == 10
                && ether.getAsJsonObject("target").get("x").getAsDouble() == 4, "RSA coordinate conversion was incorrect");
            var waits = ether.getAsJsonArray("awaits");
            check(waits.size() == 2 && waits.get(0).getAsJsonObject().get("amount").getAsInt() == 2
                && waits.get(1).getAsJsonObject().get("type").getAsString().equals("raycast"), "An RSA wait was lost");
            check(aotv.get("type").getAsString().equals("use_item") && aotv.get("unsneak").getAsBoolean()
                && aotv.get("item").getAsString().equals("ASPECT_OF_THE_VOID") && aotv.get("yaw").getAsFloat() == -90f,
                "Ordinary AOTV retained the wrong item or posture");
            Path nativeFile = Path.of("native-route-fixture.json");
            Files.writeString(nativeFile, nativeJson);
            var reloaded = (Map<?, ?>) read.invoke(instance, nativeFile.toFile());
            check(gson.toJson(reloaded).equals(nativeJson), "Imported routes did not round-trip in Quoi's format");
            Path supplied = Path.of("rsa-import-probe.json");
            if (Files.isRegularFile(supplied)) {
                var imported = (Map<?, ?>) read.invoke(instance, supplied.toFile());
                long count = imported.values().stream().mapToLong(nodesInRoom -> ((List<?>) nodesInRoom).size()).sum();
                check(imported.size() == 120 && count == 1851, "Some routes or nodes in the original pack were lost");
                Path converted = Path.of("routes-quoi.json");
                Files.writeString(converted, gson.toJson(imported));
                check(gson.toJson(read.invoke(instance, converted.toFile())).equals(gson.toJson(imported)),
                    "Converted route pack did not round-trip");
                System.out.println("CSB_RSA_IMPORT_OK rooms=" + imported.size() + " nodes=" + count);
            }
        } catch (ReflectiveOperationException | java.io.IOException error) {
            throw new AssertionError("Route import regression", error);
        }
    }

    private static long blurPasses() {
        try {
            return (long) Class.forName(IntegratedClientPorts.typeName("motionblur", "ShaderManager"))
                .getMethod("submittedPasses").invoke(null);
        } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
    }

    private static void verifyBobby(Minecraft client) {
        try {
            var type = Class.forName(IntegratedClientPorts.typeName("bobby", "ext.ClientChunkCacheExt"));
            var cache = client.level.getChunkSource();
            check(type.isInstance(cache) && type.getMethod("bobby_getFakeChunkManager").invoke(cache) != null,
                "Bobby did not attach its chunk manager to the test world");
            check(com.customcosmetics.features.gameplay.BobbyIntegration.read()[1] != 0, "Bobby multi-world should be on by default");
        } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
    }

    private static void verifyNetworkChart(ClientGameTestContext context) {
        context.runOnClient(client -> {
            var debug = client.getDebugOverlay();
            if (debug.showNetworkCharts()) debug.toggleNetworkCharts();
            debug.getPingLogger().reset();
        });
        context.waitTicks(55);
        context.runOnClient(client -> {
            var debug = client.getDebugOverlay();
            check(debug.getPingLogger().size() == 0, "Unexpected background ping sample with network charts closed");
            debug.toggleNetworkCharts();
        });
        context.waitFor(client -> client.getDebugOverlay().getPingLogger().size() > 0, 30);
        context.runOnClient(client -> {
            var debug = client.getDebugOverlay();
            var samples = debug.getPingLogger();
            for (int i = 0; i < samples.size(); i++) {
                long sample = samples.get(i);
                check(sample >= 0 && sample < 5000, "Invalid loopback ping sample: " + sample);
            }
            debug.toggleNetworkCharts();
            System.out.println("CSB_VANILLA_NETWORK_CHART_OK");
        });
    }

    private static void verifyMouseRefocus(Minecraft client) {
        var game = net.fabricmc.loader.api.FabricLoader.getInstance().getModContainer("minecraft").orElseThrow();
        if (!game.getMetadata().getVersion().getFriendlyString().startsWith("26.3")) return;
        try {
            var window = client.getWindow();
            var focus = window.getClass().getDeclaredMethod("onFocus", boolean.class);
            focus.setAccessible(true);
            var mouse = Class.forName("org.lwjgl.sdl.SDLMouse");
            var keyboard = Class.forName("org.lwjgl.sdl.SDLKeyboard");
            var keyboardFocus = keyboard.getMethod("SDL_GetKeyboardFocus");
            check(java.util.Arrays.stream(window.getClass().getDeclaredMethods())
                .anyMatch(method -> method.getName().contains("csb$restoreRelativeMouseMode")), "SDL focus repair mixin was not applied");
            var set = mouse.getMethod("SDL_SetWindowRelativeMouseMode", long.class, boolean.class);
            var captured = mouse.getMethod("SDL_GetWindowRelativeMouseMode", long.class);
            ClientUiAccess.setScreen(client, new CosmeticsScreen(null, CosmeticsScreen.Page.MAIN));
            focus.invoke(window, true);
            check(!(boolean) captured.invoke(null, window.handle()), "Focus captured the pointer inside a menu");
            ClientUiAccess.setScreen(client, null);
            if ((long) keyboardFocus.invoke(null) != window.handle()) {
                System.out.println("CSB_SDL_MOUSE_REFOCUS_NATIVE_SKIPPED: test window has no OS focus");
                return;
            }
            client.mouseHandler.grabMouse();
            check(client.mouseHandler.isMouseGrabbed(), "Test could not grab the game cursor");
            focus.invoke(window, false);
            check((boolean) set.invoke(null, window.handle(), false), "Could not simulate a lost native capture");
            focus.invoke(window, true);
            check((boolean) captured.invoke(null, window.handle()), "Focus did not restore native cursor capture: "
                + Class.forName("org.lwjgl.sdl.SDLError").getMethod("SDL_GetError").invoke(null));
            System.out.println("CSB_SDL_MOUSE_REFOCUS_OK");
        } catch (ReflectiveOperationException error) {
            throw new AssertionError("Could not verify SDL refocus", error);
        }
    }

    private static void verifyModuleNotification(Minecraft client) {
        try {
            var history = net.minecraft.client.gui.components.ChatComponent.class.getDeclaredField("allMessages");
            history.setAccessible(true);
            var messages = (List<?>) history.get(ClientUiAccess.chat(client));
            check(messages.stream().map(Object::toString).anyMatch(message -> message.contains("Auto Harp") && message.contains("Module disabled")),
                "Module toggle did not add a chat message");
        } catch (ReflectiveOperationException error) {
            throw new AssertionError("Could not verify module notification", error);
        }
    }

    private static void verifyLegacyHeldSkull(Minecraft client) {
        var skull = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.PLAYER_HEAD);
        var firstPerson = net.minecraft.world.item.ItemDisplayContext.FIRST_PERSON_RIGHT_HAND;
        var leftHand = net.minecraft.world.item.ItemDisplayContext.FIRST_PERSON_LEFT_HAND;
        var gui = net.minecraft.world.item.ItemDisplayContext.GUI;
        var vanillaRight = submitHead(client, skull, firstPerson);
        var vanillaLeft = submitHead(client, skull, leftHand);
        var vanillaGui = submitHead(client, skull, gui);
        ConfigManager.legacyHeldSkull = true;
        try {
            var styledRight = submitHead(client, skull, firstPerson);
            var styledLeft = submitHead(client, skull, leftHand);
            check(!styledRight.equals(vanillaRight, 0.001f) && !styledLeft.equals(vanillaLeft, 0.001f),
                "Legacy head transforms did not run for both hands");
            check(submitHead(client, skull, gui).equals(vanillaGui, 0.001f), "Legacy head transform changed GUI items");
        } finally {
            ConfigManager.legacyHeldSkull = false;
        }
    }

    private static org.joml.Matrix4f submitHead(Minecraft client, net.minecraft.world.item.ItemStack head,
                                                net.minecraft.world.item.ItemDisplayContext display) {
        var state = new net.minecraft.client.renderer.item.ItemStackRenderState();
        client.getItemModelResolver().updateForTopItem(state, head, display, client.level, client.player, 0);
        var matrix = new org.joml.Matrix4f[1];
        var collector = (net.minecraft.client.renderer.SubmitNodeCollector) java.lang.reflect.Proxy.newProxyInstance(
            CustomSkyBlockGameTest.class.getClassLoader(), new Class<?>[] {net.minecraft.client.renderer.SubmitNodeCollector.class},
            (proxy, method, args) -> {
                if (method.getName().equals("order")) return proxy;
                if (args != null) for (Object arg : args) if (arg instanceof com.mojang.blaze3d.vertex.PoseStack pose) {
                    matrix[0] = new org.joml.Matrix4f(pose.last().pose());
                    break;
                }
                return null;
            });
        var poses = new com.mojang.blaze3d.vertex.PoseStack();
        state.submit(poses, collector, 15728880, 0, 0);
        check(matrix[0] != null && poses.last().pose().equals(new org.joml.Matrix4f(), 0.001f), "Head submission did not restore the pose");
        return matrix[0];
    }

    private static void verifyCrystalHollowsScanner(Minecraft client) {
        try {
            var scanner = Class.forName("com.customcosmetics.features.gameplay.CrystalHollowsScanner");
            var start = scanner.getDeclaredMethod("startChunk", net.minecraft.world.level.chunk.LevelChunk.class);
            var advance = scanner.getDeclaredMethod("advanceChunk", Minecraft.class);
            var reset = scanner.getDeclaredMethod("reset");
            var current = scanner.getDeclaredField("scanning");
            var markers = scanner.getDeclaredField("markers");
            var grottos = scanner.getDeclaredField("grottos");
            for (var member : java.util.List.of(start, advance, reset, current, markers, grottos)) member.setAccessible(true);
            var terrain = new net.minecraft.world.level.chunk.LevelChunk(client.level, new net.minecraft.world.level.ChunkPos(16, 20));
            var stone = net.minecraft.world.level.block.Blocks.STONE.defaultBlockState();
            for (int y = 0; y < 176; y++) for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++)
                terrain.getSections()[terrain.getSectionIndex(y)].setBlockState(x, y & 15, z, stone);
            start.invoke(null, terrain);
            int steps = 0;
            while (current.get(null) != null && steps < 500) {
                advance.invoke(null, client);
                steps++;
            }
            check(steps < 20 && ((java.util.List<?>) markers.get(null)).isEmpty(), "Scanner read ordinary stone sections");

            // Temple's distinctive block is in the section above its base. Jasper has two accepted block types.
            var ids = new String[] {"bedrock", "bedrock", "bedrock", "bedrock", "stone", "clay", "clay", "clay",
                "oak_leaves", "oak_leaves", "lime_terracotta", "lime_terracotta", "green_terracotta"};
            for (int i = 0; i < ids.length; i++) {
                int y = 30 + i;
                var block = net.minecraft.core.registries.BuiltInRegistries.BLOCK
                    .getOptional(net.minecraft.resources.Identifier.withDefaultNamespace(ids[i])).orElseThrow();
                terrain.getSections()[terrain.getSectionIndex(y)].setBlockState(6, y & 15, 6, block.defaultBlockState());
            }
            for (int i = 0; i < 2; i++) {
                var id = net.minecraft.resources.Identifier.withDefaultNamespace(i == 0 ? "magenta_stained_glass" : "magenta_stained_glass_pane");
                var block = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getOptional(id).orElseThrow();
                terrain.getSections()[terrain.getSectionIndex(60)].setBlockState(8 + i, 12, 8, block.defaultBlockState());
            }
            start.invoke(null, terrain);
            steps = 0;
            while (current.get(null) != null && steps++ < 500) advance.invoke(null, client);
            var found = (java.util.List<?>) markers.get(null);
            var jasper = (java.util.List<?>) grottos.get(null);
            check(found.size() == 1 && found.getFirst().toString().contains("Temple"), "Scanner missed temple across sections");
            check(jasper.size() == 1, "Scanner missed Fairy Grotto");
            var count = jasper.getFirst().getClass().getDeclaredMethod("blockCount");
            count.setAccessible(true);
            check(((net.minecraft.network.chat.Component) count.invoke(jasper.getFirst())).getString().equals("2"),
                "Scanner missed a Jasper block type");
            reset.invoke(null);
            System.out.println("CSB_CRYSTAL_HOLLOWS_PALETTE_AND_MATCHES_OK");
        } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
    }

    private static void sendKey(Minecraft client, int key, int action) {
        try {
            var method = client.keyboardHandler.getClass().getDeclaredMethod("keyPress", long.class, int.class,
                net.minecraft.client.input.KeyEvent.class);
            method.setAccessible(true);
            method.invoke(client.keyboardHandler, client.getWindow().handle(), action,
                new net.minecraft.client.input.KeyEvent(key, 0, 0));
        } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
    }

    private static void verifyTooltipContent() {
        var armor = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.LEATHER_CHESTPLATE);
        var attributes = new net.minecraft.nbt.CompoundTag();
        attributes.putLong("timestamp", 1_700_000_000_000L);
        armor.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.of(attributes));
        armor.set(net.minecraft.core.component.DataComponents.DYED_COLOR, new net.minecraft.world.item.component.DyedItemColor(0x064DC3));
        var lines = new java.util.ArrayList<net.minecraft.network.chat.Component>();
        lines.add(net.minecraft.network.chat.Component.literal("Test armor"));
        lines.add(net.minecraft.network.chat.Component.literal("Gear Score: 10"));
        lines.add(net.minecraft.network.chat.Component.literal("Aqua Affinity I").withStyle(net.minecraft.ChatFormatting.GOLD));
        ConfigManager.tooltipHideGearScore = ConfigManager.tooltipShowAge = ConfigManager.tooltipShowHex = true;
        try {
            var method = com.customcosmetics.features.gameplay.TooltipTweaks.class.getDeclaredMethod("updateTooltip",
                net.minecraft.world.item.ItemStack.class, java.util.List.class, long.class, boolean.class);
            method.setAccessible(true);
            method.invoke(null, armor, lines, 1_700_086_400_000L, true);
            var actual = lines.stream().map(net.minecraft.network.chat.Component::getString).toList();
            check(actual.size() == 4 && actual.get(1).contains("#064DC3") && actual.get(2).startsWith("Age: 1d")
                && actual.get(3).equals("Aqua Affinity I"), "Tooltip Tweaks did not apply the expected changes: " + actual);
        } catch (ReflectiveOperationException error) {
            throw new AssertionError(error);
        } finally {
            ConfigManager.tooltipHideGearScore = ConfigManager.tooltipShowAge = ConfigManager.tooltipShowHex = false;
        }
        System.out.println("CSB300_TOOLTIP_CONTENT_OK");
    }

    private static void verifyDuplicateEnchantTooltip(Minecraft client) {
        var boots = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND_BOOTS);
        var enchantments = client.level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT);
        boots.enchant(enchantments.getOrThrow(net.minecraft.world.item.enchantment.Enchantments.DEPTH_STRIDER), 3);
        boots.enchant(enchantments.getOrThrow(net.minecraft.world.item.enchantment.Enchantments.PROTECTION), 4);
        var attributes = new net.minecraft.nbt.CompoundTag();
        attributes.putString("id", "MAXOR_BOOTS");
        boots.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.of(attributes));
        var lore = net.minecraft.network.chat.Component.literal("Legion V, ")
            .append(net.minecraft.network.chat.Component.literal("Depth Strider III").withColor(0xAA55FF))
            .append(", Feather Falling X");
        boots.set(net.minecraft.core.component.DataComponents.LORE, new net.minecraft.world.item.component.ItemLore(List.of(lore)));
        var tooltipContext = net.minecraft.world.item.Item.TooltipContext.of(client.level);
        var tooltip = (java.util.function.Supplier<List<String>>) () -> boots
            .getTooltipLines(tooltipContext, client.player, net.minecraft.world.item.TooltipFlag.NORMAL)
            .stream().map(net.minecraft.network.chat.Component::getString).toList();
        check(tooltip.get().contains("Depth Strider III"), "Vanilla worlds lost a native enchantment");
        try {
            var isLocal = Minecraft.class.getDeclaredField("isLocalServer");
            var presence = com.customcosmetics.features.animations.PresenceWorld.class;
            var hypixel = presence.getDeclaredField("hypixel");
            var gameType = presence.getDeclaredField("gameType");
            for (var field : List.of(isLocal, hypixel, gameType)) field.setAccessible(true);
            var oldLocal = isLocal.getBoolean(client);
            var oldHypixel = hypixel.getBoolean(null);
            var oldGame = gameType.get(null);
            var oldHide = ConfigManager.tooltipHideVanillaEnchants;
            try {
                isLocal.setBoolean(client, false);
                hypixel.setBoolean(null, true);
                gameType.set(null, "SKYBLOCK");
                check(com.customcosmetics.features.animations.PresenceWorld.isHypixel(client)
                    && com.customcosmetics.features.animations.PresenceWorld.isSkyblock(client),
                    "Could not set up SkyBlock tooltip context");
                ConfigManager.tooltipHideVanillaEnchants = false;
                var deduped = tooltip.get();
                check(!deduped.contains("Depth Strider III") && deduped.contains(lore.getString())
                    && deduped.contains("Protection IV"), "Wrong enchantment was removed: " + deduped);
                var lower = net.minecraft.network.chat.Component.literal("Depth Strider II").withColor(0xAA55FF);
                boots.set(net.minecraft.core.component.DataComponents.LORE,
                    new net.minecraft.world.item.component.ItemLore(List.of(lower)));
                check(tooltip.get().contains("Depth Strider III"), "A different level suppressed the native enchantment");
                ConfigManager.tooltipHideVanillaEnchants = true;
                var hidden = tooltip.get();
                check(!hidden.contains("Depth Strider III") && hidden.contains("Depth Strider II")
                    && hidden.contains("Protection IV"), "Hide option removed the wrong tooltip line: " + hidden);
            } finally {
                ConfigManager.tooltipHideVanillaEnchants = oldHide;
                gameType.set(null, oldGame);
                hypixel.setBoolean(null, oldHypixel);
                isLocal.setBoolean(client, oldLocal);
            }
        } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
        System.out.println("CSB_TOOLTIP_NATIVE_ENCHANT_DEDUP_OK");
    }

    private static void verifyDyeAndZoom(ClientGameTestContext context) {
        context.runOnClient(client -> {
            var dye = com.customcosmetics.features.gameplay.SeymourColors.match(0x064DC3, true);
            check(dye != null && dye.delta() == 0 && dye.name().startsWith("Ocean"), "Ocean dye was not matched");
            ConfigManager.zoomEnabled = true;
            ConfigManager.zoomAnimate = false;
        });
        var key = zoomKey();
        context.runOnClient(client -> key.setDown(true));
        try {
            context.waitTicks(3);
            context.runOnClient(client -> {
                check(CameraZoom.sensitivity() < .5, "Zoom key did not update the camera");
                check(CameraZoom.scroll(client, 1), "Zoom did not consume the scroll input");
                ConfigManager.zoomScrollBelowInitial = false;
                CameraZoom.scroll(client, -100);
                CameraZoom.fov(70);
                check(CameraZoom.sensitivity() == .25, "Clamped scroll went below the initial zoom");
                ConfigManager.zoomScrollBelowInitial = true;
                CameraZoom.scroll(client, -100);
                check(CameraZoom.fov(70) == 70, "Scroll did not return to normal FOV");
            });
        } finally { context.runOnClient(client -> key.setDown(false)); }
        context.waitTicks(3);
        context.runOnClient(client -> {
            check(CameraZoom.sensitivity() == 1, "Zoom did not release");
            ConfigManager.zoomInSeconds = 1;
            ConfigManager.zoomOutSeconds = 0.5;
            ConfigManager.zoomAnimate = true;
            key.setDown(true);
            check(CameraZoom.fov(70) == 70, "Zoom-in did not start at the current field of view");
        });
        context.waitFor(client -> CameraZoom.sensitivity() < .99 && CameraZoom.sensitivity() > .26, 40);
        context.waitFor(client -> CameraZoom.sensitivity() == .25, 60);
        context.runOnClient(client -> {
            key.setDown(false);
            CameraZoom.fov(70);
            check(CameraZoom.sensitivity() == .25, "Zoom-out did not start at the current field of view");
        });
        context.waitFor(client -> CameraZoom.sensitivity() > .26 && CameraZoom.sensitivity() < .99, 30);
        context.waitFor(client -> CameraZoom.sensitivity() == 1, 40);
        context.runOnClient(client -> {
            ConfigManager.zoomEnabled = false;
        });
        System.out.println("CSB300_SEYMOUR_ZOOM_OK");
    }

    private static net.minecraft.client.KeyMapping zoomKey() {
        try {
            var field = com.customcosmetics.features.camera.CameraZoom.class.getDeclaredField("key");
            field.setAccessible(true);
            return (net.minecraft.client.KeyMapping) field.get(null);
        } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
    }

    private static void screenshot(ClientGameTestContext context, String name) {
        try { context.takeScreenshot(name); }
        catch (Exception failure) { throw new AssertionError(failure); }
    }

    private static AbstractWidget widget(Minecraft client, String prefix, boolean sidebar) {
        return ClientUiAccess.screen(client).children().stream().filter(AbstractWidget.class::isInstance)
            .map(AbstractWidget.class::cast).filter(widget -> widget.getMessage().getString().startsWith(prefix))
            .filter(widget -> !sidebar || widget.getX() < client.getWindow().getGuiScaledWidth() / 3)
            .findFirst().orElseThrow(() -> new AssertionError("Missing " + prefix));
    }

    private static void click(Minecraft client, String prefix, boolean sidebar) {
        click(client, prefix, sidebar, ClientInput.MOUSE_LEFT);
    }

    private static void click(Minecraft client, String prefix, boolean sidebar, int button) {
        var widget = widget(client, prefix, sidebar);
        if (ClientUiAccess.screen(client) instanceof CosmeticsScreen screen && (sidebar || !widget.visible)) {
            try {
                var navTop = CosmeticsScreen.class.getDeclaredField("navTop");
                var navBottom = CosmeticsScreen.class.getDeclaredField("navBottom");
                navTop.setAccessible(true);
                navBottom.setAccessible(true);
                int top = navTop.getInt(screen);
                int bottom = navBottom.getInt(screen);
                int offset = widget.getY() < top ? widget.getY() - top : Math.max(0, widget.getBottom() - bottom);
                if (offset != 0) screen.mouseScrolled(widget.getX() + 5, top + 3, 0, -offset / 36.0);
            } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
        }
        var event = new MouseButtonEvent(widget.getX() + widget.getWidth() / 2.0, widget.getY() + widget.getHeight() / 2.0,
            new MouseButtonInfo(button, 0));
        check(ClientUiAccess.screen(client).mouseClicked(event, false), "Could not click " + prefix);
    }

    private static void check(boolean value, String reason) {
        if (!value) throw new AssertionError(reason);
    }
}
