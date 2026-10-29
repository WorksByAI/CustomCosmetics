// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.features.items;

import java.util.EnumMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EquipmentSlot;

/** Four local slots. Only the observed Hypixel slim animated-dye payload may retain an identity. */
public final class ArmorIdentitySlots {
    private record Known(Object item,String uuid,String id,String dye){}
    private final EnumMap<EquipmentSlot,Known> slots=new EnumMap<>(EquipmentSlot.class);
    private Object owner;
    public void clear(){owner=null;slots.clear();}
    public String observe(Object player,EquipmentSlot slot,Object item,CompoundTag data){
        if(owner!=player){clear();owner=player;}
        if(data==null){slots.remove(slot);return "";}
        String uuid=ItemCosmetics.uuid(data),id=data.getStringOr("id",""),dye=data.getStringOr("dye_item","");
        if(!uuid.isEmpty()){
            slots.put(slot,new Known(item,uuid,id,dye));return uuid;
        }
        var known=slots.get(slot);
        if(known!=null&&known.item()==item&&data.size()==2&&!id.isEmpty()&&!dye.isEmpty()
                &&id.equals(known.id())&&dye.equals(known.dye()))return known.uuid();
        slots.remove(slot);return "";
    }
}
