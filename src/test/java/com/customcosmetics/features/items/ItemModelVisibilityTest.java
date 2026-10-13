// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.features.items;

import java.util.List;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ItemModelVisibilityTest {
    private static final Identifier GOOD = Identifier.parse("pack:item/hyperion"), MISSING = Identifier.parse("minecraft:missingno");
    @Test void rejectsMissingTexturesEvenWhenOtherFacesAreValid() {
        assertFalse(ItemModelVisibility.usable(List.of(MISSING), false, MISSING));
        assertFalse(ItemModelVisibility.usable(List.of(GOOD, MISSING), false, GOOD));
        assertFalse(ItemModelVisibility.usable(List.of(), false, GOOD));
    }
    @Test void keepsValidResourcePackModelsAndVanillaSpecialRenderers() {
        assertTrue(ItemModelVisibility.usable(List.of(GOOD), false, GOOD));
        assertTrue(ItemModelVisibility.usable(List.of(), true, GOOD));
        assertTrue(ItemModelVisibility.usable(List.of(), true, null));
        assertFalse(ItemModelVisibility.usable(List.of(), true, MISSING));
    }
}
