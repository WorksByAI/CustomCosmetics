// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.utils;

import com.customcosmetics.config.ConfigManager;
import com.customcosmetics.config.PlayerBlocklist;
import com.customcosmetics.sync.PresenceSync;
import java.util.*;
import net.minecraft.network.chat.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

class NicknameBlocklistTest {
    private static final UUID SELF=UUID.fromString("22345678-1234-4234-8234-123456789abc"),ALICE=UUID.fromString("12345678-1234-4234-8234-123456789abc"),BOB=UUID.fromString("32345678-1234-4234-8234-123456789abc");
    private List<PlayerBlocklist.Entry> saved;
    @BeforeEach void isolate(){saved=ConfigManager.nickTweaksBlockedPlayers;NickRenderUtils.invalidateLocalTargetsCache();}
    @AfterEach void restore(){ConfigManager.nickTweaksBlockedPlayers=saved;NickRenderUtils.invalidateLocalTargetsCache();}
    private static PresenceSync.RemoteNickname remote(UUID uuid,String account,String alias){
        return new PresenceSync.RemoteNickname(uuid,account,new PresenceSync.ChromaProfile(false,new int[0],1,1,.2,.8,0x12ABEF,0xFFCCAA,true,true,alias,"smooth",1));
    }
    @Test void accountNamesAreLocalCaseInsensitiveAndDoNotBlockAnAliasCollision(){
        ConfigManager.nickTweaksBlockedPlayers=List.of(new PlayerBlocklist.Entry(null,"ALICE"));
        var targets=NickRenderUtils.buildTargets(SELF,"Self",false,List.of(remote(ALICE,"Alice","PrettyName"),remote(BOB,"Bob","Alice")));
        assertEquals(List.of("Bob"),targets.stream().map(NickRenderUtils.Target::name).toList());
        assertEquals("Alice",NickRenderUtils.renderString("Alice",targets));
    }
    @Test void selectedIdentityStaysBlockedAfterAliasAndAccountChanges(){
        ConfigManager.nickTweaksBlockedPlayers=List.of(new PlayerBlocklist.Entry(ALICE,"Alice"));
        assertTrue(NickRenderUtils.buildTargets(SELF,"Self",false,List.of(remote(ALICE,"Renamed","暮冬"))).isEmpty());
        assertFalse(NickRenderUtils.buildTargets(SELF,"Self",false,List.of(remote(BOB,"Alice","DifferentPerson"))).isEmpty());
    }
    @Test void blockedNamesRetainTheirOriginalServerFormattingAndUnblockingRestoresReplacement(){
        var remotes=List.of(remote(ALICE,"Alice","暮冬"));ConfigManager.nickTweaksBlockedPlayers=List.of(new PlayerBlocklist.Entry(ALICE,"Alice"));
        var text=Component.literal("[MVP+] Alice: hello").withStyle(Style.EMPTY.withColor(0x55FFFF).withClickEvent(new ClickEvent.SuggestCommand("/msg Alice ")));
        var result=NickRenderUtils.renderFormattedText(text,NickRenderUtils.buildTargets(SELF,"Self",false,remotes));
        assertSame(text,result);ConfigManager.nickTweaksBlockedPlayers=List.of();NickRenderUtils.invalidateLocalTargetsCache();
        assertEquals("暮冬",NickRenderUtils.renderFormattedText(Component.literal("Alice"),NickRenderUtils.buildTargets(SELF,"Self",false,remotes)).getString());
    }
    @Test void normalizationRejectsFormattingInvalidNamesAndDuplicates(){
        var normalized=PlayerBlocklist.normalize(Arrays.asList(null,new PlayerBlocklist.Entry(null," Alice "),new PlayerBlocklist.Entry(null,"ALICE"),new PlayerBlocklist.Entry(null,"&aBad"),new PlayerBlocklist.Entry(null,"a name"),new PlayerBlocklist.Entry(ALICE,"Alice"),new PlayerBlocklist.Entry(ALICE,"Renamed")));
        assertEquals(List.of(new PlayerBlocklist.Entry(null,"Alice"),new PlayerBlocklist.Entry(ALICE,"Alice")),normalized);
        assertEquals(List.of(),PlayerBlocklist.normalize(null));
    }
}
