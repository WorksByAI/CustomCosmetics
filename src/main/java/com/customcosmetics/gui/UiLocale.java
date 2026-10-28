// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.gui;
import com.customcosmetics.config.ConfigManager;
import net.minecraft.client.Minecraft;
public final class UiLocale {
    private UiLocale(){}
    public static void refresh(){var client=Minecraft.getInstance();UiText.configure(ConfigManager.uiLanguage,client==null||client.options==null?"en_us":client.options.languageCode);}
    public static void cycle(){ConfigManager.uiLanguage=switch(UiText.normalize(ConfigManager.uiLanguage)){case "auto"->"zh_cn";case "zh_cn"->"en_us";default->"auto";};refresh();ConfigManager.requestSave();}
}
