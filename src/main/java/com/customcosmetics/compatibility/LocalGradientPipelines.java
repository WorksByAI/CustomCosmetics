// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.compatibility;

import com.customcosmetics.client.CustomCosmeticsClient;
import net.minecraft.client.gui.Font;
import net.minecraft.resources.Identifier;

import java.util.IdentityHashMap;

public final class LocalGradientPipelines {
    public static final Identifier GRADIENT = Identifier.fromNamespaceAndPath("meowdding-lib", "text/gradient");
    private static final IdentityHashMap<Object, Object> GRAYSCALE = new IdentityHashMap<>();

    private LocalGradientPipelines() {}

    public static Object grayscale(Object glyph, Object original) {
        var cached = GRAYSCALE.get(original);
        if (cached != null) return cached;
        if (GRAYSCALE.size() >= 64) GRAYSCALE.clear();
        var corrected = findGrayscale(glyph, original);
        GRAYSCALE.put(original, corrected);
        return corrected;
    }

    private static Object findGrayscale(Object glyph, Object original) {
        try {
            // Meowdding's GUI selection requests the RGBA variant even for an R8 atlas.
            var holder = Class.forName("me.owdding.lib.accessor.FontPipelineHolder");
            if (!holder.isInstance(glyph)) return original;
            var shader = holder.getMethod("meowddinglib$getPipeline").invoke(glyph);
            if (shader == null) return original;
            var shaderType = Class.forName("me.owdding.lib.rendering.text.TextShader");
            var corrected = shaderType.getMethod("getPipelines", Font.DisplayMode.class, boolean.class)
                .invoke(shader, null, true);
            // The RenderPearl version returns separate glyph and effect pipelines.
            if (corrected != null && corrected.getClass().getName().equals("kotlin.Pair"))
                corrected = corrected.getClass().getMethod("getFirst").invoke(corrected);
            if (original.getClass().isInstance(corrected)) return corrected;
        } catch (ReflectiveOperationException | LinkageError error) {
            CustomCosmeticsClient.LOGGER.debug("Could not select the grayscale gradient text pipeline", error);
        }
        return original;
    }
}
