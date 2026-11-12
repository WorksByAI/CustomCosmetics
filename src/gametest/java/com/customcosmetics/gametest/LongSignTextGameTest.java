// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.gametest;

import com.customcosmetics.compatibility.ClientUiAccess;
import com.customcosmetics.config.ConfigManager;
import com.customcosmetics.sync.ExperimentalAccess;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.font.TextFieldHelper;
import net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import javax.imageio.ImageIO;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.List;

public final class LongSignTextGameTest implements FabricClientGameTest {
    private static final String LONG_LINE = "W宽".repeat(192);

    @Override public void runTest(ClientGameTestContext context) {
        var access = field(ExperimentalAccess.class, "status");
        context.runOnClient(client -> {
            ConfigManager.syncEnabled = false;
            ConfigManager.updateCheckEnabled = false;
            ConfigManager.checkExperimentalAccess = false;
            ConfigManager.offlineMode = false;
            ConfigManager.longSignTextEnabled = true;
            grant(access, ExperimentalAccess.Status.DENIED);
        });
        try (var world = context.worldBuilder().setUseConsistentSettings(true).create()) {
            var server = world.getServer();
            server.runCommand("time set noon");
            server.runCommand("fill -2 84 -2 3 84 3 minecraft:stone");
            server.runCommand("setblock 0 85 2 minecraft:oak_sign[rotation=8]");
            server.runCommand("setblock 1 87 2 minecraft:stone");
            server.runCommand("setblock 1 86 2 minecraft:oak_hanging_sign[rotation=8]");
            server.runCommand("tp @a 0.5 85 0.5 0 0");
            context.waitTicks(10);
            for (var pos : List.of(new BlockPos(0, 85, 2), new BlockPos(1, 86, 2))) {
                context.runOnClient(client -> client.gameMode.useItemOn(client.player, InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(pos), Direction.NORTH, pos, false)));
                context.waitFor(client -> ClientUiAccess.screen(client) instanceof AbstractSignEditScreen, 100);
                checkBackgroundDarkness(context, access, pos.getX() == 0 ? "standing" : "hanging");
                context.runOnClient(client -> {
                    var screen = (AbstractSignEditScreen) ClientUiAccess.screen(client);
                    var input = (TextFieldHelper) read(field(AbstractSignEditScreen.class, "signField"), screen);
                    var lines = (String[]) read(field(AbstractSignEditScreen.class, "messages"), screen);
                    grant(access, ExperimentalAccess.Status.DENIED);
                    input.insertText(LONG_LINE);
                    assertLine(lines, "");
                    grant(access, ExperimentalAccess.Status.GRANTED);
                    ConfigManager.longSignTextEnabled = false;
                    input.insertText(LONG_LINE);
                    assertLine(lines, "");
                    ConfigManager.longSignTextEnabled = true;
                    input.insertText(LONG_LINE + "x");
                    assertLine(lines, "");
                    input.insertText(LONG_LINE);
                    input.insertText("x");
                    assertLine(lines, LONG_LINE);
                    ClientUiAccess.setScreen(client, null);
                });
                context.waitTicks(6);
                server.runOnServer(instance -> {
                    var sign = (SignBlockEntity) instance.overworld().getBlockEntity(pos);
                    assertLine(new String[] {frontLine(frontText(sign)).getString()}, LONG_LINE);
                });
                context.runOnClient(client -> {
                    var sign = (SignBlockEntity) client.level.getBlockEntity(pos);
                    var text = frontText(sign);
                    var expanded = text.getRenderMessages(false, msg -> FormattedCharSequence.EMPTY);
                    if (client.font.width(expanded[0]) != client.font.width(LONG_LINE))
                        throw new AssertionError("Long sign text was truncated");
                    ConfigManager.longSignTextEnabled = false;
                    if (text.getRenderMessages(false, msg -> FormattedCharSequence.EMPTY)[0] != FormattedCharSequence.EMPTY)
                        throw new AssertionError("Turning off long sign text left the expanded render cache active");
                    ConfigManager.longSignTextEnabled = true;
                });
            }
            System.out.println("LONG_SIGN_TEXT_NORMAL_HANGING_AND_SERVER_ROUND_TRIP_OK");
        } finally {
            context.runOnClient(client -> {
                ConfigManager.longSignTextEnabled = false;
                grant(access, ExperimentalAccess.Status.UNCHECKED);
            });
        }
    }

    private static void checkBackgroundDarkness(ClientGameTestContext context, Field access, String sign) {
        context.runOnClient(client -> {
            grant(access, ExperimentalAccess.Status.GRANTED);
            ConfigManager.inventoryBlurEnabled = true;
            ConfigManager.inventoryDimPercent = 0;
        });
        context.waitTicks(3);
        int clear = backgroundBrightness(context.takeScreenshot(sign + "_sign_background_0"));
        context.runOnClient(client -> ConfigManager.inventoryDimPercent = 15);
        context.waitTicks(3);
        int light = backgroundBrightness(context.takeScreenshot(sign + "_sign_background_15"));
        context.runOnClient(client -> ConfigManager.inventoryDimPercent = 80);
        context.waitTicks(3);
        int normal = backgroundBrightness(context.takeScreenshot(sign + "_sign_background_80"));
        if (clear < 30 || clear <= light || light <= normal + 10) {
            throw new AssertionError("Sign background ignored darkness slider: " + clear + "/" + light + "/" + normal);
        }
        System.out.println("SIGN_BACKGROUND_DARKNESS_OK " + sign + " " + clear + "/" + light + "/" + normal);
    }

    private static int backgroundBrightness(Path screenshot) {
        try {
            var image = ImageIO.read(screenshot.toFile());
            int left = image.getWidth() / 8, top = image.getHeight() / 3;
            int sum = 0;
            for (int y = top; y < top + 40; y++) {
                for (int x = left; x < left + 40; x++) {
                    int rgb = image.getRGB(x, y);
                    sum += (rgb >>> 16 & 255) + (rgb >>> 8 & 255) + (rgb & 255);
                }
            }
            return sum / (40 * 40 * 3);
        } catch (IOException error) {
            throw new AssertionError("Could not inspect sign background", error);
        }
    }

    private static void assertLine(String[] lines, String expected) {
        if (!lines[0].equals(expected)) throw new AssertionError("Unexpected sign line length: " + lines[0].length());
    }

    private static SignText frontText(SignBlockEntity sign) {
        try {
            try {
                return (SignText) SignBlockEntity.class.getMethod("getFrontText").invoke(sign);
            } catch (NoSuchMethodException modern) {
                Class<?> slot = Class.forName("net.minecraft.world.level.block.entity.SignTextSlot");
                return (SignText) SignBlockEntity.class.getMethod("getText", slot).invoke(sign, slot.getField("FRONT").get(null));
            }
        } catch (ReflectiveOperationException error) {
            throw new AssertionError(error);
        }
    }

    private static Component frontLine(SignText text) {
        try {
            Object messages = SignText.class.getMethod("getMessages", boolean.class).invoke(text, false);
            return messages instanceof Component[] lines ? lines[0] : (Component) ((List<?>) messages).getFirst();
        } catch (ReflectiveOperationException error) {
            throw new AssertionError(error);
        }
    }

    private static Field field(Class<?> type, String name) {
        try {
            var field = type.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException error) {
            throw new AssertionError(error);
        }
    }

    private static Object read(Field field, Object object) {
        try { return field.get(object); }
        catch (IllegalAccessException error) { throw new AssertionError(error); }
    }

    private static void grant(Field field, ExperimentalAccess.Status status) {
        try { field.set(null, status); }
        catch (IllegalAccessException error) { throw new AssertionError(error); }
    }
}
