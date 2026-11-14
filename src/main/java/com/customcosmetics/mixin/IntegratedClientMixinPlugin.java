package com.customcosmetics.mixin;

import java.util.List;
import java.util.Set;
import net.fabricmc.loader.api.FabricLoader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.spongepowered.asm.service.MixinService;

/** Register the upstream hooks for the running Minecraft version only. */
public final class IntegratedClientMixinPlugin implements IMixinConfigPlugin {
    private boolean active;

    @Override public void onLoad(String mixinPackage) {
        var loader = FabricLoader.getInstance();
        var game = loader.getModContainer("minecraft").orElseThrow().getMetadata().getVersion().getFriendlyString();
        String version = mixinPackage.substring(mixinPackage.lastIndexOf(".v") + 2, mixinPackage.lastIndexOf(".mixin"));
        String upstream = mixinPackage.contains(".bobby.") ? "bobby" : "naturalmotionblur";
        active = game.startsWith("26." + version.charAt(2)) && !loader.isModLoaded(upstream)
            && (upstream.equals("bobby") || !loader.isModLoaded("vulkanmod"));
    }

    @Override public boolean shouldApplyMixin(String target, String mixin) {
        if (!active) return false;
        var loader = FabricLoader.getInstance();
        if (target.startsWith("ca.spottedleaf.starlight.")) return loader.isModLoaded("starlight");
        if (!mixin.contains(".sodium06.")) return true;
        if (!loader.isModLoaded("sodium")) return false;
        try {
            MixinService.getService().getBytecodeProvider()
                .getClassNode("net.caffeinemc.mods.sodium.client.render.chunk.map.ChunkTrackerHolder");
            return true;
        } catch (Exception unavailable) {
            return false;
        }
    }

    @Override public String getRefMapperConfig() { return null; }
    @Override public List<String> getMixins() { return null; }
    @Override public void acceptTargets(Set<String> mine, Set<String> others) {}
    @Override public void preApply(String name, ClassNode target, String mixin, IMixinInfo info) {}
    @Override public void postApply(String name, ClassNode target, String mixin, IMixinInfo info) {}
}
