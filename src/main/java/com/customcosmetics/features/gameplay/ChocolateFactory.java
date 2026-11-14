// SPDX-License-Identifier: GPL-3.0-only
// Adapted from Quoi ChocolateFactory, based on OdinLegacy by odtheking (BSD-3-Clause).
package com.customcosmetics.features.gameplay;

import com.customcosmetics.compatibility.ClientUiAccess;
import com.customcosmetics.compatibility.PartyRenderPipeline;
import com.customcosmetics.config.ConfigManager;
import com.customcosmetics.features.items.HeadTexture;
import com.customcosmetics.features.party.PartyGeometry;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.authlib.GameProfile;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.phys.AABB;
import java.util.EnumMap;
import java.util.List;
import java.util.regex.Pattern;

public final class ChocolateFactory {
    private static final Pattern CPS = Pattern.compile("([\\d.,]+)\\s+per second");
    private static final Pattern MULTIPLIER = Pattern.compile("Total Multiplier:\\s+([\\d.]+)x");
    private static final Pattern TOWER = Pattern.compile("Status:\\s+(ACTIVE|INACTIVE)");
    private static final Pattern CHARGES = Pattern.compile("Charges:\\s*(\\d+)\\s*/\\s*\\d+");
    private static final Pattern SPAWN = Pattern.compile("A Chocolate (Breakfast|Lunch|Dinner|Brunch|Déjeuner|Supper) Egg has appeared!");
    private static final Pattern CLAIM = Pattern.compile("(?:found a|collected this) Chocolate (Breakfast|Lunch|Dinner|Brunch|Déjeuner|Supper) Egg");
    private static final EnumMap<Meal, ArmorStand> eggs = new EnumMap<>(Meal.class);
    private static final EnumMap<Meal, Integer> previousEggs = new EnumMap<>(Meal.class);
    private static final java.util.Set<Integer> claimed = new java.util.HashSet<>();
    private static final java.util.Map<Integer, SeenHead> seenHeads = new java.util.HashMap<>();
    private static long nextAction, nextUpgrade;
    private static int eggTicks;
    private record Upgrade(int slot, long cost, double payback) {}
    private record SeenHead(ResolvableProfile profile, int texture) {}
    private enum Meal {
        Breakfast(0), Lunch(1), Dinner(2), Brunch(0), Déjeuner(1), Supper(2);
        final int texture;
        Meal(int texture) { this.texture = texture; }
    }
    private static final int[] EGG_COLORS = {0xFFFFAA00, 0xFF5555FF, 0xFF55FF55};
    private static final List<String> EGG_HASHES = List.of(
        "a49333d85b8a315d0336eb2df37d8a714ca24c51b8c6074f1b5b927deb516c24",
        "7ae6d2d31d8167bcaf95293b68a4acd872d66e751db5a34f2cbc6766a0356d0a",
        "e5e36165819fd2850f98552edcd763ff986313119283c126ace0c4cc495e76a8");
    private ChocolateFactory() {}

    static void reset() { eggs.clear(); previousEggs.clear(); claimed.clear(); seenHeads.clear(); nextAction = nextUpgrade = 0; }

    private static AbstractContainerScreen<?> screen(Minecraft client) {
        return ClientUiAccess.screen(client) instanceof AbstractContainerScreen<?> menu
            && menu.getTitle().getString().equals("Chocolate Factory") && menu.getMenu().slots.size() >= 54 ? menu : null;
    }

    public static boolean silence(Minecraft client, String sound) {
        return ConfigManager.chocolateMute && GameplayModule.CHOCOLATE_FACTORY.enabled()
            && sound.equals("minecraft:entity.generic.eat") && screen(client) != null
            && com.customcosmetics.features.animations.PresenceWorld.isHypixel(client);
    }

