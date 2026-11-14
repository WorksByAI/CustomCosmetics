// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.gui;

import com.customcosmetics.config.ConfigManager;
import com.customcosmetics.compatibility.ClientUiAccess;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen;

/** The mod toggle must still work when vanilla's menu blur slider is zero. Never writes Options. */
public final class UiBlurPolicy {
    public interface Graphics { boolean ccos$canBlur(); }
    public interface State { boolean ccos$canBlur(); }
    private static final boolean EXTERNAL_INVENTORY_BLUR = FabricLoader.getInstance().isModLoaded("eg_inventory_blur");
    private UiBlurPolicy(){}
    public static boolean inventoryAvailable(){return !EXTERNAL_INVENTORY_BLUR;}
    public static boolean inventory(Screen screen){
        return inventoryAvailable() && ConfigManager.inventoryBlurEnabled
            && screen instanceof AbstractContainerScreen<?> && inAuthorizedWorld();
    }
    public static boolean inventoryDarkness(Screen screen) {
        return (screen instanceof AbstractContainerScreen<?> || screen instanceof AbstractSignEditScreen)
            && inAuthorizedWorld();
    }
    private static boolean inAuthorizedWorld() {
        return Minecraft.getInstance().level != null;
    }
    public static int inventoryDimColor(int vanilla, int percent) {
        int alpha = vanilla >>> 24;
        if (percent <= 80) return (alpha * percent / 80 << 24) | (vanilla & 0xFFFFFF);
        int remaining = 100 - percent;
        int red = (vanilla >> 16 & 255) * remaining / 20;
        int green = (vanilla >> 8 & 255) * remaining / 20;
        int blue = (vanilla & 255) * remaining / 20;
        return (255 - (255 - alpha) * remaining / 20 << 24) | (red << 16) | (green << 8) | blue;
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
