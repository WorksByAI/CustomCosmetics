package com.customcosmetics.features.blocking;

import java.util.Optional;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SwordItemsTest {
    @Test void skyblockIdentityOverridesVanillaMaterialInBothDirections() {
        for (String id : new String[] {"ATOMSPLIT_KATANA", "HYPERION", "SCYLLA", "VALKYRIE", "ASTRAEA", "ASPECT_OF_THE_END"})
            assertTrue(SwordItems.eligible(Optional.of(id), false), id + " works even as a blaze rod");
        for (String id : new String[] {"ASPECT_OF_THE_VOID", "MIDAS_SWORD"})
            assertTrue(SwordItems.eligible(Optional.of(id), true));
        for (String id : new String[] {"BLAZE_ROD", "DIAMOND_PICKAXE", "JUJU_SHORTBOW", "", "UNKNOWN_ITEM"})
            assertFalse(SwordItems.eligible(Optional.of(id), true), id + " cannot borrow a vanilla sword appearance");
        assertTrue(SwordItems.eligible(Optional.empty(), true));
        assertFalse(SwordItems.eligible(Optional.empty(), false));
    }
    @Test void modernCustomDataReadsOnlyExtraAttributesId() {
        CompoundTag data = new CompoundTag(), extra = new CompoundTag();
        extra.putString("id", "ATOMSPLIT_KATANA"); data.put("ExtraAttributes", extra);
        assertEquals(Optional.of("ATOMSPLIT_KATANA"), SwordItems.skyblockId(data));
        assertEquals(Optional.empty(), SwordItems.skyblockId(new CompoundTag()));
        assertEquals(Optional.empty(), SwordItems.skyblockId((CompoundTag) null));
        extra.putInt("id", 123);
        assertEquals(Optional.of(""), SwordItems.skyblockId(data));
        assertFalse(SwordItems.eligible(SwordItems.skyblockId(data), true));
    }
    @Test void modernRootIdentityTakesPriorityOverLegacyIdentity() {
        CompoundTag data = new CompoundTag(), extra = new CompoundTag();
        extra.putString("id", "ATOMSPLIT_KATANA"); data.put("ExtraAttributes", extra);
        data.putString("id", "HYPERION");
        assertEquals(Optional.of("HYPERION"), SwordItems.skyblockId(data));
        data.putString("id", "JUJU_SHORTBOW");
        assertFalse(SwordItems.eligible(SwordItems.skyblockId(data), true));
        data.putInt("id", 5);
        assertFalse(SwordItems.eligible(SwordItems.skyblockId(data), true));
    }
}
