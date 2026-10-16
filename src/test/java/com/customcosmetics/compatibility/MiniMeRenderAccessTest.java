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
}
