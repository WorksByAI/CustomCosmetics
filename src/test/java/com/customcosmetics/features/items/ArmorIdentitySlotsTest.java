// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.features.items;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EquipmentSlot;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ArmorIdentitySlotsTest {
    private static final String A="320324d5-5cbd-4ecb-ba5e-5abf8566755a",B="ce493518-1bd8-4c4c-8310-ce8a1f2fcce8";
    private static CompoundTag data(String uuid){var data=new CompoundTag();data.putString("id","WISE_WITHER_CHESTPLATE");data.putString("dye_item","DYE_OCEAN");if(!uuid.isEmpty())data.putString("uuid",uuid);return data;}
    @Test void remembersOnlyAnObservedPhysicalItemInTheSameLocalSlot(){
        var cache=new ArmorIdentitySlots();var player=new Object();var item=new Object();var slot=EquipmentSlot.CHEST;
        assertEquals("",cache.observe(player,slot,item,data("")));
        assertEquals(A,cache.observe(player,slot,item,data(A)));
        assertEquals(A,cache.observe(player,slot,item,data("")));
        assertEquals("",cache.observe(player,EquipmentSlot.LEGS,item,data("")));
        assertEquals(B,cache.observe(player,slot,item,data(B)));
        assertEquals(B,cache.observe(player,slot,item,data("")));
        assertEquals("",cache.observe(new Object(),slot,item,data("")));
    }
    @Test void unequipChangedDyeChangedItemAndUnknownPartialPayloadForgetTheOldIdentity(){
        var cache=new ArmorIdentitySlots();var player=new Object();var item=new Object();var slot=EquipmentSlot.CHEST;
        cache.observe(player,slot,item,data(A));assertEquals("",cache.observe(player,slot,item,null));assertEquals("",cache.observe(player,slot,item,data("")));
        cache.observe(player,slot,item,data(A));var changed=data("");changed.putString("dye_item","DYE_FIREFLY");assertEquals("",cache.observe(player,slot,item,changed));
        cache.observe(player,slot,item,data(A));assertEquals("",cache.observe(player,slot,new Object(),data("")));
        cache.observe(player,slot,item,data(A));var partial=data("");partial.putInt("upgrade_level",5);assertEquals("",cache.observe(player,slot,item,partial));
        cache.observe(player,slot,item,data(A));cache.clear();assertEquals("",cache.observe(player,slot,item,data("")));
    }
}
