// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.features.items;

import com.customcosmetics.features.items.ItemAppearanceStore.Appearance;
import com.customcosmetics.mixin.ItemAppearanceMixin.SpritePixels;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.Base64;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/** Admin-only thumbnail made from the baked item sprite, with name and base item metadata. */
public record ItemSharingPreview(String originalName, String itemId, String rgba, String head) {
    private static final int SIZE = 16;

    public static ItemSharingPreview capture(Minecraft client, ItemStack source, Appearance appearance) {
        var model = appearance.model().isEmpty() ? null : Identifier.tryParse(appearance.model());
        var stack = ItemCosmetics.preview(source, model);
        String name = ChatFormatting.stripFormatting(ItemCosmetics.preview(source, null).getHoverName().getString());
        if (name.length() > 256) name = name.substring(0, 256);
        String itemId = BuiltInRegistries.ITEM.getKey(source.getItem()).toString();
        String head = appearance.headTexture();
        if (head.isEmpty() && model == null && appearance.armorModel().isEmpty()) head = originalHead(stack);
        if (!head.isEmpty()) return new ItemSharingPreview(name, itemId, "", head.substring(head.lastIndexOf(':') + 1));
        return new ItemSharingPreview(name, itemId, sprite(client, stack, appearance.armorColor()), "");
    }

    private static String originalHead(ItemStack stack) {
        var profile = stack.get(DataComponents.PROFILE);
        if (profile == null) return "";
        for (var texture : profile.partialProfile().properties().get("textures")) {
            try { return HeadTexture.normalize(texture.value()); }
            catch (IllegalArgumentException ignored) { /* This property has no usable Minecraft skin. */ }
        }
        return "";
    }

    private static String sprite(Minecraft client, ItemStack stack, Integer tint) {
        try {
            var state = new ItemStackRenderState();
            client.getItemModelResolver().updateForTopItem(state, stack, ItemDisplayContext.GUI, client.level, client.player, 0);
            var material = state.pickParticleMaterial(RandomSource.create(0));
            if (material == null) return "";
            var contents = material.sprite().contents();
            if (contents.name().getPath().equals("missingno")) return "";
            var pixels = ((SpritePixels) (Object) contents).customcosmetics$originalImage();
            var rgba = new byte[SIZE * SIZE * 4];
            for (int y = 0; y < SIZE; y++) {
                for (int x = 0; x < SIZE; x++) {
                    int color = pixels.getPixel(x * contents.width() / SIZE, y * contents.height() / SIZE);
                    int offset = (y * SIZE + x) * 4;
                    for (int component = 0; component < 3; component++) {
                        int shift = (2 - component) * 8;
                        int channel = (color >>> shift) & 255;
                        if (tint != null) channel = channel * ((tint >>> shift) & 255) / 255;
                        rgba[offset + component] = (byte) channel;
                    }
                    rgba[offset + 3] = (byte) (color >>> 24);
                }
            }
            return Base64.getEncoder().encodeToString(rgba);
        } catch (RuntimeException | LinkageError error) {
            return ""; // A third-party model may not expose a sprite.
        }
    }

    JsonObject json() {
        var result = new JsonObject();
        result.addProperty("originalName", originalName);
        result.addProperty("itemId", itemId);
        if (!rgba.isEmpty()) result.addProperty("rgba", rgba);
        if (!head.isEmpty()) result.addProperty("head", head);
        return result;
    }

    static ItemSharingPreview parse(JsonElement input) {
        if (!(input instanceof JsonObject data)) return null;
        return new ItemSharingPreview(string(data, "originalName", 256), string(data, "itemId", 256),
            string(data, "rgba", 1368), string(data, "head", 64));
    }

    private static String string(JsonObject data, String field, int max) {
        if (!(data.get(field) instanceof com.google.gson.JsonPrimitive value) || !value.isString()) return "";
        return value.getAsString().length() <= max ? value.getAsString() : "";
    }
}
