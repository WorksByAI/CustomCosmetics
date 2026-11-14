// SPDX-License-Identifier: LGPL-3.0-only
package com.customcosmetics.features.motionblur;

/** Settings used by Natural Motion Blur 1.4.5's renderers. */
public final class BlurSettings {
    public enum BlurAlgorithm {
        VELOCITY_BASED("Velocity Based"), FRAME_BLENDING("Frame Blending"), HYBRID_BLENDING("Hybrid Blending"),
        ACCUMULATION_MAX("Accumulation Max"), ACCUMULATION_MIX("Accumulation Mix");

        public final String label;
        BlurAlgorithm(String label) { this.label = label; }
    }

    public enum BlurProfile { UNIFORM, WEIGHTED }

    public boolean enabled;
    public boolean refreshRateScaling = true;
    public float motionBlurStrength = 1;
    public transient boolean recordingOverlayEnabled;
    public int recordingOverlayTargetFPS = 60;
    public BlurAlgorithm blurAlgorithm = BlurAlgorithm.VELOCITY_BASED;
    public BlurProfile blurProfile = BlurProfile.UNIFORM;

    public boolean usesVelocityBlur() {
        return blurAlgorithm == BlurAlgorithm.VELOCITY_BASED || blurAlgorithm == BlurAlgorithm.HYBRID_BLENDING;
    }

    public boolean allowsRefreshRateScaling() { return blurAlgorithm == BlurAlgorithm.VELOCITY_BASED; }
    public float getEffectiveMotionBlurStrength() { return motionBlurStrength; }
}
