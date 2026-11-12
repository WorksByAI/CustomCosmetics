// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.mixin;

import com.customcosmetics.gui.UiFonts;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.gui.font.providers.TrueTypeGlyphProviderDefinition;
import net.minecraft.server.packs.resources.ResourceManager;
import org.lwjgl.PointerBuffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.nio.ByteBuffer;

@Mixin(TrueTypeGlyphProviderDefinition.class)
public abstract class LocalFontFaceMixin {
    @WrapOperation(method = "load", at = @At(value = "INVOKE",
        target = "Lorg/lwjgl/util/freetype/FreeType;FT_New_Memory_Face(JLjava/nio/ByteBuffer;JLorg/lwjgl/PointerBuffer;)I"))
    private int customcosmetics$loadFace(long library, ByteBuffer data, long defaultIndex, PointerBuffer face,
                                        Operation<Integer> original, ResourceManager resources) {
        return original.call(library, data, UiFonts.localFaceIndex(resources, defaultIndex), face);
    }
}
