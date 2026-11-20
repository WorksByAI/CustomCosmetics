// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.gui;

import com.customcosmetics.config.ConfigManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.screens.inventory.tooltip.*;
import org.joml.Vector2i;
import org.joml.Vector2ic;

/** Reuses native placement and bounds, then aligns eased movement to physical pixels. */
final class UiTooltipPositioner implements ClientTooltipPositioner {
    private final ThemedScreen screen;
    private final TooltipMotion motion=new TooltipMotion();
    private final Vector2i result=new Vector2i();
    private ClientTooltipPositioner nativePositioner;
    private GuiGraphicsExtractor graphics;
    UiTooltipPositioner(ThemedScreen screen){this.screen=screen;}
    ClientTooltipPositioner begin(GuiGraphicsExtractor graphics,ClientTooltipPositioner original){this.graphics=graphics;nativePositioner=original;return this;}
    void end(){graphics=null;nativePositioner=null;}
    @Override public Vector2ic positionTooltip(int width,int height,int mx,int my,int tooltipWidth,int tooltipHeight){
        Object owner=screen.getFocused();
        for(var child:screen.children())if(child.isMouseOver(mx,my)){owner=child;break;}
        int offsetX = screen.widgetOffsetX(), offsetY = screen.widgetOffsetY();
        var positioner = nativePositioner;
        if ((offsetX != 0 || offsetY != 0) && owner instanceof AbstractWidget widget) {
            var bounds = new ScreenRectangle(widget.getX() + offsetX, widget.getY() + offsetY,
                widget.getWidth(), widget.getHeight());
            if (positioner instanceof MenuTooltipPositioner) positioner = new MenuTooltipPositioner(bounds);
            else if (positioner instanceof BelowOrAboveWidgetTooltipPositioner) {
                positioner = new BelowOrAboveWidgetTooltipPositioner(bounds);
            }
        }
        int mouseX = mx + offsetX, mouseY = my + offsetY;
        var target=positioner.positionTooltip(width,height,mouseX,mouseY,tooltipWidth,tooltipHeight);
        var client=Minecraft.getInstance();var window=client.getWindow();double pixels=window.getGuiScale();
        double tx=target.x(),ty=target.y(),rawX=client.mouseHandler.getScaledXPos(window),rawY=client.mouseHandler.getScaledYPos(window);
        if(positioner instanceof DefaultTooltipPositioner||positioner instanceof MenuTooltipPositioner){
            if(Math.abs(rawX-mouseX)<1)tx+=rawX-mouseX;
            if(positioner instanceof DefaultTooltipPositioner&&Math.abs(rawY-mouseY)<1)ty+=rawY-mouseY;
        }
        motion.update(owner,tx,ty,ConfigManager.uiAnimationsEnabled,System.nanoTime());
        double x=Math.clamp(motion.x(pixels),4,Math.max(4,width-tooltipWidth-4));
        double y=Math.clamp(motion.y(pixels),4,Math.max(4,height-tooltipHeight-4));
        result.set((int)Math.floor(x),(int)Math.floor(y));
        graphics.pose().translate((float)(x-result.x),(float)(y-result.y));
        UiMotionTrail.edge(graphics,result.x-3,result.y-3,tooltipWidth+6,tooltipHeight+6,4,
            PinkUi.MUTED,motion.travelX(),motion.travelY());
        return result;
    }
}
