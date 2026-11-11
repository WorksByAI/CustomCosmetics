// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.mixin;
import com.customcosmetics.compatibility.UiFontAccess;
import com.customcosmetics.gui.UiFonts;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GlyphSource;
import net.minecraft.network.chat.FontDescription;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
@Mixin(Font.class)
public abstract class UiFontAccessor implements UiFontAccess {
    @Shadow @Final private Font.Provider provider;
    @Override public Font.Provider customcosmetics$provider(){return provider;}

    @ModifyReturnValue(method = "getGlyphSource", at = @At("RETURN"))
    private GlyphSource ccos$localGameFont(GlyphSource original, FontDescription description) {
        return UiFonts.gameSource(description, original);
    }
}
