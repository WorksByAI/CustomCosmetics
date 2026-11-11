// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.features.hud;

import com.customcosmetics.config.ConfigManager;
import com.customcosmetics.config.FeatureModules;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.ARGB;

/** Local HUD settings. The inactive path leaves the vanilla extraction untouched. */
public final class HudLayout {
    public interface OpacityState { float ccos$opacity(); }
    public enum Element {
        ACTION_BAR("Action Bar"), BOSS_BAR("Boss Bar"), SUBTITLES("Subtitles"), CROSSHAIR("Crosshair"),
        DEBUG("Debug Screen"), HOTBAR("Hotbar Group"), ITEM_NAME("Selected Item Name"), TAB("Player List"),
        SCOREBOARD("Scoreboard"), TITLE("Screen Title"), EFFECTS("Status Effects"), TOAST("Toast Messages"),
        TOOLTIP("Tooltips");
        public final String label;
        Element(String label){this.label=label;}
    }
    public record Options(boolean visible, float scale, float opacity, float x, float y) {
        public static final Options DEFAULT=new Options(true,1,1,0,0);
        public Options {
            scale=bounded(scale,1,.25f,3);opacity=bounded(opacity,1,0,1);
            x=bounded(x,0,-4096,4096);y=bounded(y,0,-4096,4096);
        }
        private static float bounded(float v,float fallback,float min,float max){
            return Float.isFinite(v)?Math.clamp(v,min,max):fallback;
        }
    }
    private static float alpha=1;
    private HudLayout(){}
    public static Options options(Element element){return ConfigManager.hudLayout.getOrDefault(element,Options.DEFAULT);}
    public static Options active(Element element){
        if(!ConfigManager.hudLayoutEnabled||!com.customcosmetics.sync.ExperimentalAccess.allowed()||!FeatureModules.enabled(FeatureModules.Feature.HUD_LAYOUT))return null;
        return ConfigManager.hudLayout.get(element);
    }
    public static void set(Element element,Options options){
        if(options.equals(Options.DEFAULT))ConfigManager.hudLayout.remove(element);
        else ConfigManager.hudLayout.put(element,options);
    }
    public static float opacity(){return alpha;}
    public static int tint(int color){return alpha==1?color:ARGB.multiplyAlpha(color,alpha);}
    public static float opacity(float opacity){float old=alpha;alpha=opacity;return old;}
    public static float push(GuiGraphicsExtractor graphics,Options option){
        var pose=graphics.pose();pose.pushMatrix();
        pose.translate(option.x(),option.y()).scale(option.scale(),option.scale());
        return opacity(alpha*option.opacity());
    }
    public static void pop(GuiGraphicsExtractor graphics,float previous){
        opacity(previous);graphics.pose().popMatrix();
    }
}
