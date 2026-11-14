// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.gametest;

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
            check(!widget(client, "Kuudra", false).active, "Kuudra sidebar button is clickable");
            for (var module : GameplayModule.values()) {
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
            leap = (PinkUi.Field) widget(client, "Search modules", false);
            leap.setValue("Auto Leap");
            click(client, "Auto Leap", false, ClientInput.MOUSE_RIGHT);
            check(widget(client, "Target by Player Name", false) instanceof PinkUi.Action
                && widget(client, "Door Opener Leap", false) instanceof PinkUi.Action, "Auto Leap settings are incomplete");
            ((PinkUi.Field) widget(client, "Search modules", false)).setValue("Secret Aura");
            click(client, "Secret Aura", false, ClientInput.MOUSE_RIGHT);
            check(widget(client, "Skull Range:", false) instanceof PinkUi.Slider
                && widget(client, "In Container", false) instanceof PinkUi.Action, "Secret Aura controls are missing");
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
            ((PinkUi.Field) widget(client, "Search modules", false)).setValue("Block Perks");
            click(client, "Block Perks", false, ClientInput.MOUSE_RIGHT);
            check(widget(client, "Override Key", false) instanceof PinkUi.Action
                && widget(client, "Human Cannonball", false) instanceof PinkUi.Action, "Kuudra Block Perks controls are missing");
            ((PinkUi.Field) widget(client, "Search modules", false)).setValue("Open Terminals");
            click(client, "Open Terminals", false, ClientInput.MOUSE_RIGHT);
            check(widget(client, "Aura FOV:", false) instanceof PinkUi.Slider
                && widget(client, "Delay After Leap", false) instanceof PinkUi.Action, "Open Terminals controls are incomplete");
            int terminalModeY = widget(client, "Mode:", false).getY();
            int delayY = widget(client, "Delay After Leap", false).getY();
            for (int i = 0; i < 3; i++) {
                click(client, "Mode:", false);
                click(client, "Mode:", false);
                check(widget(client, "Mode:", false).getY() == terminalModeY
                    && widget(client, "Delay After Leap", false).getY() == delayY,
                    "Rebuilding Open Terminals settings changed their layout");
            }
            ((PinkUi.Field) widget(client, "Search modules", false)).setValue("Kuudra Queuer");
            click(client, "Kuudra Queuer", false, ClientInput.MOUSE_RIGHT);
            check(widget(client, "Requeue Delay (ticks):", false) instanceof PinkUi.Slider, "Requeue delay is missing");
            ((PinkUi.Field) widget(client, "Search modules", false)).setValue("Cocoon Alert");
            click(client, "Cocoon Alert", false, ClientInput.MOUSE_RIGHT);
            check(widget(client, "Cocoon Timer", false) instanceof PinkUi.Action, "Cocoon timer setting is missing");
            leap = (PinkUi.Field) widget(client, "Search modules", false);
            leap.setValue("Fast Leap");
            click(client, "Fast Leap", false, ClientInput.MOUSE_RIGHT);
            check(widget(client, "Leap Target", false) instanceof PinkUi.Field && widget(client, "Yellow Pad Leap", false) instanceof PinkUi.Action,
                "Fast Leap settings are incomplete");
            click(client, "Keybind", false);
        });
        context.getInput().pressKey(ClientInput.C);
        context.waitTicks(2);
        context.runOnClient(client -> check(GameplayKeybinds.key(GameplayModule.FAST_LEAP).getValue() == ClientInput.C,
            "Module keybind did not record"));
        screenshot(context, "csb-300-module-search");
        context.runOnClient(client -> {
            ClientUiAccess.screen(client).onClose();
            click(client, "Cheating", false);
            check(widget(client, "Auto Harp", false).visible, "Cheating confirmation was shown a second time");
            click(client, "HUD Editor", false);
            var editor = ClientUiAccess.screen(client);
            check(editor instanceof com.customcosmetics.gui.GameplayHudEditor, "HUD editor did not open");
            var previous = ConfigManager.gameplayHudScales;
            ConfigManager.gameplayHudScales = new float[0];
            int x = com.customcosmetics.features.gameplay.GameplayFeatures.hudX(0, editor.width) + 1;
            int y = com.customcosmetics.features.gameplay.GameplayFeatures.hudY(0, editor.height) + 1;
            check(editor.mouseScrolled(x, y, 0, 1), "HUD did not capture the mouse wheel");
            check(ConfigManager.gameplayHudScales[0] > 1 && ConfigManager.gameplayHudScales[1] == 0,
                "Mouse wheel scaled the wrong HUD");
            click(client, "Reset", false);
            check(ConfigManager.gameplayHudScales.length == 0, "Reset did not restore HUD scale");
            ConfigManager.gameplayHudScales = previous;
            ClientUiAccess.setScreen(client, null);
        });
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
                check(com.customcosmetics.features.gameplay.InventoryAutomation.countEnderPearls(java.util.List.of(
                    new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.ENDER_PEARL, 7),
                    new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.ENDER_PEARL, 9))) == 16,
                    "Auto GFS missed pearls without a SkyBlock ID");
                var key = GameplayKeybinds.key(GameplayModule.FAST_LEAP);
                pressKey(client, key.getValue());
                check(GameplayModule.FAST_LEAP.enabled(), "Recorded module binding did not toggle");
                pressKey(client, key.getValue());
                check(!GameplayModule.FAST_LEAP.enabled(), "Recorded module binding did not toggle off");
                GameplayKeybinds.bind(GameplayModule.FAST_LEAP, com.mojang.blaze3d.platform.InputConstants.UNKNOWN);
            });
            verifyDyeAndZoom(context);
            context.runOnClient(client -> {
                verifyTooltipContent();
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

    private static void pressKey(Minecraft client, int key) {
        try {
            var method = client.keyboardHandler.getClass().getDeclaredMethod("keyPress", long.class, int.class,
                net.minecraft.client.input.KeyEvent.class);
            method.setAccessible(true);
            method.invoke(client.keyboardHandler, client.getWindow().handle(), com.mojang.blaze3d.platform.InputConstants.PRESS,
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
        lines.add(net.minecraft.network.chat.Component.literal("Depth Strider III").withStyle(net.minecraft.ChatFormatting.GRAY));
        lines.add(net.minecraft.network.chat.Component.literal("Aqua Affinity I").withStyle(net.minecraft.ChatFormatting.GOLD));
        ConfigManager.tooltipHideGearScore = ConfigManager.tooltipHideVanillaEnchants = ConfigManager.tooltipShowAge = ConfigManager.tooltipShowHex = true;
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
            ConfigManager.tooltipHideGearScore = ConfigManager.tooltipHideVanillaEnchants = ConfigManager.tooltipShowAge = ConfigManager.tooltipShowHex = false;
        }
        System.out.println("CSB300_TOOLTIP_CONTENT_OK");
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
