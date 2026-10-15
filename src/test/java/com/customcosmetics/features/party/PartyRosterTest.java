// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.features.party;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import net.hypixel.modapi.HypixelModAPI;
import net.hypixel.modapi.packet.impl.clientbound.ClientboundPartyInfoPacket;
import static org.junit.jupiter.api.Assertions.*;
class PartyRosterTest{
    @Test void statusTitleAndRosterSourceAreSeparateAndReadable(){
        var api=new PartyRoster(Set.of(A,B),Set.of(),"Hypixel Mod API");
        assertEquals("Party: 2 Members.",api.title());assertEquals("Source: Hypixel Mod API",api.sourceLabel());
        var manual=new PartyRoster(Set.of(),Set.of("Alice","Bob"),"/p list");
        assertEquals("Party: 2 Members.",manual.title());assertEquals("Source: /p list",manual.sourceLabel());
        assertEquals("Party: 1 Member.",new PartyRoster(Set.of(A),Set.of(),"test").title());
        assertEquals("Party: 0 Members.",PartyRoster.EMPTY.title());assertEquals("Source: Unknown",PartyRoster.EMPTY.sourceLabel());
    }
    private static final UUID A=UUID.fromString("12345678-1234-4234-8234-123456789abc"),B=UUID.fromString("22345678-1234-4234-8234-123456789abc");
    @Test void officialApiReportsAllPartyMembersAndClearsOnLeave(){
        var result=new AtomicReference<PartyRoster>();var bridge=new HypixelPartyBridge();assertTrue(bridge.register(getClass().getClassLoader(),result::set));
        var packet=new ClientboundPartyInfoPacket(2,true,Map.of(A,new ClientboundPartyInfoPacket.PartyMember(A,ClientboundPartyInfoPacket.PartyRole.LEADER),B,new ClientboundPartyInfoPacket.PartyMember(B,ClientboundPartyInfoPacket.PartyRole.MEMBER)));
        HypixelModAPI.getInstance().handle(packet);assertEquals(Set.of(A,B),result.get().members());
        HypixelModAPI.getInstance().handle(new ClientboundPartyInfoPacket(2,false,Map.of()));assertTrue(result.get().empty());
    }
    @Test void missingOptionalApiDoesNotCrashOrSendChatCommands(){
        var loader=new ClassLoader(getClass().getClassLoader()){@Override protected Class<?> loadClass(String name,boolean resolve)throws ClassNotFoundException{if(name.startsWith("net.hypixel."))throw new ClassNotFoundException(name);return super.loadClass(name,resolve);}};
        var bridge=new HypixelPartyBridge();assertFalse(bridge.register(loader,value->fail()));assertFalse(bridge.request());
    }
    @Test void manualListsOnlyCommitCompleteRankedServerReplies(){
        var parser=new PartyListParser();assertTrue(parser.accept("Party > Alice: Party Members (2)",0).isEmpty());
        assertTrue(parser.accept("§eParty Members (2)",1).isEmpty());assertTrue(parser.accept("Party Leader: [MVP+] Alice ●",2).isEmpty());
        var roster=parser.accept("Party Members: [VIP] bOB ●",3).orElseThrow();assertEquals(Set.of("alice","bob"),roster.names());assertTrue(roster.contains(B,"BoB"));
    }
    @Test void incompleteOrExpiredListsCannotAddStrangers(){
        var parser=new PartyListParser();parser.accept("Party Members (3)",0);parser.accept("Party Leader: Alice ●",1);
        assertTrue(parser.accept("Party Members: Bob ●",6000).isEmpty());assertTrue(parser.accept("Party Leader: Carol ●",6001).isEmpty());
        parser.accept("Party Members (2)",7000);parser.accept("Party Leader: Alice ●",7001);parser.reset();assertTrue(parser.accept("Party Members: Bob ●",7002).isEmpty());
        assertTrue(PartyListParser.changed("You left the party."));assertTrue(PartyListParser.changed("[MVP+] Alice has joined the party."));assertFalse(PartyListParser.changed("Party > Alice: You left the party."));
    }
    @Test void multilineServerRepliesAndMembershipMessageVariantsAreHandled(){
        var parser=new PartyListParser();
        var roster=parser.accept("----------------\nParty Members (2)\nParty Leader: [MVP++] Alice ●\nParty Members: [VIP+] Bob ●\n----------------",10).orElseThrow();
        assertEquals(Set.of("alice","bob"),roster.names());
        for(String text:List.of("[MVP++] Alice joined the party.","Alice has left the party.","Bob left the party!","[VIP+] Bob has been removed from the party.","You have removed [VIP+] Bob from your party.","Your party has been disbanded.","You have joined [MVP+] Alice's party!"))assertTrue(PartyListParser.changed(text),text);
        assertFalse(PartyListParser.changed("Party > [MVP+] Alice: Bob left the party!"));
    }
    @Test void namesAreNormalizedWithoutAcceptingFormattingOrMutableSets(){
        var input=new HashSet<>(Set.of("Alice"));var roster=new PartyRoster(Set.of(A),input,"test");input.add("Carol");assertEquals(Set.of("alice"),roster.names());
        assertThrows(IllegalArgumentException.class,()->new PartyRoster(Set.of(),Set.of("§aCarol"),"test"));
    }
    @Test void prefixOnlyInterceptsIrcWhenEnabledAndNeverProducesHypixelCommands(){
        assertTrue(PartyChatRules.intercept("`hello",true,"`"));assertFalse(PartyChatRules.intercept("`hello",false,"`"));assertFalse(PartyChatRules.intercept("/p hello",true,"`"));
        assertTrue(PartyChatRules.valid("😀".repeat(256)));for(String text:List.of("😀".repeat(257),"", " ","x\ny","§aInjected","\uD800"))assertFalse(PartyChatRules.valid(text));
    }
}
