// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.gametest;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.InteractionHand;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** Unspawned client-only reference: Minecraft's own swing state machine with Noamm's duration hook. */
public final class NoammSwingReferencePlayer extends RemotePlayer {
    public int referenceDuration = 6;
    private final Object state;
    private final Field previous, current;
    private final Method tick;
    public NoammSwingReferencePlayer(ClientLevel level) {
        super(level, new GameProfile(java.util.UUID.randomUUID(), "NoammReference"));
        try {
            Object nativeState = null;
            Field old = null, value = null;
            Method advance;
            try {
                old = LivingEntity.class.getField("oAttackAnim");
                value = LivingEntity.class.getField("attackAnim");
                advance = LivingEntity.class.getDeclaredMethod("updateSwingTime");
            } catch (NoSuchFieldException modern) {
                Field field = LivingEntity.class.getDeclaredField("swingState");
                field.setAccessible(true);
                nativeState = field.get(this);
                advance = nativeState.getClass().getDeclaredMethod("tick");
            }
            advance.setAccessible(true);
            state = nativeState; previous = old; current = value; tick = advance;
        } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }
    public void step() {
        try {
            if (previous != null) previous.setFloat(this, current.getFloat(this));
            tick.invoke(state == null ? this : state);
        } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }
    public boolean modern() { return state != null; }
    public void start(InteractionHand hand) {
        try {
            try { LivingEntity.class.getMethod("swing", InteractionHand.class, boolean.class).invoke(this, hand, false); }
            catch (NoSuchMethodException modern) {
                var stack = net.minecraft.client.Minecraft.getInstance().player.getItemInHand(hand);
                Object animation = stack.getClass().getMethod("getAttackAnimation").invoke(stack);
                LivingEntity.class.getMethod("swing", InteractionHand.class, animation.getClass(), boolean.class)
                    .invoke(this, hand, animation, false);
            }
        } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }
    public static float progress(LivingEntity entity, float partial) {
        try {
            Method read;
            try { read = LivingEntity.class.getMethod("getAttackAnim", float.class); }
            catch (NoSuchMethodException modern) { read = LivingEntity.class.getMethod("getSwingAnimation", float.class); }
            return ((Number)read.invoke(entity, partial)).floatValue();
        } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }
}
