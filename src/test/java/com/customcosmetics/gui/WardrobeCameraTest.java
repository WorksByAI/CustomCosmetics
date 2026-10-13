// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.gui;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class WardrobeCameraTest {
    @Test void draggingCanInspectTheSideAndFullBackWithoutFlippingThePlayer(){var camera=WardrobeCamera.DEFAULT.drag(200,200);assertEquals(162,camera.yaw(),1e-4);assertEquals(35,camera.pitch());assertEquals(1,camera.zoom());camera=camera.drag(600,-300);assertTrue(Math.abs(camera.yaw())<=180);assertEquals(-35,camera.pitch());}
    @Test void zoomIsBoundedAndResetRestoresTheThreeQuarterView(){assertEquals(1.8f,WardrobeCamera.DEFAULT.zoom(100).zoom());assertEquals(.65f,WardrobeCamera.DEFAULT.zoom(-100).zoom());assertEquals(WardrobeCamera.DEFAULT,new WardrobeCamera(Float.NaN,Float.NaN,Float.NaN));}
}
