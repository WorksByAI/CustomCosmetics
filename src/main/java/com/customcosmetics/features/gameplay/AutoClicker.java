// SPDX-License-Identifier: GPL-3.0-only
// Adapted from Quoi's AutoClicker (jcnlk/quoi). See NOTICE.
package com.customcosmetics.features.gameplay;

import com.customcosmetics.compatibility.ClientInput;
import com.customcosmetics.compatibility.ClientUiAccess;
import com.customcosmetics.config.ConfigManager;
import com.customcosmetics.features.animations.PresenceWorld;
import com.customcosmetics.features.map.Freecam;
import com.customcosmetics.features.pack.LegacyPackItems;
import com.customcosmetics.gui.UiText;
import com.customcosmetics.utils.MessageUtils;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

public final class AutoClicker {
    private static final Button LEFT = new Button("left", ClientInput.MOUSE_LEFT);
    private static final Button RIGHT = new Button("right", ClientInput.MOUSE_RIGHT);
    private static int lastSlot = -1;
    private static boolean mining;

    private AutoClicker() {}

    private static final class Button {
        final String name;
        final int mouseButton;
        KeyMapping key;
        boolean blockedUntilRelease, capturedMouse;
        long nextClick;

        Button(String name, int mouseButton) {
            this.name = name;
            this.mouseButton = mouseButton;
        }

        boolean enabled() { return this == LEFT ? ConfigManager.autoClickerLeft : ConfigManager.autoClickerRight; }
        Set<String> items() { return this == LEFT ? ConfigManager.autoClickerLeftItems : ConfigManager.autoClickerRightItems; }
    }

    static void init() {
        var category = KeyMapping.Category.register(net.minecraft.resources.Identifier.fromNamespaceAndPath("customcosmetics", "auto_clicker"));
        LEFT.key = register(LEFT, category);
        RIGHT.key = register(RIGHT, category);
    }

    private static KeyMapping register(Button button, KeyMapping.Category category) {
        return KeyMappingHelper.registerKeyMapping(new KeyMapping("key.customcosmetics.auto_clicker." + button.name,
            InputConstants.Type.MOUSE, button.mouseButton, category));
    }

    public static KeyMapping key(boolean left) { return (left ? LEFT : RIGHT).key; }

    public static boolean mouse(long window, int button, int action) {
        var side = button == ClientInput.MOUSE_LEFT ? LEFT : button == ClientInput.MOUSE_RIGHT ? RIGHT : null;
        if (side == null || side.key == null) return false;
        var client = Minecraft.getInstance();
        if (client.getWindow().handle() != window) return false;
        if (action != InputConstants.PRESS) {
            if (side.capturedMouse) side.key.setDown(false);
            side.capturedMouse = false;
            return false;
        }
        if (!side.enabled() || !GameplayModule.AUTO_CLICKER.enabled() || !ready(client)
                || !PresenceWorld.isHypixel(client) || !PresenceWorld.isSkyblock(client)
                || !side.key.saveString().equals(InputConstants.Type.MOUSE.getOrCreate(button).getName())) return false;
        if (!acceptsItem(client.player.getMainHandItem(), side)) return false;
        side.capturedMouse = true;
        side.key.setDown(true);
        return true;
    }

    private static boolean ready(Minecraft client) {
        return client.player != null && client.level != null && client.gameMode != null && client.isWindowActive()
            && ClientUiAccess.screen(client) == null && !Freecam.active()
            && (ConfigManager.autoClickerWhileSneaking || !client.player.isShiftKeyDown());
    }

    static void tick(Minecraft client, boolean inSkyblock) {
        if (LEFT.key == null || RIGHT.key == null || !inSkyblock || !GameplayModule.AUTO_CLICKER.enabled() || !ready(client)) {
            reset(client);
            return;
        }
        int slot = client.player.getInventory().getSelectedSlot();
        if (ConfigManager.autoClickerStopOnSwap && lastSlot >= 0 && slot != lastSlot) {
            LEFT.blockedUntilRelease = LEFT.key.isDown();
            RIGHT.blockedUntilRelease = RIGHT.key.isDown();
        }
        lastSlot = slot;
        ItemStack held = client.player.getMainHandItem();
        boolean left = shouldClick(LEFT, held), right = shouldClick(RIGHT, held);
        boolean targetBlock = client.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK;
        boolean mine = left && ConfigManager.autoClickerBreakBlocks && targetBlock
            && client.hitResult instanceof BlockHitResult hit && breakable(client, hit);
        if (mine != mining) {
            mining = mine;
            client.options.keyAttack.setDown(mine);
        }
        long now = System.nanoTime();
        if (due(LEFT, left && !targetBlock, now, ConfigManager.autoClickerLeftMin, ConfigManager.autoClickerLeftMax))
            ((GameplayFeatures.ClickActions) client).csb$attack();
        if (due(RIGHT, right && !client.player.isUsingItem(), now, ConfigManager.autoClickerRightMin, ConfigManager.autoClickerRightMax))
            ((GameplayFeatures.ClickActions) client).csb$use();
    }

