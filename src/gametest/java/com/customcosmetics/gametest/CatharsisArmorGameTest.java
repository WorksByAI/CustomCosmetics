// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.gametest;

import com.customcosmetics.features.items.*;
import java.lang.reflect.Method;
import java.util.Set;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.Identifier;

/** Exercises the installed Catharsis implementation's direct draw path, rather than imitating dye lookups. */
public final class CatharsisArmorGameTest {
    public static final String UUID="12345678-1234-1234-1234-123456789abe";
    public static boolean track, customPath;
    public static final Set<Integer> submitted=java.util.concurrent.ConcurrentHashMap.newKeySet();
    private static Method getState,setChest,getChest;
    public static int customDraws;
    private static Object blue;
    public static void injectTexture(LivingEntityRenderState state){
        if(!track)return;
        try{
            if(getState==null){
                var hook=Class.forName("me.owdding.catharsis.hooks.armor.LivingEntityRenderStateHook");
                var definition=Class.forName("me.owdding.catharsis.features.armor.ArmorDefinitionRenderState");
                var model=Class.forName("me.owdding.catharsis.features.armor.models.ArmorModelState");
                var texture=Class.forName("me.owdding.catharsis.features.armor.models.ArmorModelState$Texture");
                getState=hook.getMethod("catharsis$getArmorDefinitionRenderState");setChest=definition.getMethod("setChest",model);getChest=definition.getMethod("getChest");
                blue=texture.getConstructor(Identifier[].class,int[].class,boolean.class).newInstance(new Identifier[]{Identifier.parse("minecraft:textures/entity/equipment/humanoid/leather.png")},new int[]{0x0554C9},false);
            }
            setChest.invoke(getState.invoke(state),customPath?blue:null);
        }catch(ReflectiveOperationException error){throw new AssertionError("Catharsis fixture API mismatch",error);}
    }
    public static void record(Object state,int color){
        try{if(getChest.invoke(getState.invoke(state))==blue)customDraws++;}catch(ReflectiveOperationException error){throw new AssertionError(error);}
        if(color!=0xFFFFFFFF)submitted.add(color);
    }
    public static void check(ClientGameTestContext context,TestSingleplayerContext world){
        if(!net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("catharsis"))throw new AssertionError("Real Catharsis is required");
        world.getServer().runCommand("item replace entity @a armor.chest with minecraft:leather_chestplate[minecraft:dyed_color=349385,minecraft:custom_data={uuid:\""+UUID+"\"}]");
        context.waitFor(c->ItemCosmetics.uuid(c.player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST)).equals(UUID));
        context.runOnClient(c->{ItemCosmetics.store().enabled(true);ItemCosmetics.store().put(UUID,new ItemAppearanceStore.Appearance("","","",0));c.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_FRONT);track=true;submitted.clear();});
        try{
            for(int i=0;i<10;i++){
                final int phase=i;context.runOnClient(c->{customPath=(phase&1)==1;c.player.setYRot(phase*20);c.player.setPos(c.player.getX()+.08,c.player.getY(),c.player.getZ());});context.waitTicks(3);
            }
            context.takeScreenshot("catharsis-direct-armor-tint");
            context.runOnClient(c->{if(customDraws==0)throw new AssertionError("Real Catharsis texture path was never exercised");if(!submitted.equals(Set.of(0xFF000000)))throw new AssertionError("REAL_CATHARSIS_ARMOR_TINT_BYPASS "+submitted+" customDraws="+customDraws);ItemCosmetics.store().put(UUID,new ItemAppearanceStore.Appearance("",""));customPath=true;submitted.clear();});context.waitTicks(3);
            context.runOnClient(c->{if(!submitted.equals(Set.of(0xFF0554C9)))throw new AssertionError("Clearing the custom color did not restore Catharsis");});
            System.out.println("REAL_CATHARSIS_AND_SKYOCEAN_MOVING_DIRECT_DRAW_BLACK_AND_RESTORE_OK");
        }finally{context.runOnClient(c->{track=false;customPath=false;c.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);});}
    }
}
