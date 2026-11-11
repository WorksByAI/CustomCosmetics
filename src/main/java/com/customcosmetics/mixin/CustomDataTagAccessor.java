// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.mixin;

import com.customcosmetics.features.pack.CatharsisItemProperties;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.component.CustomData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(CustomData.class)
public abstract class CustomDataTagAccessor implements CatharsisItemProperties.ItemData {
    @Shadow @Final private CompoundTag tag;
    @Override public CompoundTag ccos$tag() { return tag; }
}