    private static boolean breakable(Minecraft client, BlockHitResult hit) {
        var state = client.level.getBlockState(hit.getBlockPos());
        return !state.isAir() && state.getFluidState().isEmpty();
    }

    static void reset(Minecraft client) {
        if (lastSlot == -1 && !mining && !LEFT.capturedMouse && !RIGHT.capturedMouse) return;
        if (mining) client.options.keyAttack.setDown(false);
        releaseCapture(LEFT);
        releaseCapture(RIGHT);
        mining = false;
        lastSlot = -1;
        LEFT.nextClick = RIGHT.nextClick = 0;
        LEFT.blockedUntilRelease = RIGHT.blockedUntilRelease = false;
    }

    private static void releaseCapture(Button side) {
        if (!side.capturedMouse) return;
        side.capturedMouse = false;
        side.key.setDown(false);
    }

    private static boolean shouldClick(Button side, ItemStack held) {
        if (!side.key.isDown()) {
            side.blockedUntilRelease = false;
            return false;
        }
        return side.enabled() && !side.blockedUntilRelease && acceptsItem(held, side);
    }

    private static boolean acceptsItem(ItemStack held, Button side) {
        return (!ConfigManager.autoClickerBlockDungeonBreaker || !"DUNGEONBREAKER".equals(LegacyPackItems.id(held)))
            && (!ConfigManager.autoClickerWhitelistOnly || !held.isEmpty() && side.items().contains(itemKey(held)));
    }

    private static boolean due(Button side, boolean active, long now, int min, int max) {
        if (!active) {
            side.nextClick = 0;
            return false;
        }
        if (side.nextClick != 0 && now - side.nextClick < 0) return false;
        long fastest = 1_000_000_000L / Math.max(min, max);
        long slowest = 1_000_000_000L / Math.min(min, max);
        long delay = fastest == slowest ? fastest : ThreadLocalRandom.current().nextLong(fastest, slowest + 1);
        side.nextClick = (side.nextClick == 0 ? now : Math.max(now - 45_000_000L, side.nextClick)) + delay;
        return true;
    }

    private static String itemKey(ItemStack stack) {
        var data = stack.get(DataComponents.CUSTOM_DATA);
        if (data != null) {
            var tag = ((LegacyPackItems.CustomDataView) (Object) data).customcosmetics$readOnlyTag();
            var attributes = tag.getCompound("ExtraAttributes").orElse(tag);
            String uuid = attributes.getStringOr("uuid", "");
            if (!uuid.isBlank()) return uuid;
            String id = attributes.getStringOr("id", "");
            if (!id.isBlank()) return id;
        }
        return stack.getHoverName().getString();
    }

    public static com.mojang.brigadier.builder.LiteralArgumentBuilder<FabricClientCommandSource> command() {
        var root = ClientCommands.literal("autoclicker").executes(context -> feedback("Use /csb autoclicker add, remove or clear, followed by left or right."));
        for (String action : new String[]{"add", "remove", "clear"})
            root.then(ClientCommands.literal(action)
                .then(ClientCommands.literal("left").executes(context -> editItems(context.getSource().getPlayer().getMainHandItem(), LEFT, action)))
                .then(ClientCommands.literal("right").executes(context -> editItems(context.getSource().getPlayer().getMainHandItem(), RIGHT, action))));
        return root;
    }

    private static int editItems(ItemStack held, Button side, String action) {
        if (!action.equals("clear") && held.isEmpty()) return feedback("Hold an item first.");
        var items = new HashSet<>(side.items());
        switch (action) {
            case "add" -> {
                if (!items.add(itemKey(held))) return feedback("This item is already on the whitelist.");
            }
            case "remove" -> {
                if (!items.remove(itemKey(held))) return feedback("This item is not on the whitelist.");
            }
            case "clear" -> items.clear();
        }
        if (side == LEFT) ConfigManager.autoClickerLeftItems = Set.copyOf(items);
        else ConfigManager.autoClickerRightItems = Set.copyOf(items);
        ConfigManager.requestSave();
        return feedback("Auto Clicker whitelist updated.");
    }

    private static int feedback(String message) {
        MessageUtils.sendMessage(UiText.tr(message));
        return 1;
    }
}
