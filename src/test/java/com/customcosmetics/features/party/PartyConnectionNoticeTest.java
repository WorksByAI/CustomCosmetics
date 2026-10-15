// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.features.party;

import com.customcosmetics.gui.PinkUi;
import java.util.Optional;
import net.minecraft.network.chat.ClickEvent;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PartyConnectionNoticeTest {
    @Test void noticeWaitsForRosterConfirmationAndOnlyRepeatsAfterAnotherJoin() {
        var notice = new PartyConnectionNotice();
        assertFalse(notice.connected(true));
        notice.membership(true);
        assertFalse(notice.connected(false));
        assertTrue(notice.connected(true));
        notice.membership(true); // periodic roster update / another member joins
        assertFalse(notice.connected(true));
        assertFalse(notice.connected(false));
        assertFalse(notice.connected(true)); // automatic reconnect
        notice.membership(false);
        assertFalse(notice.connected(true));
        notice.membership(true);
        assertFalse(notice.connected(false));
        assertTrue(notice.connected(true));
    }

    @Test void messageIsPinkEnglishAndEverySpanOpensTheLocalPartySettingsCommand() {
        var message = PartyConnectionNotice.message();
        assertEquals("[CustomCosmetics] Connected to the Party sharing server. [Settings]", message.getString());
        message.visit((style, text) -> {
            assertEquals(PinkUi.PINK & 0xFFFFFF, style.getColor().getValue());
            assertEquals(new ClickEvent.RunCommand("/customcosmetics party"), style.getClickEvent());
            return Optional.empty();
        }, message.getStyle());
    }

    @Test void disabledNoticesAreConsumedWithoutAppearingLaterInTheSameParty(){
        var notice=new PartyConnectionNotice();notice.membership(true);
        assertFalse(notice.connected(true,false));assertFalse(notice.connected(true,true));
        notice.membership(false);notice.membership(true);assertTrue(notice.connected(true,true));
    }

    @Test void aFailedConnectionCanReportOnceOnlyInAPartyAndARecoveryCanReportSuccessOnce(){
        var notice=new PartyConnectionNotice();assertFalse(notice.failed(true));
        notice.membership(true);assertTrue(notice.failed(true));assertFalse(notice.failed(true));
        assertTrue(notice.connected(true,true));assertFalse(notice.failed(true));assertFalse(notice.connected(true,true));
        notice.membership(false);notice.membership(true);assertFalse(notice.failed(false));assertFalse(notice.failed(true));
        var message=PartyConnectionNotice.message(false);assertEquals("[CustomCosmetics] Could not connect to the Party sharing server. [Settings]",message.getString());
        assertEquals(new ClickEvent.RunCommand(PartyConnectionNotice.SETTINGS_COMMAND),message.getStyle().getClickEvent());
    }

    @Test void otherMembersJoiningOrLeavingDoNotStartAnotherPartySession() {
        for (String message : new String[]{"Alice joined the party.", "Bob has left the party.",
                "You have removed [VIP] Bob from your party.", "[VIP] Bob has been removed from the party."}) {
            assertTrue(PartyListParser.changed(message));
            assertFalse(PartyListParser.leftOrJoined(message));
        }
        for (String message : new String[]{"You left the party.", "[VIP] Bob has disbanded the party!",
                "You have joined Alice's party!", "You have been kicked from the party by Alice."}) {
            assertTrue(PartyListParser.leftOrJoined(message));
        }
        assertFalse(PartyListParser.leftOrJoined("Party > Alice: You left the party."));
        assertFalse(PartyListParser.leftOrJoined("Party > Alice: Bob has disbanded the party!"));
        assertFalse(PartyListParser.changed("Party > Alice: Bob has disbanded the party!"));
    }
}
