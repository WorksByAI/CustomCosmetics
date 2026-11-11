// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.mixin;

import com.customcosmetics.compatibility.ClientUiAccess;
import com.customcosmetics.gui.ThemedScreen;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.tooltip.*;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import java.util.List;

/** Only mod-owned screens opt in; world, inventory and chat tooltips are unchanged. */
public final class UiTooltipMixin {
    private UiTooltipMixin(){}
    @Mixin(net.minecraft.client.gui.components.Tooltip.class)
    public abstract static class Wrap {
        @WrapMethod(method="splitTooltip")
        private static List<net.minecraft.util.FormattedCharSequence> customcosmetics$wrap(Minecraft client,net.minecraft.network.chat.Component message,Operation<List<net.minecraft.util.FormattedCharSequence>> original){
            if(!(ClientUiAccess.screen(client) instanceof ThemedScreen))return original.call(client,message);
            return com.customcosmetics.gui.UiTooltips.split(client,message);
        }
    }
    @Mixin(GuiGraphicsExtractor.class)
    public abstract static class Legacy {
        @WrapMethod(method="tooltip(Lnet/minecraft/client/gui/Font;Ljava/util/List;IILnet/minecraft/client/gui/screens/inventory/tooltip/ClientTooltipPositioner;Lnet/minecraft/resources/Identifier;)V")
        private void smooth(Font font,List<ClientTooltipComponent> lines,int x,int y,ClientTooltipPositioner positioner,Identifier style,Operation<Void> original){
            if(!(ClientUiAccess.screen(Minecraft.getInstance()) instanceof ThemedScreen screen)){original.call(font,lines,x,y,positioner,style);return;}
            var graphics=(GuiGraphicsExtractor)(Object)this;graphics.pose().pushMatrix();
            try{original.call(font,lines,x,y,screen.beginTooltip(graphics,positioner),style);}
            finally{screen.endTooltip();graphics.pose().popMatrix();}
        }
    }
    @Mixin(GuiGraphicsExtractor.class)
    public abstract static class Modern {
        @WrapMethod(method="tooltip(Lnet/minecraft/client/gui/Font;Ljava/util/List;IILnet/minecraft/client/gui/screens/inventory/tooltip/ClientTooltipPositioner;Lnet/minecraft/resources/Identifier;Z)V")
        private void smooth(Font font,List<ClientTooltipComponent> lines,int x,int y,ClientTooltipPositioner positioner,Identifier style,boolean flag,Operation<Void> original){
            if(!(ClientUiAccess.screen(Minecraft.getInstance()) instanceof ThemedScreen screen)){original.call(font,lines,x,y,positioner,style,flag);return;}
            var graphics=(GuiGraphicsExtractor)(Object)this;graphics.pose().pushMatrix();
            try{original.call(font,lines,x,y,screen.beginTooltip(graphics,positioner),style,flag);}
            finally{screen.endTooltip();graphics.pose().popMatrix();}
        }
    }
}
