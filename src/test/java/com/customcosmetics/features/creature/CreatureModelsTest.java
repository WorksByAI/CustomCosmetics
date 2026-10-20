// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.features.creature;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

class CreatureModelsTest {
    @Test void onlySupportedModelsCanBeLoadedFromConfigOrSync() {
        assertEquals(CreatureModels.Choice.PLAYER, CreatureModels.parse(null));
        assertEquals(CreatureModels.Choice.PLAYER, CreatureModels.parse("../zombie"));
        assertEquals(CreatureModels.Choice.COW, CreatureModels.parse("cow"));
    }
}
