// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.features.items;

import com.customcosmetics.config.FeatureModules;
import java.util.EnumMap;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/** Recovers a known local armor UUID in render copies only, never in inventory or packet stacks. */
public final class ArmorItemIdentity {
    private static final EquipmentSlot[] SLOTS={EquipmentSlot.HEAD,EquipmentSlot.CHEST,EquipmentSlot.LEGS,EquipmentSlot.FEET};
    private static final ArmorIdentitySlots IDENTITIES=new ArmorIdentitySlots();
    private static final EnumMap<EquipmentSlot,Observed> OBSERVED=new EnumMap<>(EquipmentSlot.class);
    private static final java.util.IdentityHashMap<ItemStack,Observed> LOCAL_ITEMS=new java.util.IdentityHashMap<>();
    private static Object owner;
    private static final class Observed {
        ItemStack source;CustomData data,renderData;String uuid;
        Observed(ItemStack source,CustomData data,String uuid){this.source=source;this.data=data;this.uuid=uuid;}
    }
    private ArmorItemIdentity(){}
    private static boolean enabled(){
        var store=ItemCosmetics.store();
        return FeatureModules.enabled(FeatureModules.Feature.ITEMS)&&store!=null&&store.enabled()
            &&(store.hasArmorColors()||store.hasArmorModels()||store.hasHiddenGlints());
    }
    private static void clear(){owner=null;OBSERVED.clear();LOCAL_ITEMS.clear();IDENTITIES.clear();}
    public static void tick(){
        if(!enabled()){if(owner!=null)clear();return;}
        var client=Minecraft.getInstance();
        if(client.player==null){if(owner!=null)clear();return;}
        if(!client.isSameThread())return;
        if(owner!=client.player){clear();owner=client.player;}
        for(var slot:SLOTS)observe(client.player,slot,client.player.getItemBySlot(slot));
    }
    private static Observed observe(Object player,EquipmentSlot slot,ItemStack source){
        var data=source.get(DataComponents.CUSTOM_DATA);var last=OBSERVED.get(slot);
        if(last!=null&&last.source==source&&last.data==data)return last;
        if(last!=null)LOCAL_ITEMS.remove(last.source);
        String uuid=IDENTITIES.observe(player,slot,source.getItem(),data==null?null:data.copyTag());
        var observed=new Observed(source,data,uuid);OBSERVED.put(slot,observed);
        if(!uuid.isEmpty())LOCAL_ITEMS.put(source,observed);
        return observed;
    }
    /** GUI icons use the actual local stack, rather than the humanoid renderer's decorated copy. */
    public static String localAppearanceKey(ItemStack stack){
        if(LOCAL_ITEMS.isEmpty()||!enabled())return "";
        var client=Minecraft.getInstance();
        if(!client.isSameThread()||client.player==null||owner!=client.player)return "";
        var known=LOCAL_ITEMS.get(stack);
        return known!=null&&known.data==stack.get(DataComponents.CUSTOM_DATA)?known.uuid:"";
    }
    public static void equipment(int entityId){
        if(!enabled())return;
        var client=Minecraft.getInstance();
        if(client.isSameThread()&&client.player!=null&&client.player.getId()==entityId)tick();
    }
    public static ItemStack render(LivingEntity entity,EquipmentSlot slot,ItemStack rendered){
        if(!enabled()||rendered.isEmpty())return rendered;
        var client=Minecraft.getInstance();
        if(entity!=client.player||!client.isSameThread())return rendered;
        if(owner!=entity){clear();owner=entity;}
        var source=entity.getItemBySlot(slot);var observed=observe(entity,slot,source);
        if(observed.uuid.isEmpty()||!ItemCosmetics.uuid(rendered).isEmpty()||observed.data==null
                ||rendered.get(DataComponents.CUSTOM_DATA)!=observed.data)return rendered;
        if(observed.renderData==null){
            var data=observed.data.copyTag();data.putString("uuid",observed.uuid);observed.renderData=CustomData.of(data);
        }
        // Vanilla returns a copy; retain that boundary if another mod returns the actual equipped stack.
        if(rendered==source)rendered=rendered.copy();
        rendered.set(DataComponents.CUSTOM_DATA,observed.renderData);return rendered;
    }
}
