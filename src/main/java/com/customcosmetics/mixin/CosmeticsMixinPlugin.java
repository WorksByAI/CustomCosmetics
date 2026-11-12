package com.customcosmetics.mixin;

import net.fabricmc.loader.api.FabricLoader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import java.util.List;
import java.util.Set;

public final class CosmeticsMixinPlugin implements IMixinConfigPlugin {
    @Override public void onLoad(String mixinPackage) {}
    @Override public String getRefMapperConfig() { return null; }
    @Override public boolean shouldApplyMixin(String target, String mixin) {
        if(mixin.contains(".Sky")){
            var loader=FabricLoader.getInstance();
            if(loader.isModLoaded("skyboxify"))return false;
            String version=loader.getModContainer("minecraft").orElseThrow().getMetadata().getVersion().getFriendlyString();
            if(mixin.contains("SkyPass26_"))return version.startsWith("26."+mixin.charAt(mixin.indexOf("SkyPass26_")+10));
        }
        if(mixin.contains("BorderlessWindowMixin$")) {
            var loader=FabricLoader.getInstance();
            return !loader.isModLoaded("cwb") && mixin.endsWith("Modern") == loader.getModContainer("minecraft").orElseThrow().getMetadata().getVersion().getFriendlyString().startsWith("26.3");
        }
        if(mixin.endsWith("FastIpPingMixin"))return !FabricLoader.getInstance().isModLoaded("fastipping");
        if(mixin.contains(".HypixelPackDisablerMixin$"))return !FabricLoader.getInstance().isModLoaded("packdisabler");
        if (mixin.contains(".DevonianReloadMixin$")) {
            var loader = FabricLoader.getInstance();
            return loader.isModLoaded("devonian") && !loader.getModContainer("minecraft").orElseThrow()
                .getMetadata().getVersion().getFriendlyString().startsWith("26.1");
        }
        if(mixin.contains(".InventoryBlurMixin"))return !FabricLoader.getInstance().isModLoaded("eg_inventory_blur");
        if(mixin.contains(".LongSignTextMixin$"))return !FabricLoader.getInstance().isModLoaded("unlimitedsigntext");
        if(mixin.endsWith("HudLayoutMixin$Hud")) {
            var version = FabricLoader.getInstance().getModContainer("minecraft").orElseThrow().getMetadata().getVersion().getFriendlyString();
            return target.endsWith(version.startsWith("26.1") ? ".Gui" : ".Hud");
        }
        if(mixin.contains("HudLayoutMixin$")&&(mixin.endsWith("Modern")||mixin.endsWith("Legacy")||mixin.endsWith("Blit261"))) {
            var version=FabricLoader.getInstance().getModContainer("minecraft").orElseThrow().getMetadata().getVersion().getFriendlyString();
            if(mixin.endsWith("Blit261"))return version.startsWith("26.1");
            if(mixin.endsWith("$BlitLegacy"))return version.startsWith("26.2");
            return mixin.endsWith("Modern")==version.startsWith("26.3");
        }
        if(mixin.contains("ArmorSubmissionMixin$")){
            boolean modern=FabricLoader.getInstance().getModContainer("minecraft").orElseThrow().getMetadata().getVersion().getFriendlyString().startsWith("26.3");
            return mixin.endsWith("Bind")||mixin.endsWith(modern?"Modern":"Legacy");
        }
        if(mixin.endsWith("UiTooltipMixin$Legacy")||mixin.endsWith("UiTooltipMixin$Modern"))return mixin.endsWith("Modern")==FabricLoader.getInstance().getModContainer("minecraft").orElseThrow().getMetadata().getVersion().getFriendlyString().startsWith("26.3");
        if (mixin.contains("LocalFontSampling")) {
            var version = FabricLoader.getInstance().getModContainer("minecraft").orElseThrow().getMetadata().getVersion().getFriendlyString();
            return mixin.contains("263Mixin") == version.startsWith("26.3");
        }
        if (mixin.endsWith("CreaturePlayerEffectsMixin")) return FabricLoader.getInstance().getModContainer("minecraft").orElseThrow()
            .getMetadata().getVersion().getFriendlyString().startsWith("26.3");
        if(mixin.contains("MapEditorMixin$Sodium"))return FabricLoader.getInstance().isModLoaded("sodium");
        if(mixin.endsWith("ChamsMixin$Targets"))return !FabricLoader.getInstance().getModContainer("minecraft").orElseThrow().getMetadata().getVersion().getFriendlyString().startsWith("26.3");
        if(mixin.contains("ModernUiNicknameMixin$"))return FabricLoader.getInstance().isModLoaded("modernui");
        if (mixin.contains("ItemModelVisibilityMixin$") && !mixin.endsWith("State")) {
            boolean modern = FabricLoader.getInstance().getModContainer("minecraft").orElseThrow()
                .getMetadata().getVersion().getFriendlyString().startsWith("26.3");
            return mixin.endsWith("Modern") == modern;
        }
        if (mixin.contains("SwingAnimationMixin$")) {
            boolean modern = FabricLoader.getInstance().getModContainer("minecraft").orElseThrow()
                .getMetadata().getVersion().getFriendlyString().startsWith("26.3");
            return mixin.endsWith("Modern") == modern;
        }
        if (mixin.endsWith("SwordBlockingMixin$ModernEquipment")) return FabricLoader.getInstance().getModContainer("minecraft").orElseThrow()
            .getMetadata().getVersion().getFriendlyString().startsWith("26.3");
        if (mixin.contains("SwordBlockingMixin$FirstPerson")) {
            boolean modern = FabricLoader.getInstance().getModContainer("minecraft").orElseThrow()
                .getMetadata().getVersion().getFriendlyString().startsWith("26.3");
            return mixin.endsWith("FirstPersonModern") == modern;
        }
        return !mixin.contains("ModMenuNickTweaksMixin") || FabricLoader.getInstance().isModLoaded("modmenu");
    }
    @Override public void acceptTargets(Set<String> mine, Set<String> others) {}
    @Override public List<String> getMixins() { return null; }
    @Override public void preApply(String target, ClassNode node, String mixin, IMixinInfo info) {}
    @Override public void postApply(String target, ClassNode node, String mixin, IMixinInfo info) {}
}
