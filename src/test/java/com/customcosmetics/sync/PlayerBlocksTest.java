// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.sync;

import com.customcosmetics.config.*;
import java.util.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

class PlayerBlocksTest {
    private static final UUID A=UUID.fromString("12345678-1234-4234-8234-123456789abc"),B=UUID.fromString("22345678-1234-4234-8234-123456789abc");
    private static final List<PresenceSync.SyncedPlayer> KNOWN=List.of(new PresenceSync.SyncedPlayer(A,"Alice","暮冬"),new PresenceSync.SyncedPlayer(B,"Bob","Snow Fall"));
    private final Map<PlayerBlocks.Kind,List<PlayerBlocklist.Entry>> original=new EnumMap<>(PlayerBlocks.Kind.class);
    @BeforeEach void isolate(){for(var kind:PlayerBlocks.Kind.values()){original.put(kind,PlayerBlocks.entries(kind));PlayerBlocks.set(kind,List.of());}}
    @AfterEach void restore(){original.forEach(PlayerBlocks::set);}
    @Test void aliasesResolveToStableIdentitiesAndEachFeatureHasItsOwnList(){
        for(var kind:PlayerBlocks.Kind.values()){
            assertTrue(PlayerBlocks.edit(true,kind,"暮冬",KNOWN).changed());assertTrue(PlayerBlocks.blocked(kind,A,"Renamed"));
            assertEquals(List.of(new PlayerBlocklist.Entry(A,"Alice")),PlayerBlocks.entries(kind));
            assertFalse(PlayerBlocks.edit(true,kind,"ALICE",KNOWN).changed());
            assertTrue(PlayerBlocks.edit(false,kind,"暮冬",KNOWN).changed());assertTrue(PlayerBlocks.entries(kind).isEmpty());
        }
        PlayerBlocks.edit(true,PlayerBlocks.Kind.CAPE,"Alice",KNOWN);assertTrue(PlayerBlocks.entries(PlayerBlocks.Kind.NICK).isEmpty());assertTrue(PlayerBlocks.entries(PlayerBlocks.Kind.ACCESSORIES).isEmpty());
    }
    @Test void unknownAccountsCanBeAddedOfflineAndRemovedWithoutAnyLookup(){
        assertTrue(PlayerBlocks.edit(true,PlayerBlocks.Kind.NICK,"OldFriend",List.of()).changed());
        assertTrue(PlayerBlocks.blocked(PlayerBlocks.Kind.NICK,A,"oldfriend"));
        assertTrue(PlayerBlocks.edit(false,PlayerBlocks.Kind.NICK,"OLDFRIEND",List.of()).changed());
        assertFalse(PlayerBlocks.edit(true,PlayerBlocks.Kind.NICK,"未知昵称",List.of()).changed());
    }
    @Test void quotedNamesSpacesAndAmbiguousAliasesAreHandledWithoutPickingTheWrongPerson(){
        assertTrue(PlayerBlocks.edit(true,PlayerBlocks.Kind.CAPE,"\"Snow Fall\"",KNOWN).changed());
        var duplicates=List.of(new PresenceSync.SyncedPlayer(A,"Alice","same"),new PresenceSync.SyncedPlayer(B,"Bob","same"));
        var result=PlayerBlocks.edit(true,PlayerBlocks.Kind.NICK,"same",duplicates);assertFalse(result.changed());assertTrue(result.message().contains("Alice, Bob"));
        assertTrue(PlayerBlocks.entries(PlayerBlocks.Kind.NICK).isEmpty());
        assertTrue(PlayerBlocks.edit(false,PlayerBlocks.Kind.CAPE,B.toString(),List.of()).changed());
    }
    @Test void commandTreeExposesEveryActionAndFeatureWithAPlayerArgument(){
        var root=PlayerBlockCommands.command().build();
        for(String action:List.of("add","remove"))for(String feature:List.of("accessories","nick","cape")){
            var node=root.getChild(action).getChild(feature);assertNotNull(node.getCommand());assertNotNull(node.getChild("player").getCommand());
        }
    }
    @Test void resolvingAnIdentityDoesNotRemoveAnotherPlayersOldAccountName(){
        PlayerBlocks.set(PlayerBlocks.Kind.NICK,List.of(new PlayerBlocklist.Entry(A,"Alice"),new PlayerBlocklist.Entry(B,"Alice")));
        assertTrue(PlayerBlocks.edit(false,PlayerBlocks.Kind.NICK,"暮冬",KNOWN).changed());
        assertEquals(List.of(new PlayerBlocklist.Entry(B,"Alice")),PlayerBlocks.entries(PlayerBlocks.Kind.NICK));
    }
}
