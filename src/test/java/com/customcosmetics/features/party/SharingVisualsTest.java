package com.customcosmetics.features.party;

import com.customcosmetics.config.ConfigManager;
import org.junit.jupiter.api.*;
import java.util.*;
import java.lang.reflect.*;
import static org.junit.jupiter.api.Assertions.*;

class SharingVisualsTest {
    private final Map<Field,Object> before=new HashMap<>();
    @BeforeEach void snapshot()throws Exception{for(var field:ConfigManager.class.getFields())if(Modifier.isStatic(field.getModifiers()))before.put(field,field.get(null));}
    @AfterEach void restore()throws Exception{for(var entry:before.entrySet())entry.getKey().set(null,entry.getValue());}
    @Test void overlappingAudiencesSelectExactlyOneAuthorizedSource(){
        assertEquals(SharingVisuals.Source.NONE,SharingVisuals.source(false,false));
        assertEquals(SharingVisuals.Source.PARTY,SharingVisuals.source(true,false));
        assertEquals(SharingVisuals.Source.LOCATION,SharingVisuals.source(false,true));
        assertEquals(SharingVisuals.Source.PARTY,SharingVisuals.source(true,true));
        ConfigManager.locationChamsEnabled=true;ConfigManager.locationEspEnabled=true;
        assertEquals(SharingVisuals.Source.NONE,SharingVisuals.source(false,false));
    }
    @Test void eachRendererPreferenceUsesTheSelectedSourceWithoutSharingMutableSettings(){
        ConfigManager.partyEspEnabled=false;ConfigManager.locationEspEnabled=true;
        ConfigManager.sharingChamsEnabled=true;ConfigManager.locationChamsEnabled=false;
        ConfigManager.partyEspColor=0x112233;ConfigManager.locationEspColor=0x445566;
        ConfigManager.partyEspStyle="none";ConfigManager.locationEspStyle="fill";
        ConfigManager.partyNametagStyle="simple";ConfigManager.locationNametagStyle="vanilla";
        ConfigManager.partyNametagSize=.5;ConfigManager.locationNametagSize=2;
        ConfigManager.partyMaxDisplayDistance=5;ConfigManager.locationMaxDisplayDistance=500;
        assertFalse(SharingVisuals.esp(true));assertTrue(SharingVisuals.esp(false));assertTrue(SharingVisuals.chams(true));assertFalse(SharingVisuals.chams(false));
        assertEquals(0x112233,SharingVisuals.color(true));assertEquals(0x445566,SharingVisuals.color(false));assertEquals("none",SharingVisuals.style(true));assertEquals("fill",SharingVisuals.style(false));
        assertEquals("simple",SharingVisuals.nametagStyle(true));assertEquals("vanilla",SharingVisuals.nametagStyle(false));assertEquals(.5,SharingVisuals.nametagSize(true));assertEquals(2,SharingVisuals.nametagSize(false));
        assertEquals(5,SharingVisuals.maxDistance(true));assertEquals(500,SharingVisuals.maxDistance(false));
    }
}
