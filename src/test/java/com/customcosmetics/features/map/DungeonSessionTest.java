package com.customcosmetics.features.map;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class DungeonSessionTest {
    private final Object world=new Object(),connection=new Object();
    private final DungeonSession session=new DungeonSession();
    private static final List<String> DRAGONS=List.of("Power: 100%","Apex: 100%","Soul: 100%","Ice: 100%","Flame: 100%","www.hypixel.net");

    @Test void confirmedM7KeepsItsFloorWhenDragonHealthReplacesTheLocation(){
        assertEquals(new DungeonSession.Context(true,"M7"),session.update(world,connection,"SKYBLOCK",List.of("⏣ The Catacombs (M7)")));
        for(int i=0;i<100;i++)assertEquals(new DungeonSession.Context(true,"M7"),session.update(world,connection,"SKYBLOCK",DRAGONS));
        assertEquals(new DungeonSession.Context(true,"M7"),session.update(world,connection,"",List.of()));
        assertEquals(new DungeonSession.Context(true,"M7"),session.update(world,connection,"SKYBLOCK",List.of("The Catacombs","Time Elapsed: 12m 20s","Cleared: 100%")));
    }
    @Test void dragonNamesAndAnEmptySidebarCannotStartARun(){
        assertFalse(session.update(world,connection,"SKYBLOCK",DRAGONS).detected());
        assertFalse(session.update(world,connection,"",List.of()).detected());
    }
    @Test void explicitExitClearsTheFloorAndDoesNotResumeFromStaleDragonRows(){
        for(String location:List.of("§c⏣ Dungeon Hub","§7\ue067 Hub","⏣ The End","⏣ Kuudra's Hollow")){
            session.update(world,connection,"SKYBLOCK",List.of("The Catacombs (M7)"));
            assertEquals(new DungeonSession.Context(false,""),session.update(world,connection,"SKYBLOCK",List.of(location)),location);
            assertFalse(session.update(world,connection,"SKYBLOCK",DRAGONS).detected());
        }
        session.update(world,connection,"SKYBLOCK",List.of("The Catacombs (F7)"));
        assertFalse(session.update(world,connection,"BED WARS",List.of()).detected());
    }
    @Test void worldConnectionAndDisconnectBoundariesClearTheSession(){
        for(int boundary=0;boundary<3;boundary++){
            session.update(world,connection,"SKYBLOCK",List.of("The Catacombs (M7)"));
            assertFalse(switch(boundary){
                case 0->session.update(new Object(),connection,"SKYBLOCK",DRAGONS).detected();
                case 1->session.update(world,new Object(),"SKYBLOCK",DRAGONS).detected();
                default->session.update(null,null,"",List.of()).detected();
            });
        }
    }
    @Test void aNewExplicitFloorReplacesThePreviousFloor(){
        session.update(world,connection,"SKYBLOCK",List.of("The Catacombs (M7)"));
        assertEquals("F7",session.update(world,connection,"SKYBLOCK",List.of("The Catacombs (F7)")).floor());
    }
}
