// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.mixin;

import com.google.common.collect.ImmutableMap;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.renderer.ShaderManager;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ShaderManager.class)
final class GradientTextCoverageMixin {
    @Unique private static final Identifier CCOS_GRADIENT =
        Identifier.fromNamespaceAndPath("meowdding-lib", "shaders/text/gradient.fsh");

    @WrapOperation(method = "loadShader", at = @At(value = "INVOKE",
        target = "Lcom/google/common/collect/ImmutableMap$Builder;put(Ljava/lang/Object;Ljava/lang/Object;)Lcom/google/common/collect/ImmutableMap$Builder;"))
    private static ImmutableMap.Builder<?, ?> ccos$keepGradientGlyphCoverage(ImmutableMap.Builder<?, ?> builder,
            Object key, Object source, Operation<ImmutableMap.Builder<?, ?>> original, Identifier file) {
        if (CCOS_GRADIENT.equals(file) && source instanceof String shader) {
            // The gradient should change RGB while retaining the glyph and vertex alpha.
            source = shader.replace(".rgb, 1) * vertexColor;",
                ".rgb, 1) * vertexColor;\n        finalColor.a = color.a;");
        }
        return original.call(builder, key, source);
    }
}
