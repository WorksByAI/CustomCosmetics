// SPDX-License-Identifier: BSD-3-Clause
// Adapted from Athen StunHelper and Nebulune Stunner, copyright 2025–2026 Starred.
package com.customcosmetics.features.gameplay;

import com.customcosmetics.compatibility.ClientUiAccess;
import com.customcosmetics.compatibility.PartyRenderPipeline;
import com.customcosmetics.config.ConfigManager;
import com.customcosmetics.features.party.PartyGeometry;
import com.customcosmetics.utils.MessageUtils;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Tracks Human Cannonball and checks Pickobulus against Kuudra's pods. */
public final class KuudraStunHelper {
    private record Pod(AABB area, AABB block) {
        Pod(int x1, int y1, int z1, int x2, int y2, int z2, int x, int y, int z) {
            this(new AABB(Math.min(x1, x2), Math.min(y1, y2), Math.min(z1, z2), Math.max(x1, x2) + 1,
                Math.max(y1, y2) + 1, Math.max(z1, z2) + 1), new AABB(new BlockPos(x, y, z)));
        }
    }
    private static final Pod[] PODS = {new Pod(-150,31,-173,-154,24,-170,-153,27,-173),
        new Pod(-153,31,-153,-156,25,-157,-156,28,-157), new Pod(-168,31,-166,-170,24,-169,-168,27,-169)};
    private static final BlockPos BELLY = new BlockPos(-161, 49, -186);
    private static KeyMapping override;
    private static boolean stunning, belly;
    private static int closeAt;
    private static AbstractContainerMenu closeMenu;
    private static long lastAlert;

    private KuudraStunHelper() {}

    static void init() {
        override = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.customcosmetics.kuudra_stun_override",
            InputConstants.UNKNOWN.getValue(),
            KeyMapping.Category.register(Identifier.fromNamespaceAndPath("customcosmetics", "kuudra"))));
    }

    public static KeyMapping overrideKey() { return override; }

    static void reset() {
        stunning = belly = false;
        closeMenu = null;
        lastAlert = 0;
    }

    static void purchase(Minecraft client) {
        stunning = true;
        if (!ConfigManager.kuudraStunCloseMenu || client.player == null
            || !(ClientUiAccess.screen(client) instanceof AbstractContainerScreen<?> screen)
            || !"Perk Menu".equals(screen.getTitle().getString())) return;
        closeMenu = screen.getMenu();
        closeAt = client.player.tickCount + ConfigManager.kuudraStunCloseDelay;
    }

    static void tick(Minecraft client) {
        if (!GameplayModule.KUUDRA_STUN_HELPER.enabled()) { if (stunning) reset(); return; }
        if (!stunning || client.player == null) return;
        if (!belly && client.player.blockPosition().equals(BELLY)) belly = true;
        if (closeMenu != null && client.player.tickCount >= closeAt) {
            if (client.player.containerMenu == closeMenu) client.player.closeContainer();
            closeMenu = null;
        }
    }

    static boolean blockUse(Minecraft client, boolean inHighTierRun) {
        if (!GameplayModule.KUUDRA_STUN_HELPER.enabled() || !ConfigManager.kuudraBlockPickobulus || !inHighTierRun
                || (override != null && override.isDown()) || client.player == null || client.level == null) return false;
        var lore = client.player.getMainHandItem().get(DataComponents.LORE);
        if (lore == null || lore.lines().stream().noneMatch(line -> line.getString().contains("Ability: Pickobulus"))) return false;
        int mode = ConfigManager.kuudraBlockWhen;
        if (!belly) return mode != 1 && block();
        if (!stunning || mode == 0) return false;
        var eye = client.player.getEyePosition();
        var hit = client.level.clip(new ClipContext(eye, eye.add(client.player.getLookAngle().scale(32)),
            ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, client.player));
        if (hit.getType() == HitResult.Type.BLOCK) {
            Vec3 pos = Vec3.atCenterOf(hit.getBlockPos());
            for (var pod : PODS) if (pod.area().contains(pos)) return false;
        }
        return block();
    }

    private static boolean block() {
        long now = System.nanoTime();
        if (now - lastAlert >= 500_000_000L) {
            lastAlert = now;
            MessageUtils.sendMessage("Blocked pickaxe ability!");
        }
        return true;
    }

    static void render(LevelRenderContext context) {
        if (!stunning || !GameplayModule.KUUDRA_STUN_HELPER.enabled()) return;
        var player = Minecraft.getInstance().player;
        if (player == null) return;
        var camera = context.levelState().cameraRenderState.pos;
        var pose = new PoseStack();
        if (belly && ConfigManager.kuudraHighlightPods) {
            for (var pod : PODS) draw(context, pose, pod.area().move(-camera.x, -camera.y, -camera.z), ConfigManager.kuudraPodDepth);
        }
        if (ConfigManager.kuudraHighlightExactPod) {
            AABB box = PODS[ConfigManager.kuudraPod].block();
            if (!belly) box = box.move(player.position().subtract(Vec3.atLowerCornerOf(BELLY)));
            draw(context, pose, box.move(-camera.x, -camera.y, -camera.z), false);
        }
    }

    private static void draw(LevelRenderContext context, PoseStack pose, AABB box, boolean depth) {
        int color = 0xFF000000 | ConfigManager.kuudraPodColor;
        context.submitNodeCollector().submitCustomGeometry(pose, depth ? RenderTypes.debugQuads() : PartyRenderPipeline.type(),
            (matrix, vertices) -> PartyGeometry.outline(vertices, matrix.pose(), box.minX, box.minY, box.minZ,
                box.maxX, box.maxY, box.maxZ, .02, color));
    }
}
