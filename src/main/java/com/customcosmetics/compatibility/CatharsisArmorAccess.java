// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.compatibility;

import com.customcosmetics.client.CustomCosmeticsClient;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

/** Optional Catharsis access, linked once. No reflection arrays or lookups in the render loop. */
public final class CatharsisArmorAccess {
    private record Bridge(Class<?> hook, Class<?> texture, MethodHandle state, MethodHandle slot) {}
    private static final Bridge BRIDGE = link();
    private static boolean failed;
    private CatharsisArmorAccess() {}
    private static Bridge link() {
        if(!net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("catharsis"))return null;
        try {
            var lookup = MethodHandles.publicLookup();
            var hook = Class.forName("me.owdding.catharsis.hooks.armor.LivingEntityRenderStateHook");
            var definition = Class.forName("me.owdding.catharsis.features.armor.ArmorDefinitionRenderState");
            var model = Class.forName("me.owdding.catharsis.features.armor.models.ArmorModelState");
            var texture = Class.forName("me.owdding.catharsis.features.armor.models.ArmorModelState$Texture");
            return new Bridge(hook, texture,
                lookup.findVirtual(hook, "catharsis$getArmorDefinitionRenderState", MethodType.methodType(definition)),
                lookup.findVirtual(definition, "fromSlot", MethodType.methodType(model, EquipmentSlot.class)));
        } catch (ReflectiveOperationException | LinkageError incompatible) {
            CustomCosmeticsClient.LOGGER.warn("Catharsis armor tint compatibility unavailable: {}", incompatible.toString());
            return null;
        }
    }
    public static boolean usesTexture(Object renderState, ItemStack stack) {
        if (BRIDGE == null || failed || !BRIDGE.hook().isInstance(renderState)) return false;
        var equipment = stack.get(DataComponents.EQUIPPABLE);
        if (equipment == null) return false;
        try {
            Object definition = BRIDGE.state().invoke(renderState);
            return definition != null && BRIDGE.texture().isInstance(BRIDGE.slot().invoke(definition, equipment.slot()));
        } catch (Throwable incompatible) {
            if (incompatible instanceof Error error && !(error instanceof LinkageError)) throw error;
            failed = true;
            CustomCosmeticsClient.LOGGER.warn("Catharsis armor tint compatibility disabled after an access failure", incompatible);
            return false;
        }
    }
}
