// SPDX-License-Identifier: AGPL-3.0-or-later
// Goldor phase timings and actions adapted from NoammAddons AutoI4 and PlayerUtils (CC0-1.0). See NOTICE.
package com.customcosmetics.features.gameplay;

import com.customcosmetics.compatibility.ClientUiAccess;
import com.customcosmetics.config.ConfigManager;
import com.customcosmetics.features.pack.LegacyPackItems;
import com.customcosmetics.utils.MessageUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

final class AutoI4Actions {
    static final List<String> LEAP_CLASSES = List.of("Tank", "Mage", "Healer", "Archer");
    private static final Set<String> NON_FISHING_RODS = Set.of("SOUL_WHIP", "FLAMING_FLAY", "GRAPPLING_HOOK");
    private static final Pattern MELODY = Pattern.compile(
        "^Party > (?:\\[[^]]+] )*([A-Za-z0-9_]{1,16}): .*?(?<!\\d)(?:[0-3]/[34]|(?:0|25|33|34|50|66|67|75|99|100)%)(?!\\w)");
    private static final String GOLDOR_START = "[BOSS] Goldor: Who dares trespass into my domain?";
    private static final int FIRST_SWAP = 70, SECOND_SWAP = 140, SAVE_LEAP = 207;
    private long deadline;
    private int startTick, serverTicks = -1, preparationStage, leapAt = -1;
    private boolean rodAttempted, maskAttempted, rodCast;
    private String melodyPlayer;
    private int rodSlot = -1, originalSlot, rodDue, maskDeadline = -1, maskReady;
    private LocalPlayer rodPlayer;
    private AbstractContainerScreen<?> maskScreen;

    void message(Minecraft client, String line, boolean onDevice, boolean completed) {
        if (GOLDOR_START.equals(line) && deadline == 0) {
            startTick = client.player.tickCount;
            deadline = System.nanoTime() + 30_000_000_000L;
        }
        if (!active()) return;
        if (ConfigManager.autoI4Leap && ConfigManager.autoI4LeapToMelody && !line.endsWith(")")) {
            var melody = MELODY.matcher(line);
            if (melody.find() && !melody.group(1).equalsIgnoreCase(client.player.getGameProfile().name())) {
                melodyPlayer = melody.group(1);
            }
        }
        if (onDevice && completed && leapAt == -1) leapAt = elapsed(client) + 4;
    }

    void serverTick(Minecraft client) {
        if (!active() || client.player == null) return;
        serverTicks = serverTicks < 0 ? client.player.tickCount - startTick : serverTicks + 1;
    }

    boolean tick(Minecraft client, AutoLeap leap, boolean completed) {
        if (leap.hasI4Request()) return true;
        if (!active()) {
            stop(client);
            return false;
        }
        int elapsed = elapsed(client);
        if (ConfigManager.autoI4Leap && (leapAt >= 0 && elapsed >= leapAt || leapAt == -1 && elapsed >= SAVE_LEAP)) {
            stop(client);
            if (ClientUiAccess.screen(client) == null) {
                leapAt = -2;
                var priority = new ArrayList<>(LEAP_CLASSES);
                priority.addFirst(priority.remove(ConfigManager.autoI4LeapPriority));
                String melody = ConfigManager.autoI4LeapToMelody ? melodyPlayer : null;
                if (leap.requestI4(client, melody, priority)) return true;
                MessageUtils.sendMessage("Auto I4: Could not queue Spirit Leap. Check the hotbar and your available teammates.");
            } else {
                return true;
            }
        }
        if (completed || leapAt == -2) {
            stop(client);
            return false;
        }
        if (rodSlot >= 0) return tickRod(client);
        if (maskDeadline >= 0) return tickMask(client);
        int stage = elapsed >= SECOND_SWAP ? 2 : elapsed >= FIRST_SWAP ? 1 : 0;
        if (stage <= preparationStage) return false;
        // Use the intended window, even when a screen or a long client pause delayed the tick.
        preparationStage = stage;
        if (ClientUiAccess.screen(client) != null || elapsed > (stage == 1 ? FIRST_SWAP : SECOND_SWAP) + 10) return false;
        boolean rod = ConfigManager.autoI4Rod && !rodAttempted;
        boolean mask = ConfigManager.autoI4Mask && !maskAttempted;
        if (rod && (!mask || (ConfigManager.autoI4SwapOrder == 0) == (stage == 1))) return startRod(client);
        if (mask) {
            maskAttempted = true;
            client.getConnection().sendCommand("stats");
            maskDeadline = client.player.tickCount + 100;
            return true;
        }
        return false;
    }

