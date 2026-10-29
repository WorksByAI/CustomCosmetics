// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.gametest;

import com.customcosmetics.compatibility.ClientUiAccess;
import com.customcosmetics.features.items.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.DyedItemColor;

/** Observe actual GUI tint layers, including the first draw after repeatedly opening inventory. */
public final class ArmorInventoryGameTest {
    private static final String UUID="12345678-1234-1234-1234-123456789abe";
    private static final EquipmentSlot[] SLOTS={EquipmentSlot.CHEST,EquipmentSlot.LEGS,EquipmentSlot.FEET};
    private static final List<String> ERRORS=new ArrayList<>();
    public static boolean tracking;
    private static int draws,black;
    public static void record(ItemStackRenderState state,ItemStack stack){
        if(!tracking)return;
        var player=Minecraft.getInstance().player;boolean own=false;
        for(var slot:SLOTS)if(player.getItemBySlot(slot)==stack){own=true;break;}
        if(!own)return;
        draws++;boolean found=false;
        var layers=(ItemModelVisibility.State)state;
        for(int i=0;i<layers.customcosmetics$layerCount();i++){
            var layer=layers.customcosmetics$layers()[i];var tints=layer.tintLayers();
            for(int j=0;j<tints.size();j++){
                int color=tints.getInt(j);if(color==0xFF000000)found=true;
                else if(color!=0xFFFFFFFF&&ERRORS.size()<16)ERRORS.add("GUI tint "+Integer.toHexString(color));
            }
            try{var foil=layer.getClass().getDeclaredField("foilType");foil.setAccessible(true);if(!foil.get(layer).toString().equals("NONE")&&ERRORS.size()<16)ERRORS.add("GUI glint was retained");}
            catch(ReflectiveOperationException error){throw new AssertionError(error);}
        }
        if(found)black++;else if(ERRORS.size()<16)ERRORS.add("No black tint in actual armor icon");
    }
    public static void check(ClientGameTestContext context,TestSingleplayerContext world){
        world.getServer().runCommand("gamemode survival @a");context.waitTicks(2);
        context.runOnClient(c->{ItemCosmetics.store().enabled(true);ItemCosmetics.store().put(UUID,new ItemAppearanceStore.Appearance("","","",0,"",true));draws=black=0;ERRORS.clear();});
        equip(world,true,0x0554C9);context.waitTicks(5);
        var retired=new AtomicReference<ItemStack>();
        try{
            for(int open=0;open<4;open++){
                equip(world,false,(open&1)==0?0x064DC3:0x0554C9);
                context.waitFor(c->ItemCosmetics.uuid(c.player.getItemBySlot(EquipmentSlot.CHEST)).isEmpty());
                context.runOnClient(c->{
                    var source=c.player.getItemBySlot(EquipmentSlot.CHEST);var data=source.get(DataComponents.CUSTOM_DATA);
                    if(data==null||data.copyTag().size()!=2||data.copyTag().contains("uuid"))throw new AssertionError("Fixture or real inventory was modified");
                    if(retired.get()==null)retired.set(source);
                    var other=source.copy();int original=0xFF000000|source.get(DataComponents.DYED_COLOR).rgb();
                    if(DyedItemColor.getOrDefault(other,0)!=original||!other.hasFoil())throw new AssertionError("Another identical-looking stack inherited the local appearance");
                    tracking=true;ClientUiAccess.setScreen(c,new InventoryScreen(c.player));
                });
                context.waitTicks(3);
                context.takeScreenshot("armor-inventory-open-"+open);
                context.runOnClient(c->{tracking=false;ClientUiAccess.setScreen(c,null);});context.waitTicks(1);
            }
            context.runOnClient(c->{
                var old=retired.get();int original=0xFF000000|old.get(DataComponents.DYED_COLOR).rgb();
                if(DyedItemColor.getOrDefault(old,0)!=original)throw new AssertionError("Retired equipment retained an appearance alias");
                if(draws<12||black!=draws||!ERRORS.isEmpty())throw new AssertionError("ARMOR_INVENTORY_ICON_FLASH draws="+draws+" black="+black+" errors="+ERRORS);
                var source=c.player.getItemBySlot(EquipmentSlot.CHEST);
                if(!Boolean.TRUE.equals(source.get(DataComponents.ENCHANTMENT_GLINT_OVERRIDE))||source.get(DataComponents.CUSTOM_DATA).copyTag().contains("uuid"))throw new AssertionError("Display changes modified server components");
                System.out.println("ARMOR_INVENTORY_FIRST_DRAW_BLACK_NO_GLINT_OK draws="+draws+" copies_and_retired_stacks_unchanged=true");
                ClientUiAccess.chat(c).clearMessages(true);
            });
        }finally{context.runOnClient(c->{tracking=false;ClientUiAccess.setScreen(c,null);});}
    }
    private static void equip(TestSingleplayerContext world,boolean full,int color){
        for(var slot:SLOTS){String name=slot.getName();String item=switch(slot){case CHEST->"chestplate";case LEGS->"leggings";default->"boots";};
            world.getServer().runCommand("item replace entity @a armor."+name+" with minecraft:leather_"+item+"[minecraft:enchantment_glint_override=true,minecraft:dyed_color="+color+",minecraft:custom_data={id:\"WISE_WITHER_"+item.toUpperCase(java.util.Locale.ROOT)+"\",dye_item:\"DYE_OCEAN\""+(full?",uuid:\""+UUID+"\"":"")+"}]");
        }
    }
}
