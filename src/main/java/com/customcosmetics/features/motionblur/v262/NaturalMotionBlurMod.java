// SPDX-License-Identifier: LGPL-3.0-only
package com.customcosmetics.features.motionblur.v262;

import com.customcosmetics.features.motionblur.v262.recording.RecordingShaderManager;

public final class NaturalMotionBlurMod implements net.fabricmc.api.ClientModInitializer {
    public static final String ID = "csb_motionblur_262";

    @Override public void onInitializeClient() {
        Runtime.getRuntime().addShutdownHook(new Thread(RecordingShaderManager::destroyFromAnyThread,
            "csb-spout-cleanup"));
    }
}
