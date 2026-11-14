// SPDX-License-Identifier: LGPL-3.0-or-later
package com.customcosmetics.features.bobby.v261.ext;

import net.minecraft.world.level.lighting.LevelLightEngine;

public interface LevelLightEngineExt {
    void bobby_enabledColumn(long pos);
    void bobby_disableColumn(long pos);

    static LevelLightEngineExt get(LevelLightEngine provider) {
        return (provider instanceof LevelLightEngineExt) ? (LevelLightEngineExt) provider : null;
    }
}
