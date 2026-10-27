// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import com.customcosmetics.compatibility.RepositoryLinks;
import java.net.URI;

/** Official, unmodified white GitHub mark used only to link to this project's source. */
public final class RepositoryButton extends PinkUi.Action {
    public static final URI REPOSITORY = URI.create("https://github.com/WorksByAI/CustomCosmetics");
    private static final Identifier ICON = Identifier.fromNamespaceAndPath("customcosmetics", "textures/gui/github.png");

    public RepositoryButton(int x, int y) {
        super(x, y, 30, 30, "GitHub source code", false, () -> RepositoryLinks.openUri(REPOSITORY));
        setTooltip(Tooltip.create(Component.literal("GitHub · CustomCosmetics source")));
    }

    @Override protected void extractWidgetRenderState(GuiGraphicsExtractor g, int mx, int my, float dt) {
        boolean effect=beginHoverTransform(g,mx,my);
        try {
        float over=hover.value(isMouseOver(mx,my)?1:0,com.customcosmetics.config.ConfigManager.uiAnimationsEnabled);
        PinkUi.outline(g,getX(),getY(),width,height,4,isFocused()?PinkUi.PINK:PinkUi.BORDER,
            PinkUi.mix(0xFF252B31,0xFF3A444D,over));
        // Preserve the official mark's 294:288 aspect ratio and original white color.
        g.pose().pushMatrix();
        g.pose().translate(getX() + 5f, getY() + 5f);
        g.pose().scale(20f / 294f, 20f / 294f);
        g.blit(ICON, 0, 0, 294, 288, 0f, 1f, 0f, 1f);
        g.pose().popMatrix();
        } finally {if(effect)g.pose().popMatrix();}
    }
}
