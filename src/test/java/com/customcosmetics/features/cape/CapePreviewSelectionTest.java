// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.features.cape;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class CapePreviewSelectionTest {
    private final UUID self=UUID.fromString("12345678-1234-4234-8234-123456789abc");
    @Test void previewAfterRestartUsesApprovedCapeWhenThereIsNoLocalDraft(){
        var approved=Map.of(self,"approved");
        assertEquals("approved",CapeVisibility.hash(self,self,true,true,true,null,approved));
        assertEquals(CapeVisibility.hash(self,self,true,true,false,null,approved),CapeVisibility.previewHash(self,true,null,approved));
    }
    @Test void localDraftStillTakesPriorityInPreviewWithoutLeakingOnline(){
        var approved=Map.of(self,"approved");
        assertEquals("draft",CapeVisibility.previewHash(self,true,"draft",approved));
        assertEquals("draft",CapeVisibility.hash(self,self,true,true,true,"draft",approved));
        assertEquals("approved",CapeVisibility.hash(self,self,true,true,false,"draft",approved));
    }
    @Test void offlinePreviewNeverFallsBackToAnApprovedCape(){
        var approved=Map.of(self,"approved");
        assertNull(CapeVisibility.previewHash(self,false,null,approved));
        assertNull(CapeVisibility.hash(self,self,false,true,true,null,approved));
        assertEquals("draft",CapeVisibility.previewHash(self,false,"draft",approved));
    }
    @Test void absentIdentityAndDisabledCapesKeepVanillaRendering(){
        assertNull(CapeVisibility.previewHash(null,true,null,Map.of()));
        assertNull(CapeVisibility.hash(self,self,true,false,true,null,Map.of(self,"approved")));
        assertNull(CapeVisibility.hash(null,self,true,true,true,null,Map.of()));
    }
}
