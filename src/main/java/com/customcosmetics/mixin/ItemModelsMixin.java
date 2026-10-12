// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.mixin;

import com.customcosmetics.features.items.ItemModelCatalog;
import java.util.Map;
import net.minecraft.client.renderer.item.ItemModel;
import net.minecraft.client.resources.model.ModelManager;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ModelManager.class)
public interface ItemModelsMixin extends ItemModelCatalog {
    @Override @Accessor("bakedItemStackModels")
    Map<Identifier, ItemModel> customcosmetics$itemModels();
}
