// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.compatibility;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class MiniMeRenderAccessTest {
    public static class RenderType{}
    public interface Legacy {void submitModel(Object model,Object state,Object pose,RenderType material,int light,int overlay,int outline,Object crumbling);}
    public interface Modern {void submitModel(Object model,Object state,Object pose,RenderType material,int light,int overlay,int outline);}
    @Test void supportedRenderersSelectTheModelAndSnapshotOverload(){
        assertEquals(8,MiniMeRenderAccess.find(Legacy.class).getParameterCount());
        assertEquals(7,MiniMeRenderAccess.find(Modern.class).getParameterCount());
        assertThrows(IllegalStateException.class,()->MiniMeRenderAccess.find(Runnable.class));
    }
    @Test void cachedHandlesPassTheSnapshotAndPrimitiveRenderArgumentsOnBothVersions(){
        Object model=new Object(),state=new Object(),pose=new Object();var material=new RenderType();int[] calls={0};
        Legacy legacy=(actualModel,actualState,actualPose,type,light,overlay,outline,crumbling)->{
            assertSame(model,actualModel);assertSame(state,actualState);assertSame(pose,actualPose);assertSame(material,type);
            assertEquals(1234,light);assertEquals(5678,overlay);assertEquals(0,outline);assertNull(crumbling);calls[0]++;
        };
        Modern modern=(actualModel,actualState,actualPose,type,light,overlay,outline)->{
            assertSame(model,actualModel);assertSame(state,actualState);assertSame(pose,actualPose);assertSame(material,type);
            assertEquals(1234,light);assertEquals(5678,overlay);assertEquals(0,outline);calls[0]++;
        };
        MiniMeRenderAccess.submit(MiniMeRenderAccess.adapt(MiniMeRenderAccess.find(Legacy.class)),legacy,model,state,pose,material,1234,5678);
        MiniMeRenderAccess.submit(MiniMeRenderAccess.adapt(MiniMeRenderAccess.find(Modern.class)),modern,model,state,pose,material,1234,5678);
        assertEquals(2,calls[0]);
    }
}