    static void tick(Minecraft client) {
        if (!GameplayModule.CHOCOLATE_FACTORY.enabled()) { if (!eggs.isEmpty() || !previousEggs.isEmpty() || !seenHeads.isEmpty()) reset(); return; }
        if (ConfigManager.chocolateEggs && ++eggTicks % 10 == 0) updateEggs(client);
        var current = screen(client);
        if (current == null || client.gameMode == null || client.player.containerMenu != current.getMenu()) return;
        var menu = current.getMenu();
        long now = System.nanoTime();
        if (now >= nextAction && (ConfigManager.chocolateClick || ConfigManager.chocolateTower || ConfigManager.chocolateStrays)) {
            nextAction = now + ConfigManager.chocolateDelay * 1_000_000L;
            if (ConfigManager.chocolateTower && "INACTIVE".equals(match(menu.getSlot(39).getItem(), TOWER))
                    && integer(match(menu.getSlot(39).getItem(), CHARGES)) > 0) {
                click(client, menu, 39, 1, ContainerInput.PICKUP);
                return;
            }
            if (ConfigManager.chocolateStrays) for (int slot = 0; slot < 54; slot++) {
                String title = menu.getSlot(slot).getItem().getHoverName().getString();
                if (title.contains("CLICK ME!") || title.contains("Golden Rabbit")) {
                    click(client, menu, slot, 0, ContainerInput.PICKUP);
                    return;
                }
            }
            if (ConfigManager.chocolateClick && !match(menu.getSlot(13).getItem(), CPS).isEmpty()) {
                click(client, menu, 13, 1, ContainerInput.PICKUP);
            }
        }
        if (ConfigManager.chocolateUpgrade && now >= nextUpgrade) {
            nextUpgrade = now + ConfigManager.chocolateUpgradeDelay * 1_000_000L;
            Upgrade upgrade = bestUpgrade(menu);
            long balance = integer(menu.getSlot(13).getItem().getHoverName().getString().replaceAll("[^0-9]", ""));
            if (upgrade != null && balance >= upgrade.cost()) click(client, menu, upgrade.slot(), 2, ContainerInput.CLONE);
        }
    }

    private static void click(Minecraft client, AbstractContainerMenu menu, int slot, int mouse, ContainerInput kind) {
        if (menu.getSlot(slot).container != client.player.getInventory()) client.gameMode.handleContainerInput(menu.containerId, slot, mouse, kind, client.player);
    }

    private static Upgrade bestUpgrade(AbstractContainerMenu menu) {
        var tower = menu.getSlot(39).getItem();
        double cps = decimal(match(menu.getSlot(13).getItem(), CPS));
        double listed = decimal(match(menu.getSlot(45).getItem(), MULTIPLIER));
        double raw = cps / listed;
        String towerName = tower.getHoverName().getString();
        double base = listed - ("ACTIVE".equals(match(tower, TOWER)) ? .1 * roman(towerName.substring(towerName.lastIndexOf(' ') + 1)) : 0);
        if (!Double.isFinite(raw) || raw <= 0 || base <= 0) return null;
        Upgrade best = null;
        for (int slot = 28; slot <= 42; slot++) {
            double gain = slot <= 34 ? (slot - 27) * base : slot == 39 ? raw * .1 / 8 : slot == 42 ? raw * .01 : 0;
            if (gain <= 0) continue;
            var lore = lore(menu.getSlot(slot).getItem());
            for (int i = 0; i + 1 < lore.size(); i++) {
                if (!lore.get(i).contains("Cost")) continue;
                long cost = integer(lore.get(i + 1).replaceAll("[^0-9]", ""));
                if (cost > 0 && (best == null || cost / gain < best.payback())) best = new Upgrade(slot, cost, cost / gain);
                break;
            }
        }
        return best;
    }

    static void message(Minecraft client, String text) {
        if (!ConfigManager.chocolateEggs || !GameplayModule.CHOCOLATE_FACTORY.enabled() || !text.contains("Chocolate ")) return;
        var spawn = SPAWN.matcher(text);
        if (spawn.find()) {
            var meal = Meal.valueOf(spawn.group(1));
            var old = eggs.remove(meal);
            if (old != null) previousEggs.put(meal, old.getId());
            updateEggs(client);
            return;
        }
        var claim = CLAIM.matcher(text);
        if (!claim.find()) return;
        var meal = Meal.valueOf(claim.group(1));
        ArmorStand closest = null;
        for (var entry : eggs.entrySet()) if (entry.getKey().texture == meal.texture && !claimed.contains(entry.getValue().getId())
                && (closest == null || entry.getValue().distanceToSqr(client.player) < closest.distanceToSqr(client.player)))
            closest = entry.getValue();
        if (closest != null) claimed.add(closest.getId());
    }

