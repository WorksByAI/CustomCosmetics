// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.gametest;

import com.customcosmetics.features.items.*;
import com.google.gson.*;
import net.minecraft.core.component.DataComponents;
import java.nio.file.Files;
import java.util.HashSet;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

/** Natural installed-pack armor path: no injected Catharsis state or simulated render hook. */
final class ArmorTraceGameTest {
    private static final String UUID="12345678-1234-1234-1234-123456789abe";
    static void check(ClientGameTestContext context,TestSingleplayerContext world){
        world.getServer().runCommand("fill ~-50 ~-1 ~-50 ~50 ~-1 ~50 minecraft:stone");
        for(String slot:new String[]{"head","chest","legs","feet"}){
            String item=switch(slot){case "head"->"helmet";case "chest"->"chestplate";case "legs"->"leggings";default->"boots";};
            world.getServer().runCommand("item replace entity @a armor."+slot+" with minecraft:leather_"+item+"[minecraft:dyed_color=349385,minecraft:custom_data={id:\"POWER_WITHER_"+item.toUpperCase(java.util.Locale.ROOT)+"\",uuid:\""+UUID+"\",dye_item:\"DYE_OCEAN\"}]");
        }
        context.waitFor(c->ItemCosmetics.uuid(c.player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST)).equals(UUID));
        context.runOnClient(c->{ItemCosmetics.store().enabled(true);ItemCosmetics.store().put(UUID,new ItemAppearanceStore.Appearance("","","",0));c.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_FRONT);c.options.keyUp.setDown(false);ArmorTrace.start();});
        context.waitTicks(50);context.takeScreenshot("full-pack-armor-standing");
        context.getInput().holdKey(options->options.keyUp);
        context.getInput().holdKey(options->options.keySprint);
        try{
            for(int phase=0;phase<50;phase++){
                int color=(phase&1)==0?0x064DC3:0x0554C9;
                // Exercise real inventory/equipment packets while sprinting, as animated server dyes do.
                for(String slot:new String[]{"chest","legs","feet"}){
                    String item=switch(slot){case "chest"->"chestplate";case "legs"->"leggings";default->"boots";};
                    String identity=Boolean.getBoolean("customcosmetics.slimArmorTest")?"":",uuid:\""+UUID+"\"";
                    world.getServer().runCommand("item replace entity @a armor."+slot+" with minecraft:leather_"+item+"[minecraft:dyed_color="+color+",minecraft:custom_data={id:\"POWER_WITHER_"+item.toUpperCase(java.util.Locale.ROOT)+"\""+identity+",dye_item:\"DYE_OCEAN\"}]");
                }
                context.waitTicks(2);
            }
            context.takeScreenshot("full-pack-armor-moving");
        }
        finally{context.getInput().releaseKey(options->options.keyUp);context.getInput().releaseKey(options->options.keySprint);}
        context.waitFor(c->ArmorTrace.lastReport()!=null);
        context.runOnClient(c->{
            try{
                if(ArmorTrace.active())throw new AssertionError("Trace did not stop");
                var report=JsonParser.parseString(Files.readString(ArmorTrace.lastReport())).getAsJsonObject();
                var events=report.getAsJsonArray("events");var stages=new HashSet<String>();int armorDraws=0,blueDraws=0;boolean moved=false;
                for(var value:events){var event=value.getAsJsonObject();stages.add(event.get("stage").getAsString());
                    if(event.has("horizontal_motion")&&event.get("horizontal_motion").getAsDouble()>.001)moved=true;
                    if(event.get("stage").getAsString().equals("completed_submit")&&event.get("material").getAsString().contains("armor")&&!event.get("material").getAsString().contains("glint")){
                        armorDraws++;String color=event.get("color").getAsString();if(!color.equals("#FF000000")&&!color.equals("#FFFFFFFF"))blueDraws++;
                    }
                }
                if(events.size()>ArmorTraceBuffer.MAX_EVENTS||!stages.containsAll(java.util.Set.of("inventory","bind","submit","completed_submit"))||!moved||armorDraws==0)
                    throw new AssertionError("Incomplete armor trace: "+stages+" moved="+moved+" draws="+armorDraws);
                if(Boolean.getBoolean("customcosmetics.slimArmorTest")){
                    var raw=c.player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST).get(DataComponents.CUSTOM_DATA);
                    if(raw==null||raw.copyTag().size()!=2||raw.copyTag().contains("uuid"))throw new AssertionError("Visual identity recovery changed the actual inventory components");
                    if(blueDraws!=0)throw new AssertionError("SLIM_ANIMATED_ARMOR_LOST_CUSTOM_COLOR draws="+blueDraws);
                }
                System.out.println("ARMOR_TRACE_NATIVE_OK events="+events.size()+" armorDraws="+armorDraws+" nonCustomDraws="+blueDraws+" report="+ArmorTrace.lastReport());
                com.customcosmetics.compatibility.ClientUiAccess.chat(c).clearMessages(true);
            }catch(java.io.IOException error){throw new AssertionError(error);}
        });
    }
}
