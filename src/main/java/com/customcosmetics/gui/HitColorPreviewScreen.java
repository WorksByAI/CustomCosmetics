// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.gui;

import com.customcosmetics.client.ModErrors;
import com.customcosmetics.compatibility.ClientUiAccess;
import com.customcosmetics.config.ConfigManager;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.equipment.trim.ArmorTrim;
import org.joml.Quaternionf;
import org.joml.Vector3f;

final class HitColorPreviewScreen extends ThemedScreen {
    private static final String[] SETS = {"Current", "None", "Leather", "Gold", "Chainmail", "Iron", "Copper", "Diamond", "Netherite"};
    private static final String[] PATTERNS = {"None", "Sentry", "Dune", "Coast", "Wild", "Ward", "Eye", "Vex", "Tide", "Snout", "Rib", "Spire",
        "Wayfinder", "Shaper", "Silence", "Raiser", "Host", "Flow", "Bolt"};
    private static final String[] MATERIALS = {"None", "Quartz", "Iron", "Netherite", "Redstone", "Copper", "Gold", "Emerald", "Diamond", "Lapis",
        "Amethyst", "Resin"};
    private static final String[] PARTS = {"helmet", "chestplate", "leggings", "boots"};
    private final Screen parent;
    private int armor, pattern, material;
    private boolean hurt = true, glint = true, rear;
    private boolean equipmentDirty = true;
    private final ItemStack[] preview = new ItemStack[4];
    private final ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
    private int x, y, w, previewTop, previewBottom;

    HitColorPreviewScreen(Screen parent) {
        super(Component.literal("Hurt Flash Preview"));
        this.parent = parent;
    }

    @Override protected void init() {
        w = Math.min(460, width - 16);
        x = (width - w) / 2;
        int h = Math.min(320, height - 16);
        y = (height - h) / 2;
        previewTop = y + 34;
        previewBottom = y + h - 98;
        int col = (w - 32) / 3;
        cycle(x + 10, previewBottom + 6, col, "Armor Set", SETS, armor, value -> armor = value);
        cycle(x + 16 + col, previewBottom + 6, col, "Trim Pattern", PATTERNS, pattern, value -> pattern = value);
        cycle(x + 22 + 2 * col, previewBottom + 6, col, "Trim Material", MATERIALS, material, value -> material = value);
        addRenderableWidget(new PinkUi.Action(x + 10, previewBottom + 32, col, 20, "Show Hurt Flash", false, () -> hurt = !hurt).selected(() -> hurt));
        addRenderableWidget(new PinkUi.Action(x + 16 + col, previewBottom + 32, col, 20, "Enchant Glint", false, () -> {
            glint = !glint; equipmentDirty = true;
        }).selected(() -> glint));
        addRenderableWidget(new PinkUi.Action(x + 22 + 2 * col, previewBottom + 32, col, 20, "Show Back", false, () -> rear = !rear).selected(() -> rear));
        addRenderableWidget(new PinkUi.Action(x + 10, y + h - 32, w - 20, 22, "Done", true, this::onClose));
    }

    private void cycle(int x, int y, int w, String label, String[] values, int index, java.util.function.IntConsumer update) {
        var field = net.minecraft.client.gui.components.CycleButton.builder(UiText::component, values[index])
            .withValues(values).displayOnlyValue().create(x, y, w, 20, UiText.component(label), (button, value) -> {
                update.accept(java.util.Arrays.asList(values).indexOf(value));
                equipmentDirty = true;
            });
        field.setTooltip(net.minecraft.client.gui.components.Tooltip.create(UiText.component(label)));
        addRenderableWidget(field);
    }

