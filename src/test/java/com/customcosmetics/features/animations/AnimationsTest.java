package com.customcosmetics.features.animations;

import com.customcosmetics.config.ConfigManager;
import com.mojang.blaze3d.vertex.PoseStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AnimationsTest {
    @AfterEach void reset() { ConfigManager.resetAnimations(); }
    @Test void theFirstFrameAndFinalRecoveryMatchTheNativeInterpolationUsedByNoamm() {
        var clock = new SwingTimeline(); clock.request(); clock.tick(6);
        assertEquals(0, clock.progress(.5f), "The first native update starts at zero, without predicting the next tick");
        for (int tick = 0; tick < 6; tick++) clock.tick(6);
        assertEquals(5f / 6, clock.progress(0), .00001, "The last frame must retain the previous swing snapshot");
        assertEquals(11f / 12, clock.progress(.5f), .00001, "Recovery must interpolate across the wrap instead of snapping to idle");
    }
    @Test void noammClampsTheFinalDurationAfterApplyingSpeedEvenWithVeryHighHaste() {
        assertEquals(1, ViewModelAnimations.duration(-4, -2));
    }
    @Test void renderClockAcceptsANewObservedSwingAfterTheDownswingAndDoesNotQueueEarlyRequests() {
        var clock = new SwingTimeline();
        clock.request(); clock.tick(6); assertEquals(0, clock.progress(.5f));
        assertFalse(clock.request()); clock.tick(6); assertEquals(1f / 6, clock.progress(1), .00001);
        clock.tick(6); assertEquals(2f / 6, clock.progress(1), .00001);
        assertFalse(clock.request()); clock.tick(6); assertEquals(.5f, clock.progress(1));
        assertTrue(clock.request()); clock.tick(6);
        assertEquals(.75f, clock.progress(.5f), .00001); // Native wrap finishes recovery during the restart tick.
        for (int i = 1; i < 6; i++) { clock.tick(6); assertEquals(i / 6f, clock.progress(1), .00001); }
        clock.tick(6); assertEquals(11f / 12, clock.progress(.5f), .00001);
        clock.tick(6); assertEquals(0, clock.progress(1));
    }
    @Test void negativeSpeedKeepsItsLongerDownswingGateEvenWithAnObservedSwingEveryTick() {
        for (double speed : new double[]{0, -.5, -1, -2}) {
            int duration = ViewModelAnimations.duration(6, speed);
            int gate = Math.max(1, duration / 2);
            var clock = new SwingTimeline(); clock.request(); clock.tick(duration);
            for (int elapsed = 1; elapsed <= gate; elapsed++) {
                assertFalse(clock.request()); clock.tick(duration);
                assertEquals(elapsed / (float)duration, clock.progress(1), .00001, "speed=" + speed + " elapsed=" + elapsed);
            }
            assertTrue(clock.request()); clock.tick(duration); assertEquals(1, clock.progress(1), .00001);
        }
    }
    @Test void explicitObservationDoesNotMissOneTickVanillaSwingsAndSwitchingTimingOffClearsState() {
        var clock = new SwingTimeline(); clock.request(); clock.tick(6);
        clock.tick(6); assertEquals(1f / 6, clock.progress(1), .00001);
        clock.request(); clock.clear(); clock.tick(6); assertEquals(0, clock.progress(0));
        ConfigManager.resetAnimations(); assertFalse(ViewModelAnimations.customTiming());
        ConfigManager.animationIgnoreHaste = true; assertTrue(ViewModelAnimations.customTiming());
        ConfigManager.animationsEnabled = false; assertFalse(ViewModelAnimations.customTiming());
        ConfigManager.resetAnimations(); ConfigManager.animationSwingSpeed = -.5;
        assertTrue(ViewModelAnimations.customTiming());
    }
    @Test void speedAndEffectsUseNoammDurationAndNeverReachZero() {
        assertEquals(6, ViewModelAnimations.duration(6, 0));
        assertEquals(44, ViewModelAnimations.duration(6, -2));
        assertEquals(2, ViewModelAnimations.duration(6, 1));
        assertEquals(1, ViewModelAnimations.duration(-10, 1));
        assertEquals(4, ViewModelAnimations.duration(4, 0));
    }
    @Test void ignoringHasteUsesTheClassicSixTickBaselineRegardlessOfItemAndEffects() {
        for (int itemDuration : new int[]{1, 6, 12, 20}) {
            for (int digSpeed : new int[]{0, 2, 4, 10}) {
                for (int fatigue : new int[]{0, 2, 4}) {
                    int base = ViewModelAnimations.effectDuration(itemDuration, digSpeed, fatigue, true);
                    assertEquals(6, base);
                    assertEquals(6, ViewModelAnimations.duration(base, 0));
                    assertEquals(9, ViewModelAnimations.duration(base, -.5));
                    assertEquals(44, ViewModelAnimations.duration(base, -2));
                    assertEquals(2, ViewModelAnimations.duration(base, 1));
                }
            }
        }
    }
    @Test void leavingIgnoreHasteOffPreservesModernItemTimingAndEffectPriority() {
        assertEquals(12, ViewModelAnimations.effectDuration(12, 0, 0, false));
        assertEquals(10, ViewModelAnimations.effectDuration(12, 2, 0, false));
        assertEquals(16, ViewModelAnimations.effectDuration(12, 0, 2, false));
        assertEquals(10, ViewModelAnimations.effectDuration(12, 2, 2, false));
        assertEquals(4, ViewModelAnimations.effectDuration(6, 2, 0, false));
        assertEquals(10, ViewModelAnimations.effectDuration(6, 0, 2, false));
        assertEquals(1, ViewModelAnimations.effectDuration(6, 10, 0, false));
    }
    @Test void portedNoammDurationKeepsTheUpstreamEffectPriorityAndFinalClamping() {
        assertEquals(6, ViewModelAnimations.noammDuration(0, 0, false, 0));
        assertEquals(4, ViewModelAnimations.noammDuration(2, 2, false, 0));
        assertEquals(10, ViewModelAnimations.noammDuration(0, 2, false, 0));
        assertEquals(1, ViewModelAnimations.noammDuration(10, 0, false, -2));
        for (int haste : new int[]{0, 2, 6, 10}) for (int fatigue : new int[]{0, 2, 5}) {
            assertEquals(6, ViewModelAnimations.noammDuration(haste, fatigue, true, 0));
            assertEquals(44, ViewModelAnimations.noammDuration(haste, fatigue, true, -2));
        }
    }
    @Test void noammsEmptyMainHandGuardPreservesVanillaTimingForAnOffhandOnlyItem() {
        ConfigManager.animationIgnoreHaste = true;
        assertTrue(ViewModelAnimations.customTiming(false)); assertFalse(ViewModelAnimations.customTiming(true));
        ConfigManager.animationIgnoreHaste = false; ConfigManager.animationSwingSpeed = -2;
        assertTrue(ViewModelAnimations.customTiming(false)); assertFalse(ViewModelAnimations.customTiming(true));
        ConfigManager.animationsEnabled = false; assertFalse(ViewModelAnimations.customTiming(false));
    }
    @Test void terminatorOnlyAndFeatureSwitchesAreIndependent() {
        ConfigManager.animationDisableSwing = true; ConfigManager.animationTerminatorOnly = true;
        assertTrue(ViewModelAnimations.suppressSwing("TERMINATOR"));
        assertFalse(ViewModelAnimations.suppressSwing("HYPERION"));
        assertFalse(ViewModelAnimations.suppressSwing(""));
        ConfigManager.animationTerminatorOnly = false; assertTrue(ViewModelAnimations.suppressSwing(""));
        ConfigManager.animationDisableEquip = true; ConfigManager.animationDisableHandMove = true;
        assertEquals(0, ViewModelAnimations.equip(.7f)); assertFalse(ViewModelAnimations.handMovement());
        ConfigManager.animationsEnabled = false;
        assertFalse(ViewModelAnimations.suppressSwing("TERMINATOR"));
        assertEquals(.7f, ViewModelAnimations.equip(.7f)); assertTrue(ViewModelAnimations.handMovement());
    }
    @Test void leftHandOffsetsMirrorAndRotationScaleDoNotLeakOutsideThePose() {
        ConfigManager.animationX = .3; ConfigManager.animationY = .2; ConfigManager.animationZ = -.4;
        ConfigManager.animationItemScale = 1.3; ConfigManager.animationRotationY = 20;
        for (int side : new int[]{1, -1}) {
            var pose = new PoseStack(); pose.pushPose();
            ViewModelAnimations.position(pose, side); ViewModelAnimations.model(pose, side);
            assertEquals(.3 * side, pose.last().pose().m30(), .00001);
            assertEquals(.2, pose.last().pose().m31(), .00001);
            assertEquals(Math.pow(1.3, 3), pose.last().pose().determinant3x3(), .00001);
            pose.popPose(); assertTrue(pose.last().pose().equals(new org.joml.Matrix4f(), .00001f));
        }
    }
    @Test void individualSwingAxesCanBeDisabledAndReset() {
        ConfigManager.animationSwingX = 0; ConfigManager.animationSwingY = 2;
        var pose = new PoseStack(); ViewModelAnimations.swingTranslation(pose, .3f, .2f, .1f);
        assertEquals(0, pose.last().pose().m30()); assertEquals(.4, pose.last().pose().m31(), .00001);
        ConfigManager.resetAnimations(); assertEquals(1, ConfigManager.animationSwingX);
        assertEquals(1, ConfigManager.animationItemScale); assertEquals(0, ConfigManager.animationX);
        assertFalse(ConfigManager.animationDisableSwing);
    }
}
