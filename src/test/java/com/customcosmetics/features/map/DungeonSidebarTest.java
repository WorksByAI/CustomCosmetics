package com.customcosmetics.features.map;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class DungeonSidebarTest {
    @Test void actualVideoSidebarWorksWithoutHostnameOrApi(){assertTrue(DungeonSidebar.matches("SKYBLOCK",List.of("Late Summer 30th","⏣ The Catacombs (F7)","Time Elapsed: 28m 43s","Cleared: 39% (71)","Solo","www.hypixel.net")));}
    @Test void formattingSplitLettersAndTeamSidebarAreNormalized(){assertTrue(DungeonSidebar.matches("§aSKYBLOCK CO-OP",List.of("§c⏣ The C§6a§et§aa§bc§9o§dm§cb§as \u200b(Ｍ７)","Starting in: 10s")));assertTrue(DungeonSidebar.matches("Other title",List.of("⏣ The Catacombs (E)","www.hypixel.net")));}
    @Test void dungeonHubSkillsAndUnrelatedObjectivesDoNotCountAsDungeon(){for(String location:List.of("⏣ Dungeon Hub","Catacombs Level: 50","Complete The Catacombs (F7)","Catacombs (F8)","Catacombs (M8)"))assertFalse(DungeonSidebar.matches("SKYBLOCK",List.of(location)));assertFalse(DungeonSidebar.matches("Lobby",List.of("The Catacombs (F7)")));assertFalse(DungeonSidebar.matches("SKYBLOCK",List.of()));}
    @Test void noFloorRequiresTheActiveRunCounters(){assertTrue(DungeonSidebar.matches("SKYBLOCK",List.of("⏣ The Catacombs","Time Elapsed: 1m 20s","Cleared: 20%")));assertFalse(DungeonSidebar.matches("SKYBLOCK",List.of("The Catacombs")));}
    @Test void actualB5LogWithPrivateUseLocationIconIsADungeon(){var rows=List.of("10/16/26 m72DM 102,66"," Early Autumn 2nd"," \ue067 The Catacombs (F7)","Keys: ■ ✗ ■ 0x","Time Elapsed: 32s","Cleared: 0% (0)","Solo","www.hypixel.net");assertTrue(DungeonSidebar.matches("SKYBLOCK",rows));assertEquals("F7",DungeonSidebar.floor(rows));}
    @Test void invisibleScoreOwnerGlyphsInsideWordsAndAfterFloorAreIgnored(){var rows=List.of("\ue067 The Cata\ue000combs (F7)\u0001","www.hypixel.net");assertTrue(DungeonSidebar.matches("SKYBLOCK",rows));assertEquals("F7",DungeonSidebar.floor(rows));assertFalse(DungeonSidebar.matches("SKYBLOCK",List.of("Dungeon Hub\ue000")));}
}
