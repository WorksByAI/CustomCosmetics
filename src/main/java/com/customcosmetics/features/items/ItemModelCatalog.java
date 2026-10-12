// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.features.items;

import java.util.Map;
import net.minecraft.client.renderer.item.ItemModel;
import net.minecraft.resources.Identifier;

public interface ItemModelCatalog {
    Map<Identifier, ItemModel> customcosmetics$itemModels();
}
