// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.features.items;

import com.customcosmetics.client.CustomCosmeticsClient;
import com.customcosmetics.config.FeatureModules;
import com.customcosmetics.gui.UiText;
import com.customcosmetics.utils.MessageUtils;
import com.google.gson.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

/** Explicit ten-second, local-only trace. No packet payloads, profiles, account tokens or coordinates. */
public final class ArmorTrace {
    private static final EquipmentSlot[] SLOTS={EquipmentSlot.HEAD,EquipmentSlot.CHEST,EquipmentSlot.LEGS,EquipmentSlot.FEET};
    private record Capture(ArmorTraceBuffer buffer,JsonObject metadata,int playerId,Set<String> ownItems,Set<Integer> models,AtomicInteger stacks){}
    private static volatile Capture capture;
    private static volatile Path lastReport;
    private ArmorTrace(){}
    public static boolean active(){return capture!=null;}
    public static Path lastReport(){return lastReport;}
    private static void message(String en,String zh){MessageUtils.sendMessage(UiText.language().equals("zh_cn")?zh:en);}
    public static int start(){
        var client=Minecraft.getInstance();
        if(client.player==null){message("Join a world before tracing armor.","请先进入游戏世界再诊断护甲。");return 0;}
        if(capture!=null){message("Armor trace is already recording.","护甲诊断正在录制。");return 0;}
        var meta=new JsonObject();meta.addProperty("format",1);meta.addProperty("duration_seconds",10);
        meta.addProperty("client_version",FabricLoader.getInstance().getModContainer("customcosmetics").orElseThrow().getMetadata().getVersion().getFriendlyString());
        meta.addProperty("minecraft",FabricLoader.getInstance().getModContainer("minecraft").orElseThrow().getMetadata().getVersion().getFriendlyString());
        meta.addProperty("items_module",FeatureModules.enabled(FeatureModules.Feature.ITEMS));
        meta.addProperty("local_entity_id",client.player.getId());
        meta.addProperty("graphics_backend",graphicsBackend());
        var store=ItemCosmetics.store();meta.addProperty("appearances_enabled",store!=null&&store.enabled());
        var colors=new JsonObject();if(store!=null)store.armorColorSnapshot().forEach((uuid,color)->colors.addProperty(uuid,hex(color)));meta.add("configured_armor_colors",colors);
        var mods=new JsonArray();for(var mod:FabricLoader.getInstance().getAllMods()){
            var entry=new JsonObject();entry.addProperty("id",mod.getMetadata().getId());entry.addProperty("version",mod.getMetadata().getVersion().getFriendlyString());mods.add(entry);
        }meta.add("mods",mods);
        var own=new HashSet<String>();for(var slot:SLOTS){String uuid=ItemCosmetics.uuid(client.player.getItemBySlot(slot));if(!uuid.isEmpty())own.add(uuid);}
        capture=new Capture(new ArmorTraceBuffer(System.nanoTime()),meta,client.player.getId(),Set.copyOf(own),java.util.concurrent.ConcurrentHashMap.newKeySet(),new AtomicInteger());
        message("Armor trace: stand still for 3 seconds, then move for 7 seconds. Recording stops automatically.","护甲诊断：先站立 3 秒，再移动 7 秒，10 秒后自动结束。");return 1;
    }
    public static void tick(Minecraft client){
        var current=capture;if(current==null)return;
        long now=System.nanoTime();
        if(current.buffer().expired(now)){
            capture=null;var report=current.buffer().finish();
            report.add("metadata",current.metadata());
            Path directory=client.gameDirectory.toPath().resolve("logs");
            CompletableFuture.runAsync(()->{
                try{
                    Files.createDirectories(directory);Path path=Files.createTempFile(directory,"customcosmetics-armor-trace-",".json");
                    Files.writeString(path,new GsonBuilder().setPrettyPrinting().create().toJson(report),StandardCharsets.UTF_8);lastReport=path;
                    CustomCosmeticsClient.LOGGER.info("[Armor trace] Saved {}",path);
                    client.execute(()->message("Armor trace saved: "+path.getFileName(),"护甲诊断已保存：logs/"+path.getFileName()));
                }catch(Exception error){CustomCosmeticsClient.LOGGER.warn("Could not save armor trace",error);client.execute(()->message("Could not save armor trace. See latest.log.","护甲诊断保存失败，请查看 latest.log。"));}
            });return;
        }
        if(client.player==null)return;
        for(var slot:SLOTS){
            if(!current.buffer().allow("inventory:"+slot,now))continue;
            var row=base("inventory",null);row.addProperty("slot",slot.getName());item(row,client.player.getItemBySlot(slot));current.buffer().add(row,now);
        }
    }
    private static boolean own(Capture current,Object state,ItemStack stack){
        return state instanceof AvatarRenderState living&&living.id==current.playerId()
            ||stack!=null&&current.ownItems().contains(ItemCosmetics.uuid(stack));
    }
    public static void bind(Object model,Object state,ItemStack stack,Object collector,int selected){
        var current=capture;if(current==null||!own(current,state,stack))return;
        if(current.models().size()<64)current.models().add(System.identityHashCode(model));
        long now=System.nanoTime();var equipment=stack.get(DataComponents.EQUIPPABLE);
        String slot=equipment==null?"unknown":equipment.slot().getName();
        if(!current.buffer().allow("bind:"+slot,now))return;
        var row=base("bind",state);row.addProperty("slot",slot);item(row,stack);
        row.addProperty("model",model.getClass().getName());row.addProperty("model_identity",System.identityHashCode(model));
        row.addProperty("collector",collector.getClass().getName());row.addProperty("selected",hex(selected));stackTrace(current,row);current.buffer().add(row,now);
    }
    public static void layer(Object state,ItemStack stack,int before,int after,boolean dyeable){
        var current=capture;if(current==null||!own(current,state,stack))return;
        long now=System.nanoTime();String uuid=ItemCosmetics.uuid(stack);if(!current.buffer().allow("layer:"+uuid+":"+dyeable,now,signature(before,after)))return;
        var row=base("layer",state);item(row,stack);row.addProperty("input",hex(before));row.addProperty("output",hex(after));row.addProperty("dyeable",dyeable);current.buffer().add(row,now);
    }
    public static void submit(Object model,Object state,Object type,int before,int after,boolean found,boolean stateMatch,boolean textureOverride,int desired){
        var current=capture;if(current==null||!own(current,state,null)&&!current.models().contains(System.identityHashCode(model)))return;
        long now=System.nanoTime();if(!current.buffer().allow("submit:"+System.identityHashCode(model)+":"+System.identityHashCode(type),now,signature(before,after)))return;
        var row=base("submit",state);row.addProperty("model",model.getClass().getName());row.addProperty("model_identity",System.identityHashCode(model));
        row.addProperty("material",String.valueOf(type));row.addProperty("input",hex(before));row.addProperty("output",hex(after));row.addProperty("desired",hex(desired));
        row.addProperty("binding_found",found);row.addProperty("state_match",stateMatch);row.addProperty("texture_override",textureOverride);stackTrace(current,row);current.buffer().add(row,now);
    }
    public static void finalSubmit(Object model,Object state,Object type,int color){
        var current=capture;if(current==null||!own(current,state,null)&&!current.models().contains(System.identityHashCode(model)))return;
        long now=System.nanoTime();if(!current.buffer().allow("completed:"+System.identityHashCode(model)+":"+System.identityHashCode(type),now,color))return;
        var row=base("completed_submit",state);row.addProperty("model",model.getClass().getName());row.addProperty("model_identity",System.identityHashCode(model));row.addProperty("material",String.valueOf(type));row.addProperty("color",hex(color));current.buffer().add(row,now);
    }
    private static JsonObject base(String stage,Object state){
        var row=new JsonObject();row.addProperty("stage",stage);row.addProperty("thread",Thread.currentThread().getName());
        if(state!=null){row.addProperty("state",state.getClass().getName());row.addProperty("state_identity",System.identityHashCode(state));if(state instanceof AvatarRenderState living)row.addProperty("state_entity_id",living.id);}
        var player=Minecraft.getInstance().player;if(player!=null)row.addProperty("horizontal_motion",player.getDeltaMovement().horizontalDistanceSqr());return row;
    }
    private static void item(JsonObject row,ItemStack stack){
        String uuid=ItemCosmetics.uuid(stack);row.addProperty("item_uuid",uuid);row.addProperty("item",net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
        var data=stack.get(DataComponents.CUSTOM_DATA);
        if(data!=null){
            var tag=data.copyTag();var extra=tag.getCompound("ExtraAttributes").orElse(tag);
            row.addProperty("skyblock_id",tag.getStringOr("id",extra.getStringOr("id","")));
            var identity=tag.contains("uuid")?tag.get("uuid"):extra.get("uuid");
            if(identity!=null)row.addProperty("uuid_tag_type",identity.getClass().getSimpleName());
        }
        var tint=stack.get(DataComponents.DYED_COLOR);if(tint!=null)row.addProperty("component_color",hex(tint.rgb()));
        var appearance=ItemCosmetics.store()==null?null:ItemCosmetics.store().get(uuid);
        if(appearance!=null&&appearance.armorColor()!=null)row.addProperty("configured_color",hex(appearance.armorColor()));
        if(appearance!=null)row.addProperty("hide_glint",appearance.hideGlint());
    }
    private static void stackTrace(Capture current,JsonObject row){
        if(current.stacks().getAndIncrement()>=12)return;
        var frames=new JsonArray();var stack=Thread.currentThread().getStackTrace();for(int i=2;i<Math.min(stack.length,24);i++)frames.add(stack[i].toString());row.add("call_stack",frames);
    }
    private static String hex(int color){return String.format(Locale.ROOT,"#%08X",color);}
    private static long signature(int before,int after){return ((long)before<<32)^(after&0xFFFFFFFFL);}
    // Diagnostic metadata only. The GPU device return type changes between 26.2 and 26.3.
    private static String graphicsBackend(){
        try{
            var device=com.mojang.blaze3d.systems.RenderSystem.class.getMethod("getDevice");
            var info=device.getReturnType().getMethod("getDeviceInfo");
            return String.valueOf(info.getReturnType().getMethod("backendName").invoke(info.invoke(device.invoke(null))));
        }catch(ReflectiveOperationException|LinkageError unavailable){return "unavailable";}
    }
}
