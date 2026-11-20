// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.compatibility;

import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.renderer.feature.phase.SimpleFeatureRenderPhase;

public interface LegacyNametagRender263 {
    interface Collection {
        SimpleFeatureRenderPhase csb$legacyNametagPhase();
    }

    interface Frame {
        void csb$renderLegacyNametags(RenderTarget target);
    }
}
