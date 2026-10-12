// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.features.items;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ItemIdentityTest {
    private static final String UUID="12345678-1234-1234-1234-123456789abc";
    @Test void recognizesModernAndLegacyItemUuidsWithoutUsingSkyblockOrPlayerIds(){
        var modern=new CompoundTag();modern.putString("id","HYPERION");modern.putString("uuid",UUID);
        assertEquals(UUID,ItemCosmetics.uuid(modern));
        var legacy=new CompoundTag();legacy.put("ExtraAttributes",modern);assertEquals(UUID,ItemCosmetics.uuid(legacy));
        var missing=new CompoundTag();missing.putString("id","HYPERION");assertEquals("",ItemCosmetics.uuid(missing));
    }
    @Test void identityLookupDoesNotModifyCustomData(){
        var tag=new CompoundTag();tag.putString("uuid",UUID);tag.putString("id","ASPECT_OF_THE_VOID");var original=tag.copy();
        assertEquals(UUID,ItemCosmetics.uuid(tag));assertEquals(original,tag);
    }
}
