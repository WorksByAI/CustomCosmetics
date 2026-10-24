// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.compatibility;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class AccessoryRenderAccessTest {
    public static class Legacy {int tint,light,overlay;Object sprite,crumbling;public void submitModelPart(Object part,Object pose,Object type,int light,int overlay,Object sprite,int tint,Object crumbling){this.tint=tint;this.light=light;this.overlay=overlay;this.sprite=sprite;this.crumbling=crumbling;}}
    public static class Modern {int tint,light,overlay;Object uv;public void submitModelPart(Object part,Object pose,Object type,int light,int overlay,Object uv,int tint){this.tint=tint;this.light=light;this.overlay=overlay;this.uv=uv;}}
    @Test void oldAndNewSubmissionRetainTintAndLightWithoutAtlasMappingOrCrumbling(){
        var legacy=new Legacy();AccessoryRenderAccess.invoke(AccessoryRenderAccess.adapt(AccessoryRenderAccess.findSubmit(Legacy.class)),legacy,null,null,null,1234,5678,0xFF123456);assertEquals(0xFF123456,legacy.tint);assertEquals(1234,legacy.light);assertEquals(5678,legacy.overlay);assertNull(legacy.sprite);assertNull(legacy.crumbling);
        var modern=new Modern();AccessoryRenderAccess.invoke(AccessoryRenderAccess.adapt(AccessoryRenderAccess.findSubmit(Modern.class)),modern,null,null,null,1234,5678,0x50123456);assertEquals(0x50123456,modern.tint);assertEquals(1234,modern.light);assertEquals(5678,modern.overlay);assertNull(modern.uv);
    }
    @Test void unknownApiCannotSilentlyDropAVisiblePart(){assertThrows(IllegalStateException.class,()->AccessoryRenderAccess.findSubmit(String.class));}
}
