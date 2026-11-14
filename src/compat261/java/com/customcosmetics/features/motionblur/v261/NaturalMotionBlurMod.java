// SPDX-License-Identifier: LGPL-3.0-only
package com.customcosmetics.features.motionblur.v261;

import com.customcosmetics.features.motionblur.v261.recording.RecordingShaderManager;

public final class NaturalMotionBlurMod implements net.fabricmc.api.ClientModInitializer {
    public static final String ID = "csb_motionblur_261";

    @Override public void onInitializeClient() {
        Runtime.getRuntime().addShutdownHook(new Thread(RecordingShaderManager::destroy,
            "csb-spout-cleanup"));
    }
}
