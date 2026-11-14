// SPDX-License-Identifier: GPL-3.0-only
// AutoGFS and AutoBookCombine behavior adapted from quoi! (jcnlk/quoi).
package com.customcosmetics.features.gameplay;

import com.customcosmetics.compatibility.ClientUiAccess;
import com.customcosmetics.config.ConfigManager;
import com.customcosmetics.features.pack.LegacyPackItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.function.BooleanSupplier;

public final class InventoryAutomation {
    private enum Refill {
        PEARLS(16, "ENDER_PEARL", "ender_pearl", "Ender Pearls", () -> ConfigManager.refillPearls),
        SUPERBOOM(64, "SUPERBOOM_TNT", "superboom_tnt", "Superboom TNT", () -> ConfigManager.refillSuperboom),
        JERRIES(64, "INFLATABLE_JERRY", "inflatable_jerry", "Inflatable Jerries", () -> ConfigManager.refillJerries),
        LEAPS(16, "SPIRIT_LEAP", "spirit_leap", "Spirit Leaps", () -> ConfigManager.refillLeaps);

        final int limit;
        final String id, command, message;
        final BooleanSupplier enabled;
        Refill(int limit, String id, String command, String message, BooleanSupplier enabled) {
            this.limit = limit;
            this.id = id;
            this.command = command;
            this.message = message;
            this.enabled = enabled;
        }
    }

    private static final Refill[] REFILLS = Refill.values();
    private static final int[] counts = new int[REFILLS.length];
    private static final int[] lastRequestedCount = new int[REFILLS.length];
    private static final EnumSet<Refill> emptySacks = EnumSet.noneOf(Refill.class);
    private static final int ANVIL_LEFT = 29, ANVIL_RIGHT = 33, ANVIL_RESULT = 22;
    private static AbstractContainerScreen<?> screen;
    private static long refillAfter, clickAfter;
    private static int nextRefill;
    private static BookCombine pendingCombine;
    private static boolean combinedInAnvil, combineFinished;
    private static boolean refillEnabled;

    private InventoryAutomation() {}

    static void worldChanged() {
        resetRefills();
        changeScreen(null);
    }

    static void resetRefills() {
        emptySacks.clear();
        java.util.Arrays.fill(lastRequestedCount, -1);
        refillAfter = 0;
        refillEnabled = false;
    }

    static void refillState(boolean enabled) {
        if (refillEnabled && !enabled) resetRefills();
        refillEnabled = enabled;
    }

    static void message(String text) {
        if (!GameplayModule.AUTO_GFS.enabled()) return;
        text = net.minecraft.ChatFormatting.stripFormatting(text).strip();
        if (!text.startsWith("You have no ")) return;
        for (var refill : REFILLS) {
            if (text.equals("You have no " + refill.message + " in your Sacks!")) emptySacks.add(refill);
        }
    }

    static void tick(Minecraft client) {
        if (client.gameMode == null || client.getConnection() == null) return;
        Object current = ClientUiAccess.screen(client);
        long now = System.nanoTime();
        if (!(current instanceof AbstractContainerScreen<?> container) || client.player.containerMenu != container.getMenu()) {
            if (screen != null) changeScreen(null);
            if (current == null && GameplayModule.AUTO_GFS.enabled() && now - refillAfter >= 0
                    && !client.player.isSpectator() && client.player.isAlive()
                    && (!ConfigManager.refillDungeonsOnly || com.customcosmetics.features.map.MapEditor.automaticDungeon(client))) {
                refillAfter = now + 1_000_000_000L * (ConfigManager.refillTimed ? ConfigManager.refillIntervalSeconds : 1);
                refill(client);
            }
            return;
        }
        if (screen != container) changeScreen(container);
        String title = screen.getTitle().getString();
        if (title.equals("Anvil")) {
            if (!GameplayModule.AUTO_BOOK_COMBINE.enabled()) {
                pendingCombine = null;
                combineFinished = combinedInAnvil = false;
            } else if (now - clickAfter >= 0) combine(client, now);
        }
    }

    private static void changeScreen(AbstractContainerScreen<?> next) {
        screen = next;
        pendingCombine = null;
        combineFinished = combinedInAnvil = false;
        clickAfter = System.nanoTime();
    }