    private void refreshEquipment() {
        equipmentDirty = false;
        ArmorTrim trim = null;
        if (pattern != 0 && material != 0) {
            var registries = minecraft.level.registryAccess();
            var patternKey = ResourceKey.create(Registries.TRIM_PATTERN, identifier(PATTERNS[pattern]));
            var materialKey = ResourceKey.create(Registries.TRIM_MATERIAL, identifier(MATERIALS[material]));
            var patternValue = registries.lookupOrThrow(Registries.TRIM_PATTERN).get(patternKey);
            var materialValue = registries.lookupOrThrow(Registries.TRIM_MATERIAL).get(materialKey);
            if (patternValue.isPresent() && materialValue.isPresent()) trim = new ArmorTrim(materialValue.get(), patternValue.get());
        }
        String armorType = armor == 3 ? "golden" : SETS[armor].toLowerCase(java.util.Locale.ROOT);
        for (int i = 0; i < PARTS.length; i++) {
            if (armor < 2) {
                preview[i] = ItemStack.EMPTY;
                continue;
            }
            var item = BuiltInRegistries.ITEM.getOptional(Identifier.withDefaultNamespace(armorType + "_" + PARTS[i])).orElseThrow();
            var stack = new ItemStack(item);
            stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, glint);
            if (trim != null) stack.set(DataComponents.TRIM, trim);
            preview[i] = stack;
        }
    }

    private static Identifier identifier(String name) {
        return Identifier.withDefaultNamespace(name.toLowerCase(java.util.Locale.ROOT));
    }

    @Override public void extractRenderState(GuiGraphicsExtractor graphics, int mx, int my, float delta) {
        PinkUi.outline(graphics, x, y, w, previewBottom - y + 98, 6, PinkUi.BORDER, PinkUi.PANEL);
        UiFonts.drawTitle(graphics, getTitle().getString(), x + 12, y + 12, PinkUi.TEXT, w - 24);
        if (minecraft.player == null) {
            PinkUi.uiText(graphics, font, "Join a world to preview your skin.", x + 12, previewTop + 12, PinkUi.MUTED, w - 24);
        } else if (previewBottom > previewTop && ModErrors.active(ModErrors.Key.PREVIEW)) {
            try { drawPreview(graphics); }
            catch (RuntimeException | LinkageError failure) { ModErrors.report(ModErrors.Key.PREVIEW, failure); }
        }
        graphics.nextStratum();
        super.extractRenderState(graphics, mx, my, delta);
    }

    private void drawPreview(GuiGraphicsExtractor graphics) {
        Object state;
        com.customcosmetics.features.creature.CreatureModels.beginAvatarPreview();
        try { state = minecraft.getEntityRenderDispatcher().extractEntity(minecraft.player, 1f); }
        finally { com.customcosmetics.features.creature.CreatureModels.endAvatarPreview(); }
        if (!(state instanceof AvatarRenderState avatar)) return;
        if (equipmentDirty) refreshEquipment();
        avatar.hasRedOverlay = hurt;
        avatar.nameTag = avatar.scoreText = null;
        avatar.nameTagAttachment = null;
        avatar.isInvisible = avatar.isInvisibleToPlayer = false;
        avatar.bodyRot = avatar.yRot = avatar.xRot = 0;
        avatar.pose = net.minecraft.world.entity.Pose.STANDING;
        avatar.isCrouching = avatar.isFallFlying = avatar.isVisuallySwimming = avatar.isPassenger = avatar.isUsingItem = false;
        if (armor != 0) {
            avatar.headItem.clear();
            avatar.wornHeadType = null;
            avatar.wornHeadProfile = null;
            avatar.headEquipment = preview[0];
            avatar.chestEquipment = preview[1];
            avatar.legsEquipment = preview[2];
            avatar.feetEquipment = preview[3];
        }
        if (avatar.rightHandItemState.isEmpty()) {
            minecraft.getItemModelResolver().updateForLiving(avatar.rightHandItemState, sword, ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, minecraft.player);
        }
        float scale = Math.max(1, (previewBottom - previewTop) * .40f);
        var rotation = new Quaternionf().rotationZ((float)Math.PI).rotateY(rear ? .18f : (float)Math.PI - .20f);
        graphics.entity(avatar, scale, new Vector3f(0, .95f, 0), rotation, null, x + 10, previewTop, x + w - 10, previewBottom);
    }

    @Override public boolean isPauseScreen() { return false; }
    @Override public void onClose() { ClientUiAccess.setScreen(minecraft, parent); }
}