    private static void updateEggs(Minecraft client) {
        if (client.level == null || client.player == null) return;
        eggs.values().removeIf(stand -> !stand.isAlive());
        for (var stand : client.level.getEntitiesOfClass(ArmorStand.class, client.player.getBoundingBox().inflate(80))) {
            if (eggs.containsValue(stand) || claimed.contains(stand.getId())) continue;
            var profile = stand.getItemBySlot(EquipmentSlot.HEAD).get(DataComponents.PROFILE);
            if (profile == null) continue;
            var seen = seenHeads.get(stand.getId());
            if (seen == null || seen.profile() != profile) {
                seen = new SeenHead(profile, eggTexture(profile.partialProfile()));
                seenHeads.put(stand.getId(), seen);
            }
            int hash = seen.texture();
            if (hash < 0) continue;
            for (var meal : Meal.values()) if (meal.texture == hash && !eggs.containsKey(meal)
                     && previousEggs.getOrDefault(meal, -1) != stand.getId()) {
                eggs.put(meal, stand);
                break;
            }
        }
        if (seenHeads.size() > 2048) seenHeads.clear();
    }

    static int eggTexture(GameProfile profile) {
        for (var texture : profile.properties().get("textures")) {
            try {
                int index = EGG_HASHES.indexOf(HeadTexture.normalize(texture.value()));
                if (index >= 0) return index;
            } catch (IllegalArgumentException invalid) {
                // Continue if a profile has another texture property.
            }
        }
        return -1;
    }

    static void render(LevelRenderContext context) {
        if (eggs.isEmpty() || !ConfigManager.chocolateEggs || !GameplayModule.CHOCOLATE_FACTORY.enabled()) return;
        var camera = context.levelState().cameraRenderState;
        var font = Minecraft.getInstance().font;
        var queue = context.submitNodeCollector();
        var pose = new PoseStack();
        for (var entry : eggs.entrySet()) {
            var stand = entry.getValue();
            if (!stand.isAlive() || claimed.contains(stand.getId())) continue;
            var pos = stand.position().subtract(camera.pos).add(0, 1.47, 0);
            var box = AABB.unitCubeFromLowerCorner(pos.add(-.5, 0, -.5));
            int color = EGG_COLORS[entry.getKey().texture];
            queue.submitCustomGeometry(pose, PartyRenderPipeline.type(), (matrices, output) ->
                PartyGeometry.outline(output, matrices.pose(), box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ, .026, color));
            var labelPose = new PoseStack();
            labelPose.translate(pos.x, pos.y + 1.7, pos.z);
            labelPose.mulPose(new org.joml.Matrix4f().rotation(camera.orientation));
            float scale = (float) Math.max(.03, pos.length() * .003);
            labelPose.scale(scale, -scale, scale);
            var label = Component.literal(entry.getKey().name() + " Egg (" + (int) pos.length() + "m)");
            queue.submitText(labelPose, -font.width(label) / 2f, 0, label.getVisualOrderText(), true,
                Font.DisplayMode.SEE_THROUGH, 0xF000F0, color, 0x50000000, 0);
        }
    }

    private static List<String> lore(ItemStack item) {
        var data = item.get(DataComponents.LORE);
        return data == null ? List.of() : data.lines().stream().map(Component::getString).toList();
    }
    private static String match(ItemStack item, Pattern pattern) {
        for (String line : lore(item)) { var m = pattern.matcher(line); if (m.find()) return m.group(1); }
        return "";
    }
    private static long integer(String value) { try { return Long.parseLong(value); } catch (NumberFormatException invalid) { return 0; } }
    private static double decimal(String value) { try { return Double.parseDouble(value.replace(",", "")); } catch (NumberFormatException invalid) { return 0; } }
    private static int roman(String s) {
        int total = 0, last = 0;
        for (int i = s.length() - 1; i >= 0; i--) {
            int value = switch (s.charAt(i)) { case 'I' -> 1; case 'V' -> 5; case 'X' -> 10; case 'L' -> 50; case 'C' -> 100; default -> 0; };
            if (value == 0) return 0;
            total += value < last ? -value : value;
            last = value;
        }
        return total;
    }
}