    private static void refill(Minecraft client) {
        if (!(ConfigManager.refillPearls || ConfigManager.refillSuperboom || ConfigManager.refillJerries || ConfigManager.refillLeaps)) return;
        var inventory = client.player.getInventory();
        java.util.Arrays.fill(counts, 0);
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            var stack = inventory.getItem(slot);
            if (stack.isEmpty()) continue;
            countRefillStack(stack, counts);
        }
        for (var item : REFILLS) {
            int index = item.ordinal();
            if (!item.enabled.getAsBoolean() || counts[index] != lastRequestedCount[index]) lastRequestedCount[index] = -1;
        }
        for (int attempts = 0; attempts < REFILLS.length; attempts++) {
            var item = REFILLS[nextRefill];
            nextRefill = (nextRefill + 1) % REFILLS.length;
            int index = item.ordinal(), amount = counts[index];
            if (item.enabled.getAsBoolean() && !emptySacks.contains(item)
                    && needsRefill(amount, item.limit, ConfigManager.refillTimed, ConfigManager.refillPercent, lastRequestedCount[index])) {
                client.getConnection().sendCommand("gfs " + item.command + " " + (item.limit - amount));
                lastRequestedCount[index] = amount;
                return;
            }
        }
    }

    static void countRefillStack(ItemStack stack, int[] totals) {
        if (stack.isEmpty()) return;
        if (stack.is(Items.ENDER_PEARL)) {
            totals[Refill.PEARLS.ordinal()] += stack.getCount();
            return;
        }
        var custom = stack.get(DataComponents.CUSTOM_DATA);
        if (custom == null) return;
        String id = LegacyPackItems.id(stack);
        if (id.isEmpty()) {
            id = custom.copyTag().getCompound("ExtraAttributes").map(tag -> tag.getStringOr("id", "")).orElse("");
        }
        if ("INFINITE_SPIRIT_LEAP".equals(id)) totals[Refill.LEAPS.ordinal()] = Refill.LEAPS.limit;
        else for (var item : REFILLS) if (item.id.equals(id)) totals[item.ordinal()] += stack.getCount();
    }

    public static int countEnderPearls(Iterable<ItemStack> stacks) {
        int[] totals = new int[REFILLS.length];
        for (var stack : stacks) countRefillStack(stack, totals);
        return totals[Refill.PEARLS.ordinal()];
    }

    static boolean needsRefill(int amount, int limit, boolean timed, int percent, int lastRequest) {
        return amount > 0 && amount < limit && amount != lastRequest && (timed || amount * 100 < limit * percent);
    }

    record Enchant(String id, int level) {}

    private record BookCombine(Enchant enchant, int secondSlot, long deadline) {}

    private static Enchant singleEnchant(ItemStack stack) {
        if (!stack.is(Items.ENCHANTED_BOOK)) return null;
        var custom = stack.get(DataComponents.CUSTOM_DATA);
        if (custom == null) return null;
        var tag = custom.copyTag();
        var enchants = tag.getCompound("ExtraAttributes").orElse(tag).getCompound("enchantments").orElse(null);
        if (enchants == null || enchants.keySet().size() != 1) return null;
        String key = enchants.keySet().iterator().next();
        int level = enchants.getIntOr(key, 0);
        return level > 0 ? new Enchant(key, level) : null;
    }

    static boolean upgradedPair(Enchant expected, Enchant left, Enchant right, Enchant result) {
        return expected != null && expected.equals(left) && expected.equals(right)
            && result != null && result.id().equals(expected.id()) && result.level() == expected.level() + 1;
    }

    private static void combine(Minecraft client, long now) {
        if (combineFinished) return;
        if (!(screen.getMenu() instanceof ChestMenu menu) || menu.getRowCount() != 6 || !menu.getCarried().isEmpty()) {
            pendingCombine = null;
            return;
        }
        ItemStack left = menu.getSlot(ANVIL_LEFT).getItem();
        ItemStack right = menu.getSlot(ANVIL_RIGHT).getItem();
        ItemStack result = menu.getSlot(ANVIL_RESULT).getItem();
        if (pendingCombine != null) {
            var pending = pendingCombine;
            if (now - pending.deadline() >= 0) {
                pendingCombine = null;
                return;
            }
            Enchant leftEnchant = singleEnchant(left), rightEnchant = singleEnchant(right);
            if ((!left.isEmpty() && !pending.enchant().equals(leftEnchant))
                    || (!right.isEmpty() && !pending.enchant().equals(rightEnchant))) {
                pendingCombine = null;
                return;
            }
            if (pending.secondSlot() >= 0) {
                if (left.isEmpty() == right.isEmpty()) return;
                var next = menu.getSlot(pending.secondSlot());
                if (next.container != client.player.getInventory() || !pending.enchant().equals(singleEnchant(next.getItem()))) {
                    pendingCombine = null;
                    return;
                }
                pendingCombine = new BookCombine(pending.enchant(), -1, now + 3_000_000_000L);
                click(client, pending.secondSlot(), ContainerInput.QUICK_MOVE, now + ConfigManager.bookCombineResultDelay * 50_000_000L);
            } else if (upgradedPair(pending.enchant(), leftEnchant, rightEnchant, singleEnchant(result))) {
                pendingCombine = null;
                combinedInAnvil = true;
                click(client, ANVIL_RESULT, ContainerInput.PICKUP, now + 500_000_000L);
            }
            return;
        }
        if (!left.isEmpty() || !right.isEmpty() || !(result.isEmpty() || result.is(Items.ANVIL))) return;
        var books = new HashMap<Enchant, Integer>();
        for (int i = 0; i < menu.slots.size(); i++) {
            var slot = menu.slots.get(i);
            if (slot.container != client.player.getInventory()) continue;
            var enchant = singleEnchant(slot.getItem());
            if (enchant == null || enchant.level() == 5 || enchant.level() >= 10) continue;
            Integer first = books.putIfAbsent(enchant, i);
            if (first == null) continue;
            pendingCombine = new BookCombine(enchant, i, now + 3_000_000_000L);
            click(client, first, ContainerInput.QUICK_MOVE, now + 350_000_000L);
            return;
        }
        if (combinedInAnvil) {
            combineFinished = true;
            com.customcosmetics.utils.MessageUtils.sendMessage(com.customcosmetics.gui.UiText.tr("Finished combining enchanted books."));
            if (ConfigManager.bookCombineDisableAfterFinish) GameplayModule.AUTO_BOOK_COMBINE.toggle();
            if (ConfigManager.bookCombineCloseAfterFinish) screen.onClose();
        }
    }

    private static void click(Minecraft client, int slot, ContainerInput input, long until) {
        clickAfter = until;
        client.gameMode.handleContainerInput(screen.getMenu().containerId, slot, 0, input, client.player);
    }
}
