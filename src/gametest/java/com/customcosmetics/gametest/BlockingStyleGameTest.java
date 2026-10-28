// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.gametest;

import com.customcosmetics.compatibility.ClientInput;
import com.customcosmetics.compatibility.ClientUiAccess;
import com.customcosmetics.config.ConfigManager;
import com.customcosmetics.config.CosmeticsConfigDir;
import com.customcosmetics.features.blocking.BlockingStyle;
import com.customcosmetics.gui.CosmeticsScreen;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.components.AbstractWidget;
import java.util.concurrent.atomic.AtomicReference;

/** Real dropdown input and first-person screenshots; phase sampling affects only the test renderer. */
final class BlockingStyleGameTest {
    static void check(ClientGameTestContext context, TestSingleplayerContext world) throws Exception {
        context.runOnClient(client -> {
            ConfigManager.resetAnimations(); ConfigManager.swordBlockingStyle = "default";
            ConfigManager.swordBlockingListMode = "off"; ConfigManager.swordBlockingEnabled = true;
            client.options.guiScale().set(2);
            ClientUiAccess.setScreen(client, new CosmeticsScreen(null, CosmeticsScreen.Page.ANIMATIONS));
        });
        context.getInput().resizeWindow(1600,1000); context.waitTicks(3);
        clickStyle(context); context.takeScreenshot("blocking-style-dropdown-default-jigsaw");
        context.getInput().pressKey(ClientInput.DOWN); context.getInput().pressKey(ClientInput.ENTER); context.waitTicks(2);
        context.runOnClient(client -> {
            if (!ConfigManager.swordBlockingStyle.equals("jigsaw")) throw new AssertionError("The dropdown did not select Jigsaw");
            ConfigManager.saveConfig();
        });
        // Wait on the test thread; file completion must not block the client thread.
        ConfigManager.awaitPendingSaves();
        var settings = com.google.gson.JsonParser.parseString(java.nio.file.Files.readString(
            CosmeticsConfigDir.getCosmeticsConfigDir().resolve("config.json"))).getAsJsonObject().getAsJsonObject("settings");
        if (!settings.get("swordBlockingStyle").getAsString().equals("jigsaw")) throw new AssertionError("Style was not saved locally");
        for(String field:java.util.List.of("swordBlockingListMode","swordBlockingBlacklist","swordBlockingWhitelist","swordBlockingListsApplyToOthers"))if(settings.has(field))throw new AssertionError("List preference remained in config.json: "+field);
        var lists=com.google.gson.JsonParser.parseString(java.nio.file.Files.readString(CosmeticsConfigDir.getCosmeticsConfigDir().resolve("sword-blocking-lists.json"))).getAsJsonObject();
        if(lists.get("version").getAsInt()!=1||!lists.has("mode")||!lists.has("applyToOthers")||!lists.get("blacklist").isJsonArray()||!lists.get("whitelist").isJsonArray())throw new AssertionError("Independent sword blocking lists were not saved");
        System.out.println("SWORD_BLOCKING_LISTS_SEPARATE_JSON_NATIVE_SAVE_OK");
        context.getInput().pressKey(ClientInput.ESCAPE);
        context.runOnClient(client -> ClientUiAccess.setScreen(client, new CosmeticsScreen(null, CosmeticsScreen.Page.ANIMATIONS)));
        context.waitTicks(2);
        context.runOnClient(client -> {
            if (!styleControl(client).getMessage().getString().contains("Smooth")) throw new AssertionError("Reopening lost the selected style");
        });
        clickStyle(context); context.getInput().pressKey(ClientInput.UP); context.getInput().pressKey(ClientInput.ENTER);
        context.runOnClient(client -> {
            if (!ConfigManager.swordBlockingStyle.equals("default")) throw new AssertionError("The dropdown could not restore Default");
        });
        context.getInput().pressKey(ClientInput.ESCAPE);
        context.getInput().resizeWindow(854,480); context.waitTicks(3);
        world.getServer().runCommand("effect clear @a");
        world.getServer().runCommand("item replace entity @a weapon.mainhand with minecraft:iron_sword");
        world.getServer().runCommand("item replace entity @a weapon.offhand with minecraft:air");
        context.waitTicks(5);
        context.runOnClient(client -> {
            ClientUiAccess.chat(client).clearMessages(true);
            client.player.setYRot(0); client.player.setXRot(0);
        });
        context.getInput().holdKey(options -> options.keyUse); context.waitTicks(3);
        try {
            for (var style : BlockingStyle.values()) {
                context.runOnClient(client -> ConfigManager.swordBlockingStyle = style.value());
                int index = 0;
                for (float phase : new float[]{0, .08f, .17f, .25f, .4f, .6f, .8f, .95f, 1}) {
                    context.runOnClient(client -> { RenderAssertions.forcedBlockingSwing = phase; RenderAssertions.calls.set(0); });
                    context.waitTicks(2);
                    context.takeScreenshot("blocking-style-" + style.value() + "-right-" + String.format("%02d",index++));
                    context.runOnClient(client -> {
                        if (RenderAssertions.calls.get() == 0 || Math.abs(RenderAssertions.blockingSwing-phase)>.00001f
                                || !style.value().equals(RenderAssertions.renderedBlockingStyle))
                            throw new AssertionError("The selected first-person style did not reach the real renderer: " + style);
                    });
                }
            }
            context.runOnClient(client -> {
                ConfigManager.swordBlockingStyle = "jigsaw"; RenderAssertions.forcedBlockingSwing = .25f;
                client.player.setMainArm(net.minecraft.world.entity.HumanoidArm.LEFT);
            });
            context.waitTicks(3); context.takeScreenshot("blocking-style-jigsaw-left-flat-stroke");
            context.runOnClient(client -> client.player.setMainArm(net.minecraft.world.entity.HumanoidArm.RIGHT));
            context.getInput().releaseKey(options -> options.keyUse);
            world.getServer().runCommand("item replace entity @a weapon.mainhand with minecraft:air");
            world.getServer().runCommand("item replace entity @a weapon.offhand with minecraft:iron_sword");
            context.waitTicks(5); context.getInput().holdKey(options -> options.keyUse); context.waitTicks(3);
            context.takeScreenshot("blocking-style-jigsaw-offhand-flat-stroke");
            context.runOnClient(client -> {
                if (com.customcosmetics.features.blocking.SwordBlocking.localHand() != com.customcosmetics.features.blocking.BlockHand.OFF)
                    throw new AssertionError("Jigsaw did not work in the offhand");
            });
        } finally {
            context.getInput().releaseKey(options -> options.keyUse);
            context.runOnClient(client -> { RenderAssertions.forcedBlockingSwing = Float.NaN; ConfigManager.swordBlockingStyle = "default"; });
        }
        world.getServer().runCommand("item replace entity @a weapon.mainhand with minecraft:diamond_sword");
        world.getServer().runCommand("item replace entity @a weapon.offhand with minecraft:air");
        context.waitTicks(3);
        System.out.println("BLOCKING_STYLE_DROPDOWN_LOCAL_SAVE_AND_RENDER_OK");
    }
    private static AbstractWidget styleControl(net.minecraft.client.Minecraft client) {
        return ClientUiAccess.screen(client).children().stream().filter(AbstractWidget.class::isInstance).map(AbstractWidget.class::cast)
            .filter(widget -> widget.getMessage().getString().startsWith("Blocking Style")).findFirst().orElseThrow();
    }
    private static void clickStyle(ClientGameTestContext context) {
        var position = new AtomicReference<double[]>();
        context.runOnClient(client -> {
            var widget = styleControl(client); double scale = client.getWindow().getGuiScale();
            position.set(new double[]{(widget.getX()+widget.getWidth()/2.0)*scale,(widget.getY()+widget.getHeight()/2.0)*scale});
        });
        context.getInput().setCursorPos(position.get()[0],position.get()[1]); context.getInput().pressMouse(ClientInput.MOUSE_LEFT);
        context.waitTicks(2);
    }
}
