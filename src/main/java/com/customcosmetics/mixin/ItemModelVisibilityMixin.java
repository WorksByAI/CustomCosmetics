// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.mixin;

import com.customcosmetics.features.items.ItemModelVisibility;
import java.util.List;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.gen.Accessor;

public final class ItemModelVisibilityMixin {
    @Mixin(ItemStackRenderState.class)
    public interface State extends ItemModelVisibility.State {
        @Override @Accessor("activeLayerCount") int customcosmetics$layerCount();
        @Override @Accessor("layers") ItemStackRenderState.LayerRenderState[] customcosmetics$layers();
    }
    @Mixin(ItemStackRenderState.LayerRenderState.class)
    public abstract static class Legacy implements ItemModelVisibility.Layer {
        @Shadow @Final private List<BakedQuad> quads;
        @Shadow private SpecialModelRenderer<?> specialRenderer;
        @Shadow private Material.Baked particleMaterial;
        @Override public List<Identifier> customcosmetics$textures() {
            return quads.stream().map(quad -> quad.materialInfo().sprite().contents().name()).toList();
        }
        @Override public boolean customcosmetics$special() { return specialRenderer != null; }
        @Override public Identifier customcosmetics$particle() { return particleMaterial == null ? null : particleMaterial.sprite().contents().name(); }
    }
    @Mixin(ItemStackRenderState.LayerRenderState.class)
    public abstract static class Modern implements ItemModelVisibility.Layer {
        @Shadow private SpecialModelRenderer<?> specialRenderer;
        @Shadow private Material.Baked particleMaterial;
        // ItemQuads only exists in 26.3; keep its type out of the shared JAR's method descriptors.
        @org.spongepowered.asm.mixin.Unique private static final java.lang.reflect.Field customcosmetics$quads = quadField();
        @org.spongepowered.asm.mixin.Unique private static final java.lang.reflect.Method customcosmetics$all = allMethod();
        @org.spongepowered.asm.mixin.Unique private static java.lang.reflect.Field quadField() {
            try { var field = ItemStackRenderState.LayerRenderState.class.getDeclaredField("quads"); field.setAccessible(true); return field; }
            catch (ReflectiveOperationException error) { throw new IllegalStateException(error); }
        }
        @org.spongepowered.asm.mixin.Unique private static java.lang.reflect.Method allMethod() {
            try { return customcosmetics$quads.getType().getMethod("all"); }
            catch (ReflectiveOperationException error) { throw new IllegalStateException(error); }
        }
        @Override public List<Identifier> customcosmetics$textures() {
            try {
                @SuppressWarnings("unchecked") var quads = (List<BakedQuad>) customcosmetics$all.invoke(customcosmetics$quads.get(this));
                return quads.stream().map(quad -> quad.materialInfo().sprite().contents().name()).toList();
            } catch (ReflectiveOperationException error) { throw new IllegalStateException(error); }
        }
        @Override public boolean customcosmetics$special() { return specialRenderer != null; }
        @Override public Identifier customcosmetics$particle() { return particleMaterial == null ? null : particleMaterial.sprite().contents().name(); }
    }
}
