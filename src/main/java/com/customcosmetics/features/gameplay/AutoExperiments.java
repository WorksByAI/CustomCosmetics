// SPDX-License-Identifier: BSD-3-Clause
// Adapted from Nebulune's AutoExperiments, Copyright (c) 2025, Starred.
package com.customcosmetics.features.gameplay;

import com.customcosmetics.compatibility.ClientInput;
import com.customcosmetics.compatibility.ClientUiAccess;
import com.customcosmetics.config.ConfigManager;
import com.customcosmetics.features.animations.PresenceWorld;
import com.customcosmetics.features.waypoint.Waypoints;
import java.util.ArrayList;
import java.util.TreeMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Pattern;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class AutoExperiments {
    private static final Pattern NUMBER = Pattern.compile("[0-9]+");
    private static final ArrayList<Integer> order = new ArrayList<>();
    private static final TreeMap<Integer, Integer> revealed = new TreeMap<>();
    private static AbstractContainerScreen<?> screen;
    private static boolean chrono, playing, recorded, closeReady;
    private static int index;
    private static int lastChronoSlot = -1;
    private static long clickAt;

    private AutoExperiments() {}

    static void reset() {
        screen = null;
        order.clear();
        revealed.clear();
        playing = recorded = closeReady = false;
        index = 0;
        lastChronoSlot = -1;
    }

    private static boolean isExperiment(AbstractContainerScreen<?> menu) {
        String title = menu.getTitle().getString();
        return title.startsWith("Chronomatron (") || title.startsWith("Ultrasequencer (");
    }

    private static boolean select(Minecraft client, AbstractContainerScreen<?> menu) {
        if (screen == menu) return true;
        reset();
        if (menu.getMenu().slots.size() < 54 || !isExperiment(menu)) return false;
        if (!PresenceWorld.isHypixel(client) || !PresenceWorld.isSkyblock(client)) return false;
        if (!"dynamic".equals(Waypoints.currentIslandMode())) return false;
        screen = menu;
        chrono = menu.getTitle().getString().startsWith("Chronomatron (");
        clickAt = System.nanoTime() + clickDelayNanos();
        return true;
    }

    private static AbstractContainerScreen<?> openContainer(Minecraft client) {
        return client.player != null && ClientUiAccess.screen(client) instanceof AbstractContainerScreen<?> menu
            && client.player.containerMenu == menu.getMenu() ? menu : null;
    }

    public static void updated(Minecraft client) {
        if (!GameplayModule.AUTO_EXPERIMENTS.enabled()) return;
        var menu = openContainer(client);
        if (menu != null && select(client, menu)) readBoard();
    }

    static void tick(Minecraft client) {
        if (!GameplayModule.AUTO_EXPERIMENTS.enabled()) {
            if (screen != null) reset();
            return;
        }
        var menu = openContainer(client);
        if (menu == null) {
            if (screen != null) reset();
            return;
        }
        if (client.gameMode == null || !select(client, menu)) return;
        readBoard();
        long now = System.nanoTime();
        if (now - clickAt < 0) return;
        if (ConfigManager.experimentAutoClose && closeReady) {
            client.player.closeContainer();
            reset();
            return;
        }
        if (playing && index < order.size() && menu.getMenu().slots.get(49).getItem().is(Items.CLOCK)) {
            client.gameMode.handleContainerInput(menu.getMenu().containerId, order.get(index++),
                ClientInput.MOUSE_LEFT, ContainerInput.CLONE, client.player);
            clickAt = now + clickDelayNanos();
        }
    }

    public static boolean blocksManualClick(Minecraft client, AbstractContainerScreen<?> menu) {
        return GameplayModule.AUTO_EXPERIMENTS.enabled() && openContainer(client) == menu && select(client, menu);
    }

    private static long clickDelayNanos() {
        return ThreadLocalRandom.current().nextInt(ConfigManager.experimentMinDelay,
            Math.max(ConfigManager.experimentMinDelay, ConfigManager.experimentMaxDelay) + 1) * 1_000_000L;
    }

    static int targetRounds(boolean chrono, boolean getMax, int serums) {
        if (getMax) return chrono ? 16 : 21;
        return (chrono ? 12 : 10) - Math.clamp(serums, 0, 3);
    }

    private static void readBoard() {
        var slots = screen.getMenu().slots;
        var timer = slots.get(49).getItem();
        int goal = targetRounds(chrono, ConfigManager.experimentGetMax, ConfigManager.experimentSerums);
        if (timer.is(Items.GLOWSTONE)) {
            if (playing) {
                if (chrono && lastChronoSlot >= 0 && highlighted(slots.get(lastChronoSlot).getItem())) return;
                closeReady = chrono && index >= order.size() && order.size() >= goal;
                playing = recorded = false;
                if (!chrono) order.clear();
            }
            if (chrono) return;
            revealed.clear();
            for (int slot = 9; slot <= 44; slot++) {
                var item = slots.get(slot).getItem();
                if (!item.isEmpty() && NUMBER.matcher(item.getHoverName().getString()).matches())
                    revealed.put(item.getCount(), slot);
            }
            if (!revealed.isEmpty() && revealed.firstKey() == 1 && revealed.lastKey() == revealed.size()) {
                order.clear();
                order.addAll(revealed.values());
                recorded = true;
                closeReady = order.size() >= goal;
            }
            return;
        }
        if (!timer.is(Items.CLOCK)) return;
        if (chrono && !recorded) {
            for (int slot = 10; slot <= 43; slot++) {
                if (!highlighted(slots.get(slot).getItem())) continue;
                order.add(slot);
                lastChronoSlot = slot;
                recorded = true;
                break;
            }
            if (!recorded) return;
        }
        if (!playing) {
            playing = true;
            index = 0;
        }
    }

    private static boolean highlighted(ItemStack tile) {
        return Boolean.TRUE.equals(tile.get(DataComponents.ENCHANTMENT_GLINT_OVERRIDE));
    }
}
