// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.gui;

import com.customcosmetics.config.ConfigManager;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Common chrome for every mod dialog; no global render or client-tick hook. */
public abstract class ThemedScreen extends Screen {
    protected final net.minecraft.client.gui.Font font;
    private final UiTooltipPositioner tooltipPositioner=new UiTooltipPositioner(this);
    public final net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner beginTooltip(GuiGraphicsExtractor g,net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner original){return tooltipPositioner.begin(g,original);}
    public final void endTooltip(){tooltipPositioner.end();}
    protected ThemedScreen(Component title){super(localizeTitle(title));font=UiFonts.get(super.font);UiTheme.refresh();}
    /** Content previews use the live game renderer, including resource packs and font-mod hooks. */
    protected final net.minecraft.client.gui.Font previewFont(){
        var active=minecraft.font;
        return active!=null?active:super.font;
    }
    private static Component localizeTitle(Component title){UiLocale.refresh();return UiText.component(title.getString());}
    @Override public void extractBackground(GuiGraphicsExtractor g,int mx,int my,float dt){
        if(minecraft.level==null)super.extractBackground(g,mx,my,dt);
        else if(ConfigManager.uiBackgroundBlur)g.blurBeforeThisStratum();
        g.fill(0,0,width,height,0x65000000);
    }
}
