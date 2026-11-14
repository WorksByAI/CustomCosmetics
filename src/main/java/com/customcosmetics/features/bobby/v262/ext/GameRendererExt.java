// SPDX-License-Identifier: LGPL-3.0-or-later
package com.customcosmetics.features.bobby.v262.ext;

import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.fog.FogRenderer;

public interface GameRendererExt {
    FogRenderer bobby_getSkyFogRenderer();

    static GameRendererExt get(GameRenderer view) {
        return (GameRendererExt) view;
    }
}
