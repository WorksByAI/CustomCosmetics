// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.mixin;
import com.customcosmetics.compatibility.UiFontAccess;
import net.minecraft.client.gui.Font;
import org.spongepowered.asm.mixin.*;
@Mixin(Font.class)
public abstract class UiFontAccessor implements UiFontAccess {
    @Shadow @Final private Font.Provider provider;
    @Override public Font.Provider customcosmetics$provider(){return provider;}
}
