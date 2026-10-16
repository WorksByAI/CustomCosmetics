// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.features.accessories;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MiniMePetTest {
    @Test void companionSelectionPersistsAndUsesExistingOnlineOfflineRules(){
        var style=AccessoryCatalog.style("pet","mini_me");assertEquals("Mini-Me",style.label());assertTrue(MiniMePet.selected(style));
        var value=AccessoriesSettings.NONE.with("pet",AccessoriesSettings.NONE.part("pet").enabled(true).style(style.id()).scale(1.5).side("left"));
        assertEquals(value,AccessoriesSettings.parse(value.json()));
        var self=java.util.UUID.randomUUID();var other=java.util.UUID.randomUUID();
        assertEquals(value,AccessoriesSettings.choose(self,self,true,value,null));
        assertEquals(value,AccessoriesSettings.choose(self,other,false,null,value));
        assertEquals(AccessoriesSettings.NONE,AccessoriesSettings.choose(self,other,true,null,value));
        assertFalse(value.json().toString().contains("skin"));
    }
    @Test void bothShouldersClearTheOwnersHeadAndKeepTheSeatedHipsAtShoulderHeight(){
        for(String side:new String[]{"left","right"})for(double size:new double[]{.5,1,2})for(int age=0;age<300;age++){
            var p=MiniMePet.mount(size,age,side);assertTrue(Math.abs(p.x())-p.scale()*.5>=.6-1e-9);
            assertEquals(side.equals("left"),p.x()>0);
            double hips=p.y()+p.scale()*.75;assertTrue(hips>=-.061&&hips<=-.019);
            assertTrue(p.z()<0,"Stay in front of wings and capes");
        }
        assertEquals(.64,MiniMePet.scale(2),1e-6);
    }
    @Test void oldAndMalformedSideValuesUseRightWithoutChangingOtherSlots(){
        var old=com.google.gson.JsonParser.parseString("{\"pet\":{\"enabled\":true,\"style\":\"cat\"},\"ears\":{\"enabled\":true,\"side\":\"left\"}}");
        assertEquals("right",AccessoriesSettings.parse(old).part("pet").side());
        assertEquals("right",AccessoriesSettings.parse(old).part("ears").side());
        assertEquals("right",AccessoriesSettings.NONE.part("pet").side("../../unsafe").side());
    }
}
