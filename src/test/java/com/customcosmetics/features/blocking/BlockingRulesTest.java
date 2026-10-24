package com.customcosmetics.features.blocking;

import org.junit.jupiter.api.Test;
import java.util.Optional;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class BlockingRulesTest {
    @Test void allocationFreeValidationPreservesAllAcceptedIdentityCharacters(){
        var samples=new java.util.ArrayList<String>(java.util.List.of("minecraft:"+"a".repeat(128),"minecraft:"+"a".repeat(129),"skyblock:"+"A".repeat(128),"skyblock:"+"A".repeat(129)," SkyBlock:ß ","minecraft:a/b-c.d_e","minecraft:","skyblock:"));
        for(int c=0;c<256;c++){samples.add("minecraft:a"+(char)c+"z");samples.add("skyblock:A"+(char)c+"Z");}
        for(var value:samples){
            String clean=value.strip(),expected;
            if(clean.regionMatches(true,0,"skyblock:",0,9)){var id=clean.substring(9).toUpperCase(java.util.Locale.ROOT);expected=id.matches("[A-Z0-9_]{1,128}")?"skyblock:"+id:"";}
            else{clean=clean.toLowerCase(java.util.Locale.ROOT);expected=clean.matches("minecraft:[a-z0-9_./-]{1,128}")?clean:"";}
            assertEquals(expected,BlockingRules.normalize(value),value);
        }
        for(var mode:BlockingRules.Mode.values()){assertEquals(mode,BlockingRules.Mode.parse(" "+mode.value().toUpperCase(java.util.Locale.ROOT)+" "));assertSame(mode.value(),mode.value());}
    }
    @Test void receiverOptInFiltersUsingItsOwnSkyblockWhitelist() {
        var white = Set.of("skyblock:HYPERION");
        assertTrue(BlockingRules.allowsRemote(true, BlockingRules.Mode.WHITELIST, "skyblock:HYPERION", Set.of(), white));
        for (String id : new String[]{"skyblock:ASPECT_OF_THE_VOID", "minecraft:blaze_rod", ""})
            assertFalse(BlockingRules.allowsRemote(true, BlockingRules.Mode.WHITELIST, id, Set.of(), white));
    }
    @Test void receiverOptOutAndOffModeLeaveAuthenticatedSenderChoicesVisible() {
        for (var mode : BlockingRules.Mode.values())
            assertTrue(BlockingRules.allowsRemote(false, mode, "skyblock:ASPECT_OF_THE_VOID",
                Set.of("skyblock:ASPECT_OF_THE_VOID"), Set.of("skyblock:HYPERION")));
        assertTrue(BlockingRules.allowsRemote(true, BlockingRules.Mode.OFF, "skyblock:ASPECT_OF_THE_VOID", Set.of("skyblock:ASPECT_OF_THE_VOID"), Set.of()));
    }
    @Test void blacklistDoesNotHideSelectedNonSwordWeaponsFromAnotherSender() {
        var black = Set.of("skyblock:ASPECT_OF_THE_VOID");
        assertFalse(BlockingRules.allowsRemote(true, BlockingRules.Mode.BLACKLIST, "skyblock:ASPECT_OF_THE_VOID", black, Set.of()));
        assertTrue(BlockingRules.allowsRemote(true, BlockingRules.Mode.BLACKLIST, "skyblock:TERMINATOR", black, Set.of()));
        assertTrue(BlockingRules.allowsRemote(true, BlockingRules.Mode.BLACKLIST, "minecraft:bow", black, Set.of()));
    }
    @Test void skyblockBlacklistDoesNotBlockOtherItemsWithTheSameVanillaMaterial() {
        var black = Set.of("skyblock:ASPECT_OF_THE_VOID");
        String voidId = BlockingRules.identity(Optional.of("ASPECT_OF_THE_VOID"), "minecraft:diamond_sword");
        String endId = BlockingRules.identity(Optional.of("ASPECT_OF_THE_END"), "minecraft:diamond_sword");
        assertFalse(BlockingRules.allows(BlockingRules.Mode.BLACKLIST, voidId, true, black, Set.of()));
        assertTrue(BlockingRules.allows(BlockingRules.Mode.BLACKLIST, endId, true, black, Set.of()));
        assertTrue(BlockingRules.allows(BlockingRules.Mode.BLACKLIST, "minecraft:diamond_sword", true, black, Set.of()));
    }
    @Test void whitelistAllowsSelectedBowsAndRejectsEverythingElse() {
        var white = Set.of("skyblock:TERMINATOR", "minecraft:bow");
        assertTrue(BlockingRules.allows(BlockingRules.Mode.WHITELIST, "skyblock:TERMINATOR", false, Set.of(), white));
        assertTrue(BlockingRules.allows(BlockingRules.Mode.WHITELIST, "minecraft:bow", false, Set.of(), white));
        assertFalse(BlockingRules.allows(BlockingRules.Mode.WHITELIST, "minecraft:diamond_sword", true, Set.of(), white));
        assertFalse(BlockingRules.allows(BlockingRules.Mode.WHITELIST, "", true, Set.of(), white));
    }
    @Test void disabledListsKeepSwordRulesAndBlacklistDoesNotEnableNonSwords() {
        for (var mode : new BlockingRules.Mode[] {BlockingRules.Mode.OFF, BlockingRules.Mode.BLACKLIST}) {
            assertTrue(BlockingRules.allows(mode, "minecraft:iron_sword", true, Set.of(), Set.of()));
            assertFalse(BlockingRules.allows(mode, "minecraft:bow", false, Set.of(), Set.of("minecraft:bow")));
        }
    }
    @Test void invalidSkyblockIdentityNeverFallsBackToAMaterialWhitelist() {
        assertEquals("", BlockingRules.identity(Optional.of("invalid!"), "minecraft:diamond_sword"));
        assertEquals("skyblock:HYPERION", BlockingRules.identity(Optional.of("hyperion"), "minecraft:blaze_rod"));
        assertArrayEquals(new String[] {"minecraft:bow", "skyblock:HYPERION"},
            BlockingRules.sanitize(new String[] {null, "bad", " SKYBLOCK:hyperion ", "skyblock:HYPERION", "Minecraft:BOW"}));
        assertEquals(BlockingRules.Mode.OFF, BlockingRules.Mode.parse("invalid"));
    }
}
