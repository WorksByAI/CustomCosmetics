// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.features.items;

import com.customcosmetics.compatibility.CatharsisArmorAccess;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.world.item.ItemStack;

/** Bounded, reused bindings for Catharsis extra draws; no edits to inventory or pack states. */
public final class ArmorSubmissionTint {
    private static final Map<Model<?>,Binding> BINDINGS=new WeakHashMap<>();
    private static final Map<RenderType,Boolean> TRIMS=new WeakHashMap<>();
    private static boolean active;
    private static final class Binding { Object state; int color; boolean textureOverride; }
    private ArmorSubmissionTint() {}
    public static void bind(Model<?> model,Object state,ItemStack stack){
        int color=ArmorAppearance.color(stack,0);
        var previous=BINDINGS.get(model);
        if(color==0){
            if(previous!=null){previous.state=null;previous.color=0;}
            return;
        }
        if(previous==null){
            if(BINDINGS.size()>=64)BINDINGS.clear();
            previous=new Binding();BINDINGS.put(model,previous);
        }
        previous.state=state;previous.color=color;previous.textureOverride=CatharsisArmorAccess.usesTexture(state,stack);active=true;
    }
    public static int color(Model<?> model,Object state,RenderType type,int original){
        int selected=resolveColor(model,state,type,original);
        if(ArmorTrace.active()){
            var binding=BINDINGS.get(model);
            ArmorTrace.submit(model,state,type,original,selected,binding!=null,binding!=null&&binding.state==state,binding!=null&&binding.textureOverride,binding==null?0:binding.color);
        }
        return selected;
    }
    private static int resolveColor(Model<?> model,Object state,RenderType type,int original){
        if(!active||!com.customcosmetics.config.FeatureModules.enabled(com.customcosmetics.config.FeatureModules.Feature.ITEMS))return original;
        var store=ItemCosmetics.store();
        if(store==null||!store.enabled()||!store.hasArmorColors())return original;
        var binding=BINDINGS.get(model);
        if(binding==null||binding.state!=state||binding.color==0)return original;
        // Vanilla non-dyeable detail overlays are white; its dyeable layers were already tinted.
        if(!binding.textureOverride&&original==0xFFFFFFFF)return original;
        // Trim decals retain their authored colors.
        var trim=TRIMS.get(type);
        if(trim==null){if(TRIMS.size()>=128)TRIMS.clear();trim=type.toString().contains("armor_trims");TRIMS.put(type,trim);}
        return trim?original:binding.color;
    }
}
