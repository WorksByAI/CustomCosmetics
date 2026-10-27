// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.features.creature;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

class CreatureModelsTest {
    @Test void expandedCatalogIncludesBossesAquaticAndRecentCreatures() {
        for (String id : new String[]{"ender_dragon", "wither", "warden", "ghast", "happy_ghast",
                "tropical_fish", "nautilus", "copper_golem", "sulfur_cube", "mannequin", "armor_stand"})
            assertEquals(id, CreatureModels.parse(id).id);
        assertEquals(CreatureModels.Choice.PLAYER, CreatureModels.parse("item"));
        assertEquals(CreatureModels.Choice.PLAYER, CreatureModels.parse("fake_mob"));
    }
    @Test void onlySupportedModelsCanBeLoadedFromConfigOrSync() {
        assertEquals(CreatureModels.Choice.PLAYER, CreatureModels.parse(null));
        assertEquals(CreatureModels.Choice.PLAYER, CreatureModels.parse("../zombie"));
        assertEquals(CreatureModels.Choice.COW, CreatureModels.parse("cow"));
        for(var choice:CreatureModels.Choice.values()){
            assertEquals(choice,CreatureModels.parse(choice.id));
            assertEquals(choice,CreatureModels.parse(choice.id.toUpperCase(java.util.Locale.ROOT)));
        }
    }
}
