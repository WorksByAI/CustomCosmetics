// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.mixin;

import com.customcosmetics.features.pack.TextColorOverrides;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.renderer.ShaderManager;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ShaderManager.class)
public class TextColorOverridesMixin {
    @ModifyExpressionValue(method = "loadShader", at = {
        @At(value = "INVOKE", target = "Lorg/apache/commons/io/IOUtils;toString(Ljava/io/Reader;)Ljava/lang/String;"),
        @At(value = "INVOKE", target = "Lnet/minecraft/server/packs/resources/Resource;readAllAsString()Ljava/lang/String;")
    }, require = 1)
    private static String ccos$replaceTextColors(String source, @Local(argsOnly = true) Identifier location) {
        return TextColorOverrides.apply(location, source);
    }
}
