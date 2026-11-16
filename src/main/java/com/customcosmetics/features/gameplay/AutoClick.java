// SPDX-License-Identifier: BSD-3-Clause
// Adapted from Nebulune AutoClicker, copyright 2025 Starred. See NOTICE.
package com.customcosmetics.features.gameplay;

import com.customcosmetics.client.ModErrors;
import com.customcosmetics.compatibility.ClientUiAccess;
import com.customcosmetics.config.ConfigManager;
import com.customcosmetics.features.animations.PresenceWorld;
import com.customcosmetics.features.map.Freecam;
import com.customcosmetics.features.pack.LegacyPackItems;
import com.customcosmetics.gui.UiText;
import com.customcosmetics.utils.MessageUtils;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

public final class AutoClick {
    public static final String LEFT_KEY = "key.customcosmetics.auto_clicker.left";
    public static final String RIGHT_KEY = "key.customcosmetics.auto_clicker.right";
    public static final String REQUIRED_KEY = "key.customcosmetics.auto_clicker.required";
    private static final int TICKS_PER_SECOND = 20;
    private static final Button LEFT = new Button(true), RIGHT = new Button(false);
    private static KeyMapping required;
    private static InputConstants.Key capturedRequirement;
    private static boolean mining;

    private AutoClick() {}

    private static final class Button {
        final boolean left;
        KeyMapping key;
        InputConstants.Key captured;
        int progress;

        Button(boolean left) { this.left = left; }
        boolean enabled() { return left ? ConfigManager.autoClickerLeft : ConfigManager.autoClickerRight; }
        int cps() { return left ? ConfigManager.autoClickerLeftCps : ConfigManager.autoClickerRightCps; }
        Set<String> items() { return left ? ConfigManager.autoClickerLeftItems : ConfigManager.autoClickerRightItems; }
        void items(Set<String> items) {
            if (left) ConfigManager.autoClickerLeftItems = Set.copyOf(items);
            else ConfigManager.autoClickerRightItems = Set.copyOf(items);
        }

        void click(KeyMapping action) {
            int jitter = ConfigManager.autoClickerJitter * 2;
            progress += adjustedCps(cps(), ThreadLocalRandom.current().nextInt(-jitter, jitter + 1));
            if (progress >= TICKS_PER_SECOND) {
                KeyMapping.click(KeyMappingHelper.getBoundKeyOf(action));
                progress -= TICKS_PER_SECOND;
            }
        }

        boolean capture(InputConstants.Key input, String item) {
            if (!key.matches(input) || !enabled() || !allows(item)) return false;
            captured = input;
            key.setDown(true);
            return true;
        }

        boolean allows(String item) { return !ConfigManager.autoClickerWhitelistOnly || items().contains(item); }

        void release() {
            if (captured != null) key.setDown(false);
            captured = null;
        }
    }

