package com.customcosmetics.features.blocking;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WeaponCatalogTest {
    @Test void bundledCatalogHasDistinctIdsAndIncludesNonVanillaSkyblockWeapons() {
        var entries = WeaponCatalog.skyblock();
        assertTrue(entries.size() > 250);
        assertEquals(entries.size(), entries.stream().map(WeaponCatalog.Entry::id).distinct().count());
        for (String id : new String[] {"ASPECT_OF_THE_VOID", "HYPERION", "ATOMSPLIT_KATANA", "TERMINATOR", "SOUL_WHIP", "FIRE_VEIL_WAND"})
            assertTrue(entries.stream().anyMatch(entry -> entry.id().equals("skyblock:" + id)), id);
        var atomsplit = entries.stream().filter(entry -> entry.id().equals("skyblock:ATOMSPLIT_KATANA")).findFirst().orElseThrow();
        assertTrue(atomsplit.matches("Atomsplit Katana")); assertTrue(atomsplit.matches("atomsplit_katana"));
        assertFalse(atomsplit.matches("terminator"));
    }
    @Test void bundledNamesAreReadableAndPreviewMenuEntriesAreExcluded() {
        for (var entry : WeaponCatalog.skyblock()) {
            assertFalse(entry.id().endsWith("_PREVIEW"), entry.id());
            assertFalse(entry.name().contains("%%"), entry.id());
            assertFalse(entry.name().contains("§"), entry.id());
            assertFalse(entry.name().isBlank(), entry.id());
        }
    }
    @Test void entryCleansOfficialAndLegacyColorCodesWithoutChangingIdentityOrNormalText() {
        var entry = new WeaponCatalog.Entry("skyblock:TEST_BLADE",
            "  %%green%%§lSword %%gray%%& Shield §x§f§f§0§0§a§a50%  ", "SWORD");
        assertEquals("Sword & Shield 50%", entry.name());
        assertEquals("skyblock:TEST_BLADE", entry.id());
        assertTrue(entry.matches("sword & shield"));
        assertFalse(entry.matches("green"));
        assertEquals("100% damage & defence", WeaponCatalog.plainName("100% damage & defence"));
        assertEquals("A Blade", WeaponCatalog.plainName("%%RED%%A %%BOLD%%Blade%%RESET%%"));
    }
    @Test void vanillaCatalogCoversWeaponFamiliesWithoutListingUtilityTools() {
        for (String id : new String[] {"iron_sword", "diamond_sword", "netherite_axe", "copper_spear", "bow", "crossbow", "trident", "mace"})
            assertTrue(WeaponCatalog.vanillaWeapon("minecraft:" + id), id);
        for (String id : new String[] {"diamond_pickaxe", "diamond_hoe", "fishing_rod", "apple", "stick", "air"})
            assertFalse(WeaponCatalog.vanillaWeapon("minecraft:" + id), id);
        assertFalse(WeaponCatalog.vanillaWeapon("foreign:iron_sword"));
    }
}
