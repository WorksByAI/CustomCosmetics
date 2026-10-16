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
        if(mixin.endsWith("MapEditorMixin$Sodium"))return FabricLoader.getInstance().isModLoaded("sodium");
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
