// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.features.blocking;
import com.customcosmetics.config.ConfigManager;
import com.mojang.blaze3d.vertex.PoseStack;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

class BlockingAdjustmentTest {
    @AfterEach void reset(){BlockingAdjustment.reset();}
    @Test void translationsAreCameraRelativeAndRotationsKeepTheGuardAnchorFixed(){
        BlockingAdjustment.reset();var base=new PoseStack();BlockingPose.apply(base,1,0,0);
        ConfigManager.blockingX=.1;ConfigManager.blockingY=.2;ConfigManager.blockingZ=.3;
        var moved=new PoseStack();BlockingPose.apply(moved,1,0,0);
        assertEquals(.1,moved.last().pose().m30()-base.last().pose().m30(),.00001);
        assertEquals(.2,moved.last().pose().m31()-base.last().pose().m31(),.00001);
        assertEquals(.3,moved.last().pose().m32()-base.last().pose().m32(),.00001);
        ConfigManager.blockingRotationX=35;ConfigManager.blockingRotationY=-15;ConfigManager.blockingRotationZ=20;
        var rotated=new PoseStack();BlockingPose.apply(rotated,1,0,0);
        assertNotEquals(moved.last().pose().m00(),rotated.last().pose().m00());
        assertEquals(moved.last().pose().m30(),rotated.last().pose().m30());
        assertEquals(0,BlockingAdjustment.bounded(Double.NaN,2));assertEquals(2,BlockingAdjustment.bounded(999,2));
    }
}
