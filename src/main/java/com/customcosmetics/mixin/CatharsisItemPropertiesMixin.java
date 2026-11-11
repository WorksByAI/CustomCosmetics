// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.mixin;

import com.customcosmetics.features.pack.CatharsisItemProperties;
import net.minecraft.client.renderer.item.properties.select.SelectItemModelProperties;
import net.minecraft.client.renderer.item.properties.select.SelectItemModelProperty;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ExtraCodecs;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SelectItemModelProperties.class)
abstract class CatharsisItemPropertiesMixin {
    @Shadow @Final private static ExtraCodecs.LateBoundIdMapper<Identifier, SelectItemModelProperty.Type<?, ?>> ID_MAPPER;

    @Inject(method = "bootstrap", at = @At("RETURN"))
    private static void ccos$registerSelectors(CallbackInfo ci) {
        ID_MAPPER.put(Identifier.fromNamespaceAndPath("catharsis", "skyblock_island"), CatharsisItemProperties.Island.TYPE);
        ID_MAPPER.put(Identifier.fromNamespaceAndPath("catharsis", "data_type"), CatharsisItemProperties.DataType.TYPE);
        ID_MAPPER.put(Identifier.fromNamespaceAndPath("catharsis", "item_model"), CatharsisItemProperties.ItemModel.TYPE);
    }
}
