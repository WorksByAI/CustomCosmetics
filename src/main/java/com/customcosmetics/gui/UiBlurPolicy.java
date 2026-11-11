// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.gui;

import com.customcosmetics.config.ConfigManager;
import com.customcosmetics.compatibility.ClientUiAccess;
import com.customcosmetics.sync.ExperimentalAccess;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;

/** The mod toggle must still work when vanilla's menu blur slider is zero. Never writes Options. */
public final class UiBlurPolicy {
    public interface Graphics { boolean ccos$canBlur(); }
    public interface State { boolean ccos$canBlur(); }
    private static final boolean EXTERNAL_INVENTORY_BLUR = FabricLoader.getInstance().isModLoaded("eg_inventory_blur");
    private UiBlurPolicy(){}
    public static boolean inventoryAvailable(){return !EXTERNAL_INVENTORY_BLUR;}
    public static boolean inventory(Screen screen){
        return inventoryAvailable() && ConfigManager.inventoryBlurEnabled && ExperimentalAccess.allowed()
            && screen instanceof AbstractContainerScreen<?> && Minecraft.getInstance().level != null;
    }
    public static int effective(int vanilla,boolean enabled,boolean ownScreen,boolean inWorld){
        return enabled&&ownScreen&&inWorld?Math.max(5,vanilla):vanilla;
    }
    public static int radius(int vanilla){
        if(!ConfigManager.uiBackgroundBlur)return vanilla;
        var client=Minecraft.getInstance();
        return client==null?vanilla:effective(vanilla,true,ClientUiAccess.screen(client) instanceof ThemedScreen,client.level!=null);
    }
}