    static void init() {
        var category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("customcosmetics", "auto_clicker"));
        LEFT.key = register(LEFT_KEY, category);
        RIGHT.key = register(RIGHT_KEY, category);
        required = register(REQUIRED_KEY, category);
        ClientTickEvents.START_CLIENT_TICK.register(client -> {
            if (!ModErrors.attempt(ModErrors.Key.GAMEPLAY, () -> tick(client))) reset();
        });
    }

    private static KeyMapping register(String name, KeyMapping.Category category) {
        return KeyMappingHelper.registerKeyMapping(new KeyMapping(name, InputConstants.UNKNOWN.getValue(), category));
    }

    public static KeyMapping key(boolean left) { return (left ? LEFT : RIGHT).key; }
    public static KeyMapping requiredKey() { return required; }

    static int adjustedCps(int base, int offset) { return Math.clamp(base + offset, 1, TICKS_PER_SECOND); }

    static boolean requirementAllows(boolean enabled, boolean reversed, boolean pressed) {
        return !enabled || pressed != reversed;
    }

    public static boolean mouse(long window, int button, int action) {
        if (required == null) return false;
        var client = Minecraft.getInstance();
        if (client.getWindow().handle() != window) return false;
        var input = InputConstants.Type.MOUSE.getOrCreate(button);
        if (action == InputConstants.RELEASE) {
            if (input.equals(LEFT.captured)) LEFT.release();
            if (input.equals(RIGHT.captured)) RIGHT.release();
            if (input.equals(capturedRequirement)) releaseRequirement();
            if (!LEFT.key.isDown()) mining = false;
            return false;
        }
        if (action != InputConstants.PRESS || !GameplayModule.AUTO_CLICKER.enabled()
                || !(LEFT.enabled() && LEFT.key.matches(input) || RIGHT.enabled() && RIGHT.key.matches(input))
                || !ready(client) || !PresenceWorld.isHypixel(client) || !PresenceWorld.isSkyblock(client)
                || !requirementAllows(ConfigManager.autoClickerRequireKey, ConfigManager.autoClickerReverseKey,
                    required.isDown() || required.matches(input))) return false;
        return captureMouse(client.player.getMainHandItem(), input);
    }

    private static boolean captureMouse(ItemStack held, InputConstants.Key input) {
        var attributes = itemAttributes(held);
        if (blocked(attributes)) return false;
        String item = ConfigManager.autoClickerWhitelistOnly ? itemKey(held, attributes) : "";
        boolean left = LEFT.capture(input, item);
        boolean right = RIGHT.capture(input, item);
        if ((left || right) && required.matches(input)) {
            capturedRequirement = input;
            required.setDown(true);
        }
        return left || right;
    }

    private static boolean ready(Minecraft client) {
        return client.player != null && client.level != null && client.gameMode != null && client.isWindowActive()
            && ClientUiAccess.screen(client) == null && !Freecam.active();
    }

    private static void tick(Minecraft client) {
        if (!GameplayModule.AUTO_CLICKER.enabled() || !ready(client)
                || !PresenceWorld.isHypixel(client) || !PresenceWorld.isSkyblock(client)) {
            reset();
            return;
        }
        tickActive(client);
    }

    private static void tickActive(Minecraft client) {
        mining = false;
        boolean left = LEFT.enabled() && LEFT.key.isDown();
        boolean right = RIGHT.enabled() && RIGHT.key.isDown();
        if (!(left || right) || client.player.isUsingItem()
                || !requirementAllows(ConfigManager.autoClickerRequireKey, ConfigManager.autoClickerReverseKey, required.isDown())) {
            return;
        }
        var item = client.player.getMainHandItem();
        var attributes = itemAttributes(item);
        if (blocked(attributes)) return;
        if (ConfigManager.autoClickerWhitelistOnly) {
            String identity = itemKey(item, attributes);
            left &= LEFT.allows(identity);
            right &= RIGHT.allows(identity);
        }
        mining = left && ConfigManager.autoClickerBreakBlocks
            && client.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK
            && !client.level.getBlockState(hit.getBlockPos()).isAir();
        if (mining || client.gameMode.isDestroying()) return;
        if (left) LEFT.click(client.options.keyAttack);
        if (right) RIGHT.click(client.options.keyUse);
    }

    public static boolean continuesMining(Minecraft client) {
        return mining && ready(client) && client.mouseHandler.isMouseGrabbed()
            && !blocked(itemAttributes(client.player.getMainHandItem()));
    }

    private static boolean blocked(CompoundTag attributes) {
        return ConfigManager.autoClickerBlockDungeonBreaker && attributes != null
            && "DUNGEONBREAKER".equals(attributes.getStringOr("id", ""));
    }

    static void reset() {
        mining = false;
        LEFT.release();
        RIGHT.release();
        releaseRequirement();
        LEFT.progress = RIGHT.progress = 0;
    }

    private static void releaseRequirement() {
        if (capturedRequirement == null) return;
        required.setDown(false);
        capturedRequirement = null;
    }

    private static CompoundTag itemAttributes(ItemStack stack) {
        var data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) return null;
        var tag = ((LegacyPackItems.CustomDataView) (Object) data).customcosmetics$readOnlyTag();
        return tag.getCompound("ExtraAttributes").orElse(tag);
    }

    private static String itemKey(ItemStack stack, CompoundTag attributes) {
        if (stack.isEmpty()) return "";
        if (attributes != null) {
            String uuid = attributes.getStringOr("uuid", "");
            if (!uuid.isBlank()) return uuid;
            String id = attributes.getStringOr("id", "");
            if (!id.isBlank()) return id;
        }
        return stack.getHoverName().getString();
    }

    public static LiteralArgumentBuilder<FabricClientCommandSource> command(String name) {
        var root = ClientCommands.literal(name).executes(context -> feedback("Use /csb ac list, or /csb ac add, remove or clear followed by left or right."));
        for (String action : new String[]{"add", "remove", "clear"}) {
            root.then(ClientCommands.literal(action)
                .then(ClientCommands.literal("left").executes(context -> edit(context.getSource(), LEFT, action)))
                .then(ClientCommands.literal("right").executes(context -> edit(context.getSource(), RIGHT, action))));
        }
        root.then(ClientCommands.literal("list").executes(context -> {
            feedback("AutoClick whitelist");
            list(LEFT);
            list(RIGHT);
            return 1;
        }));
        return root;
    }

    private static int edit(FabricClientCommandSource source, Button side, String action) {
        var items = new HashSet<>(side.items());
        if (action.equals("clear")) items.clear();
        else {
            var player = source.getPlayer();
            if (player == null || player.getMainHandItem().isEmpty()) return feedback("Hold an item first.");
            var held = player.getMainHandItem();
            String item = itemKey(held, itemAttributes(held));
            if (action.equals("add") && !items.add(item)) return feedback("This item is already on the whitelist.");
            if (action.equals("remove") && !items.remove(item)) return feedback("This item is not on the whitelist.");
        }
        side.items(items);
        ConfigManager.requestSave();
        return feedback("AutoClick whitelist updated.");
    }

    private static void list(Button side) {
        String title = UiText.tr(side.left ? "Left AutoClick whitelist" : "Right AutoClick whitelist");
        MessageUtils.sendMessage(title + " (" + side.items().size() + ")");
        side.items().stream().sorted().forEach(item -> MessageUtils.sendMessage("  " + item));
    }

    private static int feedback(String message) {
        MessageUtils.sendMessage(UiText.tr(message));
        return 1;
    }
}
