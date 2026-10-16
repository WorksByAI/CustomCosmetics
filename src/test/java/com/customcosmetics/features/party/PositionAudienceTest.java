// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.features.party;
import com.customcosmetics.config.PlayerBlocklist;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class PositionAudienceTest {
    private static final UUID A=UUID.randomUUID(),B=UUID.randomUUID();
    private static final PartyRoster ROSTER=new PartyRoster(Set.of(A),Set.of(),"fixture");
    @Test void defaultRetainsPartyConsentAndDoesNotReceiveStrangers(){
        var audience=PositionAudience.settings(false,"public",List.of(),false);
        assertEquals(PositionAudience.PARTY,audience);assertFalse(audience.publishes(PartyRoster.EMPTY,true));
        assertFalse(audience.sendsTo(A,"Alice",ROSTER,false));assertTrue(audience.sendsTo(A,"Alice",ROSTER,true));
        assertFalse(audience.accepts(B,"Bob",ROSTER,true));assertFalse(audience.accepts(A,"Alice",ROSTER,false));
    }
    @Test void publicSharingAndReceivingAreIndependentAndWorkWithoutAParty(){
        var send=PositionAudience.settings(true,"public",List.of(),false);
        assertTrue(send.publishes(PartyRoster.EMPTY,false));assertTrue(send.sendsTo(B,"Bob",PartyRoster.EMPTY,false));
        assertFalse(send.accepts(B,"Bob",PartyRoster.EMPTY,false));
        var receive=PositionAudience.settings(false,"public",List.of(),true);
        assertTrue(receive.accepts(B,"Bob",PartyRoster.EMPTY,false));assertFalse(receive.publishes(PartyRoster.EMPTY,false));
    }
    @Test void selectedAudienceReplacesPartyAndUuidRecipientsDoNotFollowAReusedAccountName(){
        var audience=PositionAudience.settings(true,"selected",List.of(new PlayerBlocklist.Entry(B,"Bob")),true);
        assertFalse(audience.sendsTo(A,"Alice",ROSTER,true));assertTrue(audience.sendsTo(B,"RenamedBob",PartyRoster.EMPTY,false));
        assertFalse(audience.sendsTo(UUID.randomUUID(),"Bob",ROSTER,true));
        var manual=PositionAudience.settings(true,"selected",List.of(new PlayerBlocklist.Entry(null,"bOb")),false);
        assertTrue(manual.sendsTo(B,"BOB",PartyRoster.EMPTY,false));
    }
    @Test void emptyAndInvalidAudiencesFailClosedAndTargetListsAreBounded(){
        var empty=PositionAudience.settings(true,"typo",List.of(),false);
        assertEquals("selected",empty.mode());assertFalse(empty.publishes(ROSTER,true));assertFalse(empty.sendsTo(A,"Alice",ROSTER,true));
        assertEquals(256,PositionAudience.targets(java.util.stream.IntStream.range(0,300).mapToObj(i->new PlayerBlocklist.Entry(null,"User"+i)).toList()).size());
        assertTrue(PositionAudience.targets(List.of(new PlayerBlocklist.Entry(null,"ab"),new PlayerBlocklist.Entry(null,"bad name"))).isEmpty());
    }
}
