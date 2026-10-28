// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.gui;

import com.customcosmetics.config.ConfigManager;
import com.customcosmetics.compatibility.ClientUiAccess;
import net.minecraft.client.Minecraft;

/** The mod toggle must still work when vanilla's menu blur slider is zero. Never writes Options. */
public final class UiBlurPolicy {
    private UiBlurPolicy(){}
    public static int effective(int vanilla,boolean enabled,boolean ownScreen,boolean inWorld){
        return enabled&&ownScreen&&inWorld?Math.max(5,vanilla):vanilla;
    }
    public static int radius(int vanilla){
        if(!ConfigManager.uiBackgroundBlur)return vanilla;
        var client=Minecraft.getInstance();
        return client==null?vanilla:effective(vanilla,true,ClientUiAccess.screen(client) instanceof ThemedScreen,client.level!=null);
    }
}
