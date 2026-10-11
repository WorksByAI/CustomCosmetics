package com.customcosmetics.features.blocking;

import com.mojang.blaze3d.vertex.PoseStack;
import com.customcosmetics.config.ConfigManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BlockPoseTest {
    @AfterEach void resetAnimations() { ConfigManager.resetAnimations(); ConfigManager.swordBlockingStyle = "default"; }
    @Test void firstPersonStylesShareTheSameGuardAndMirrorTheirFullAttackAndRecovery() {
        for (var style : BlockingStyle.values()) {
            ConfigManager.swordBlockingStyle = style.value();
            var guard = new PoseStack(); BlockingPose.apply(guard, 1, 0, 0);
            for (float progress : new float[]{0, .1f, .25f, .5f, .8f, 1}) {
                var right = new PoseStack(); var left = new PoseStack();
                BlockingPose.apply(right, 1, progress, 0); display(right, 1);
                BlockingPose.apply(left, -1, progress, 0); display(left, -1);
                assertTrue(right.last().pose().isFinite()); assertTrue(left.last().pose().isFinite());
                for (float point : new float[]{-.35f, 0, .35f}) {
                    var r = new org.joml.Vector3f(point, point, 0).mulPosition(right.last().pose());
                    var l = new org.joml.Vector3f(point, point, 0).mulPosition(left.last().pose());
                    assertEquals(-r.x, l.x, .00001); assertEquals(r.y, l.y, .00001); assertEquals(r.z, l.z, .00001);
                }
            }
            for (float progress : new float[]{1, -1, 2, Float.NaN, Float.POSITIVE_INFINITY}) {
                var rest = new PoseStack(); BlockingPose.apply(rest, 1, progress, 1);
                assertTrue(guard.last().pose().equals(rest.last().pose(), .00001f));
            }
        }
    }
    @Test void jigsawSweepsDownwardWithoutPitchingEitherEndTowardOrAwayFromTheCamera() {
        ConfigManager.swordBlockingStyle = "jigsaw";
        for (int side : new int[]{-1,1}) {
            var rest = new PoseStack(); BlockingPose.apply(rest, side, 0, 0); display(rest, side);
            for (float phase : new float[]{.08f,.17f,.25f,.4f,.6f,.8f,.95f}) {
                var moving = new PoseStack(); BlockingPose.apply(moving, side, phase, 0); display(moving, side);
                for (var landmark : new org.joml.Vector3f[]{new org.joml.Vector3f(.35f,.35f,0),
                        new org.joml.Vector3f(-.35f,-.35f,0),new org.joml.Vector3f(.2f,-.1f,.02f)}) {
                    var a = new org.joml.Vector3f(landmark).mulPosition(rest.last().pose());
                    var b = new org.joml.Vector3f(landmark).mulPosition(moving.last().pose());
                    assertEquals(a.z,b.z,.00001,"Neither the blade nor the grip may seesaw in depth");
                }
                assertEquals(rest.last().pose().determinant3x3(),moving.last().pose().determinant3x3(),.00001);
                for (var axis : new org.joml.Vector3f[]{new org.joml.Vector3f(1,0,0),
                        new org.joml.Vector3f(0,1,0),new org.joml.Vector3f(0,0,1),new org.joml.Vector3f(1,1,0)}) {
                    assertEquals(new org.joml.Vector3f(axis).mulDirection(rest.last().pose()).length(),
                        new org.joml.Vector3f(axis).mulDirection(moving.last().pose()).length(),.00001,
                        "Flattening the swing must not stretch or shear the sword");
                }
            }
            var peak = new PoseStack(); BlockingPose.apply(peak,side,.25f,0); display(peak,side);
            assertTrue(screenY(peak,.35f)>screenY(rest,.35f)+.1f,"Jigsaw follows the downward stroke, not the rejected upward lift");
        }
    }
    @Test void jigsawRetainsDefaultsWristTravelInTheScreenPlane() {
        for (int side : new int[]{-1,1}) for (float phase : new float[]{0,.1f,.25f,.5f,.8f,1}) {
            ConfigManager.swordBlockingStyle = "default";
            var original = new PoseStack(); BlockingPose.apply(original,side,phase,0); display(original,side);
            ConfigManager.swordBlockingStyle = "jigsaw";
            var flat = new PoseStack(); BlockingPose.apply(flat,side,phase,0); display(flat,side);
            assertEquals(original.last().pose().m30(),flat.last().pose().m30(),.00001);
            assertEquals(original.last().pose().m31(),flat.last().pose().m31(),.00001);
            var originalBlade = new org.joml.Vector3f(1,1,0).mulDirection(original.last().pose());
            var flatBlade = new org.joml.Vector3f(1,1,0).mulDirection(flat.last().pose());
            var originalDirection = new org.joml.Vector2f(originalBlade.x,originalBlade.y).normalize();
            var flatDirection = new org.joml.Vector2f(flatBlade.x,flatBlade.y).normalize();
            float correction = (float) Math.toRadians(12) * (float) Math.sin(Math.sqrt(phase) * Math.PI) * side;
            float x = originalDirection.x, y = originalDirection.y;
            originalDirection.set(x * (float)Math.cos(correction) - y * (float)Math.sin(correction),
                x * (float)Math.sin(correction) + y * (float)Math.cos(correction));
            assertEquals(originalDirection.x,flatDirection.x,.00001);
            assertEquals(originalDirection.y,flatDirection.y,.00001);
        }
    }
    @Test void firstPersonStyleNeverChangesThirdPersonBlocking() {
        for (int side : new int[]{-1, 1}) {
            ConfigManager.swordBlockingStyle = "default";
            var original = new PoseStack(); BlockingPose.thirdPerson(original, side);
            ConfigManager.swordBlockingStyle = "jigsaw";
            var selected = new PoseStack(); BlockingPose.thirdPerson(selected, side);
            assertTrue(original.last().pose().equals(selected.last().pose(), 0));
        }
    }
    @Test void jigsawScalesScreenDisplacementWithoutAddingDepth() {
        ConfigManager.swordBlockingStyle = "jigsaw";
        for (int side : new int[]{-1, 1}) {
            ConfigManager.animationSwingZ = 1;
            var rest = new PoseStack(); var normal = new PoseStack();
            BlockingPose.apply(rest, side, 0, 0); BlockingPose.apply(normal, side, .3f, 0);
            for (double factor : new double[]{0, .5, 2}) {
                ConfigManager.animationSwingZ = factor;
                var scaled = new PoseStack(); BlockingPose.apply(scaled, side, .3f, 0);
                assertEquals(rest.last().pose().m32() + (normal.last().pose().m32() - rest.last().pose().m32()) * factor,
                    scaled.last().pose().m32(), .00001);
                assertTrue(new org.joml.Matrix3f(normal.last().pose()).equals(new org.joml.Matrix3f(scaled.last().pose()), .00001f));
            }
            ConfigManager.animationSwingZ = 1;
            ConfigManager.animationSwingX = 0; ConfigManager.animationSwingY = 2;
            var scaled = new PoseStack(); BlockingPose.apply(scaled,side,.3f,0);
            assertEquals(rest.last().pose().m30(),scaled.last().pose().m30(),.00001);
            assertEquals(rest.last().pose().m31()+(normal.last().pose().m31()-rest.last().pose().m31())*2,
                scaled.last().pose().m31(),.00001);
            assertEquals(rest.last().pose().m32(),scaled.last().pose().m32(),.00001);
            ConfigManager.resetAnimations();
        }
    }
    @Test void blockingSwingAxesScaleDisplacementWithoutChangingTheGuardOrRotationArc() {
        ConfigManager.resetAnimations();
        for (int side : new int[]{-1,1}) {
            var rest = new PoseStack(); BlockingPose.apply(rest, side, 0, 0);
            var normal = new PoseStack(); BlockingPose.apply(normal, side, .3f, 0);
            var a = rest.last().pose(); var b = normal.last().pose();
            assertTrue(Math.abs(a.m32() - b.m32()) > .001f, "The blocking arc must have a depth displacement to scale");
            for (double factor : new double[]{0, .5, 2}) {
                ConfigManager.animationSwingZ = factor;
                var scaled = new PoseStack(); BlockingPose.apply(scaled, side, .3f, 0);
                var c = scaled.last().pose();
                assertEquals(a.m32() + (b.m32() - a.m32()) * factor, c.m32(), .00001);
                assertEquals(b.m30(), c.m30(), .00001); assertEquals(b.m31(), c.m31(), .00001);
                assertTrue(new org.joml.Matrix3f(b).equals(new org.joml.Matrix3f(c), .00001f));
                var guard = new PoseStack(); BlockingPose.apply(guard, side, 0, 0);
                assertTrue(a.equals(guard.last().pose(), .00001f));
            }
            ConfigManager.animationSwingZ = 1; ConfigManager.animationSwingX = 0; ConfigManager.animationSwingY = 2;
            var scaled = new PoseStack(); BlockingPose.apply(scaled, side, .3f, 0);
            assertEquals(a.m30(), scaled.last().pose().m30(), .00001);
            assertEquals(a.m31() + (b.m31() - a.m31()) * 2, scaled.last().pose().m31(), .00001);
            ConfigManager.animationsEnabled = false;
            var disabled = new PoseStack(); BlockingPose.apply(disabled, side, .3f, 0);
            assertTrue(b.equals(disabled.last().pose(), .00001f));
            ConfigManager.resetAnimations();
        }
    }
    @Test void defaultGuardPlacesTheBladeAndCrossguardAtTheRequestedScreenHeight() {
        // Reference: blade in the lower half, with the crossguard visible above the bottom edge.
        // Project centered stock-model landmarks at the standard 70-degree hand FOV.
        for (int side : new int[] {-1, 1}) {
            var pose = new PoseStack(); BlockingPose.apply(pose, side); display(pose, side);
            float blade = screenY(pose, .35f), crossguard = screenY(pose, 0);
            assertTrue(blade >= .59f && blade <= .63f, "Blade is below the requested guard height: " + blade);
            assertTrue(crossguard >= .79f && crossguard <= .83f, "Crossguard is cropped too low: " + crossguard);
        }
    }
    @Test void pvpPoseMirrorsHandsAndRetainsTheResourcePackDisplayScale() {
        var right = new PoseStack(); BlockingPose.apply(right, 1); display(right, 1);
        for (int side : new int[] {-1, 1}) {
            var stack = new PoseStack();
            BlockingPose.apply(stack, side);
            display(stack, side);
            var matrix = stack.last().pose();
            assertEquals(right.last().pose().m30() * side, matrix.m30(), 0.00001f);
            assertEquals(right.last().pose().m31(), matrix.m31(), 0.00001f);
            assertEquals(right.last().pose().m32(), matrix.m32(), 0.00001f);
            assertEquals(.68f * .68f * .68f, Math.abs(matrix.determinant3x3()), 0.00001f);
            assertEquals(.68f, new org.joml.Vector3f(matrix.m00(), matrix.m01(), matrix.m02()).length(), 0.00001f);
        }
    }
    @Test void theBladeAndGripMirrorTogetherInsteadOfInvertingTheLeftHandSword() {
        for (float progress : new float[] {0, .2f, .5f, .8f, 1}) {
            var right = new PoseStack(); var left = new PoseStack();
            BlockingPose.apply(right, 1, progress, 0); display(right, 1);
            BlockingPose.apply(left, -1, progress, 0); display(left, -1);
            for (float point : new float[] {-.35f, .35f}) {
                var r = new org.joml.Vector3f(point, point, 0).mulPosition(right.last().pose());
                var l = new org.joml.Vector3f(point, point, 0).mulPosition(left.last().pose());
                assertEquals(-r.x, l.x, .00001);
                assertEquals(r.y, l.y, .00001);
                assertEquals(r.z, l.z, .00001);
            }
        }
    }
    @Test void blockhitKeepsTheGuardRaisedDuringModernAttackRecovery() {
        var idle = new PoseStack(); var swing = new PoseStack(); var equip = new PoseStack();
        BlockingPose.apply(idle, 1, 0, 0); BlockingPose.apply(swing, 1, .4f, 0); BlockingPose.apply(equip, 1, 0, 1);
        assertFalse(idle.last().pose().equals(swing.last().pose(), .00001f));
        assertEquals(1, swing.last().pose().determinant3x3(), .00001);
        assertTrue(idle.last().pose().equals(equip.last().pose(), .00001f));
        var recoveringSwing = new PoseStack(); BlockingPose.apply(recoveringSwing, 1, .4f, 1);
        assertTrue(swing.last().pose().equals(recoveringSwing.last().pose(), .00001f));
        var third = new PoseStack(); BlockingPose.thirdPerson(third, 1);
        assertEquals(1, third.last().pose().determinant3x3(), .00001);
        assertFalse(third.last().pose().equals(new org.joml.Matrix4f(), .00001f));
    }

    @Test void blockhitReturnsToItsGuardAndRejectsInvalidProgress() {
        var guard = new PoseStack(); BlockingPose.apply(guard, 1, 0, 0);
        for (float progress : new float[] {1, -1, 2, Float.NaN, Float.POSITIVE_INFINITY}) {
            var pose = new PoseStack(); BlockingPose.apply(pose, 1, progress, 0);
            assertTrue(guard.last().pose().equals(pose.last().pose(), .00001f));
        }
        // The blade folds down, while the wrist stays near the guard's origin.
        for (float progress : new float[] {.1f, .25f, .5f, .75f}) {
            var pose = new PoseStack(); BlockingPose.apply(pose, 1, progress, 0);
            var matrix = pose.last().pose();
            assertTrue(matrix.isFinite());
            float distance = new org.joml.Vector3f(matrix.m30(), matrix.m31(), matrix.m32())
                .distance(guard.last().pose().m30(), guard.last().pose().m31(), guard.last().pose().m32());
            assertTrue(distance < .35f, "blockhit must pivot near the wrist, not displace the whole arm");
        }
    }
    private static void display(PoseStack pose, int side) {
        // The stock handheld display still runs after our pose, including left-hand conversion.
        pose.mulPose(new org.joml.Matrix4f().translation(1.13f / 16 * side, 3.2f / 16, 1.13f / 16)
            .rotateY((float) Math.toRadians(-90)).rotateZ((float) Math.toRadians(25)).scale(.68f));
    }
    private static float screenY(PoseStack pose, float landmark) {
        var point = new org.joml.Vector3f(landmark, landmark, 0).mulPosition(pose.last().pose());
        return (float) ((1 - point.y / (-point.z * Math.tan(Math.toRadians(35)))) / 2);
    }
}
