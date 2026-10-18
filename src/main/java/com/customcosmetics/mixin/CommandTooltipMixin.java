// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.mixin;

import com.customcosmetics.features.chat.CommandTooltips;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Style;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;

@Mixin(GuiGraphicsExtractor.class)
public abstract class CommandTooltipMixin {
    @ModifyVariable(method="componentHoverEffect",at=@At("HEAD"),argsOnly=true)
    private Style customcosmetics$commandPreview(Style style){return CommandTooltips.enrich(style);}
}
