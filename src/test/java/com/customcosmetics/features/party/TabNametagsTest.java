package com.customcosmetics.features.party;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class TabNametagsTest {
    private static TabNametags.Row row(String profile,String display){return new TabNametags.Row(UUID.randomUUID(),profile,Component.literal(display).withStyle(ChatFormatting.AQUA));}
    @Test void handlesBothCoopAndPlayersLayoutsWithoutDependingOnRowOrderOrUuid(){
        var id=UUID.randomUUID();var tory=row("!fake1","483 ToryHi ♣");var fly=row("!fake2","584 FlyMode ✓");
        assertEquals(tory.display(),TabNametags.find(id,"ToryHi",List.of(row("!heading","Coop (3)"),tory,fly)));
        assertEquals(tory.display(),TabNametags.find(id,"ToryHi",List.of(row("!heading","Players (21)"),fly,row("!other","464 Sinnoh ♫"),tory)));
        assertEquals("[483] ToryHi ♣",TabNametags.find(id,"ToryHi",List.of(row("!fake","[483] ToryHi ♣"))).getString());
        assertEquals("§c483 §bToryHi ♣",TabNametags.find(id,"ToryHi",List.of(row("!fake","§c483 §bToryHi ♣"))).getString());
    }
    @Test void rejectsSubstringCollisionsHeadingsStatisticsAndAmbiguousRows(){
        var id=UUID.randomUUID();assertNull(TabNametags.find(id,"ToryHi",List.of(row("!fake","483 ToryHiAlt ♣"))));
        assertNull(TabNametags.find(id,"Players",List.of(row("!head","Players (21)"))));assertNull(TabNametags.find(id,"Mana",List.of(row("!stat","Mana: 483"))));
        assertNull(TabNametags.find(id,"ToryHi",List.of(row("!a","483 ToryHi ♣"),row("!b","584 ToryHi ✓"))));
    }
    @Test void prefersAuthenticatedIdentityOrExactProfileNameAndAcceptsIdenticalRepeatedRows(){
        var id=UUID.randomUUID();var exact=new TabNametags.Row(id,"ToryHi",Component.literal("[MVP+] ToryHi"));
        assertEquals(exact.display(),TabNametags.find(id,"ToryHi",List.of(row("!fake","483 ToryHi ♣"),exact)));
        assertEquals(exact.display(),TabNametags.find(UUID.randomUUID(),"ToryHi",List.of(row("!fake","483 ToryHi ♣"),exact)));
        assertEquals("483 ToryHi ♣",TabNametags.find(UUID.randomUUID(),"ToryHi",List.of(row("!a","483 ToryHi ♣"),row("!b","483 ToryHi ♣"))).getString());
    }
}