    private boolean startRod(Minecraft client) {
        rodAttempted = true;
        var inventory = client.player.getInventory();
        for (int slot = 0; slot < 9; slot++) {
            var item = inventory.getItem(slot);
            if (!item.is(Items.FISHING_ROD) || NON_FISHING_RODS.contains(LegacyPackItems.id(item))) continue;
            originalSlot = inventory.getSelectedSlot();
            rodPlayer = client.player;
            rodSlot = slot;
            rodDue = client.player.tickCount + 2;
            rodCast = false;
            inventory.setSelectedSlot(slot);
            return true;
        }
        MessageUtils.sendMessage("Auto I4: No fishing rod in the hotbar.");
        return false;
    }

    private boolean tickRod(Minecraft client) {
        var player = client.player;
        if (!ConfigManager.autoI4Rod || player.getInventory().getSelectedSlot() != rodSlot
                || ClientUiAccess.screen(client) != null || !player.getMainHandItem().is(Items.FISHING_ROD)) {
            stop(client);
            return false;
        }
        if (player.tickCount < rodDue) return true;
        if (!rodCast) {
            client.gameMode.useItem(player, InteractionHand.MAIN_HAND);
            rodCast = true;
            rodDue = player.tickCount + 2;
            return true;
        }
        stop(client);
        return false;
    }

    private boolean tickMask(Minecraft client) {
        var screen = ClientUiAccess.screen(client);
        var player = client.player;
        if (!ConfigManager.autoI4Mask || maskScreen != null && screen != maskScreen || player.tickCount >= maskDeadline) {
            stop(client);
            return false;
        }
        if (maskScreen == null && screen != null) {
            if (!(screen instanceof AbstractContainerScreen<?> container)
                    || !screen.getTitle().getString().strip().equalsIgnoreCase("Stats & Equipment")) {
                stop(client);
                return false;
            }
            maskScreen = container;
            maskReady = player.tickCount + 7;
        }
        if (maskScreen == null || player.tickCount < maskReady) return true;
        var menu = maskScreen.getMenu();
        for (var slot : menu.slots) {
            if (slot.container != player.getInventory()) continue;
            String id = LegacyPackItems.id(slot.getItem());
            if (!id.endsWith("SPIRIT_MASK") && !id.endsWith("BONZO_MASK")) continue;
            client.gameMode.handleContainerInput(menu.containerId, slot.index, 0, ContainerInput.PICKUP, player);
            stop(client);
            return false;
        }
        MessageUtils.sendMessage("Auto I4: No Spirit Mask or Bonzo's Mask in the inventory.");
        stop(client);
        return false;
    }

    private boolean active() {
        return deadline != 0 && System.nanoTime() < deadline;
    }

    private int elapsed(Minecraft client) {
        return serverTicks >= 0 ? serverTicks : client.player.tickCount - startTick;
    }

    void stop(Minecraft client) {
        var player = client.player;
        if (player != null) {
            if (player == rodPlayer && player.getInventory().getSelectedSlot() == rodSlot) {
                player.getInventory().setSelectedSlot(originalSlot);
            }
            if (maskScreen != null && ClientUiAccess.screen(client) == maskScreen) player.closeContainer();
        }
        rodSlot = maskDeadline = -1;
        rodPlayer = null;
        maskScreen = null;
    }

    void reset(Minecraft client) {
        stop(client);
        deadline = 0;
        serverTicks = leapAt = -1;
        preparationStage = 0;
        rodAttempted = maskAttempted = false;
        melodyPlayer = null;
    }
}
