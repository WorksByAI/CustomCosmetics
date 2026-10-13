// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.features.items;

import java.util.List;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.resources.Identifier;

/** Examines every submitted layer, not just the model's presence in the baked map. */
public final class ItemModelVisibility {
    public record RenderCall(ItemStackRenderState state, net.minecraft.world.item.ItemStack stack) {}
    public interface State {
        int customcosmetics$layerCount();
        ItemStackRenderState.LayerRenderState[] customcosmetics$layers();
    }
    public interface Layer {
        List<Identifier> customcosmetics$textures();
        boolean customcosmetics$special();
        Identifier customcosmetics$particle();
    }
    private ItemModelVisibility() {}
    public static boolean usable(List<Identifier> textures, boolean special, Identifier particle) {
        Identifier missing = MissingTextureAtlasSprite.getLocation();
        return !textures.contains(missing) && (!special || !missing.equals(particle))
            && (special || !textures.isEmpty());
    }
    public static boolean usable(ItemStackRenderState value) {
        if (!(value instanceof State state) || value.isEmpty()) return false;
        boolean visible = false;
        for (int i = 0; i < state.customcosmetics$layerCount(); i++) {
            var layer = (Layer) state.customcosmetics$layers()[i];
            var textures = layer.customcosmetics$textures();
            if (textures.contains(MissingTextureAtlasSprite.getLocation())
                || layer.customcosmetics$special() && MissingTextureAtlasSprite.getLocation().equals(layer.customcosmetics$particle())) return false;
            visible |= usable(textures, layer.customcosmetics$special(), layer.customcosmetics$particle());
        }
        return visible;
    }
}
