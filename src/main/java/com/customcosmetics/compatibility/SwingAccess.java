// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.compatibility;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import net.minecraft.world.InteractionHand;

/** Access to the swing snapshot and interaction animation across Minecraft versions. */
public final class SwingAccess {
    private record Action(Method method, Object animation) {}
    private static final ClassValue<Action> ACTION = new ClassValue<>() {
        @Override protected Action computeValue(Class<?> type) {
            try {
                try { return new Action(type.getMethod("swing", InteractionHand.class), null); }
                catch (NoSuchMethodException modern) {
                    var animation = Class.forName("net.minecraft.world.item.component.SwingAnimation");
                    var swing = type.getMethod("swing", InteractionHand.class, animation, boolean.class);
                    return new Action(swing, animation.getField("DEFAULT").get(null));
                }
            } catch (ReflectiveOperationException error) { throw new IllegalStateException("Unsupported interaction swing", error); }
        }
    };
    private record Access(Method progress, Field hand, Method description, Method descriptionHand, Method descriptionAnimation) {}
    private static final ClassValue<Access> ACCESS = new ClassValue<>() {
        @Override protected Access computeValue(Class<?> type) {
            try {
                try { return new Access(type.getMethod("getAttackAnim", float.class), type.getField("swingingArm"), null, null, null); }
                catch (NoSuchMethodException | NoSuchFieldException modern) {
                    var description = type.getMethod("getCurrentSwing");
                    return new Access(type.getMethod("getSwingAnimation", float.class), null, description,
                        description.getReturnType().getMethod("hand"), description.getReturnType().getMethod("animation"));
                }
            } catch (ReflectiveOperationException missing) { throw new IllegalStateException("Unsupported swing snapshot", missing); }
        }
    };
    private static final ClassValue<Method> ITEM_ANIMATION = new ClassValue<>() {
        @Override protected Method computeValue(Class<?> type) {
            try {
                try { return type.getMethod("getSwingAnimation"); }
                catch (NoSuchMethodException modern) { return type.getMethod("getAttackAnimation"); }
            } catch (ReflectiveOperationException error) { throw new IllegalStateException("Unsupported item swing animation", error); }
        }
    };
    private static final ClassValue<Method> DURATION = new ClassValue<>() {
        @Override protected Method computeValue(Class<?> type) {
            try { return type.getMethod("duration"); }
            catch (ReflectiveOperationException error) { throw new IllegalStateException("Unsupported swing duration", error); }
        }
    };
    private SwingAccess() {}
    public static void swingMainHand(Object player) {
        var action = ACTION.get(player.getClass());
        try {
            if (action.animation() == null) action.method().invoke(player, InteractionHand.MAIN_HAND);
            else action.method().invoke(player, InteractionHand.MAIN_HAND, action.animation(), false);
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Cannot play interaction swing", error);
        }
    }
    public static void validate(Class<?> type) { ACCESS.get(type); }
    public static boolean modern(Object player) { return ACCESS.get(player.getClass()).description != null; }
    public static void validateItem(Class<?> type) { ITEM_ANIMATION.get(type); }
    public static int baseDuration(Object player, Object heldItem) {
        try {
            var access = ACCESS.get(player.getClass());
            Object description = access.description == null ? null : access.description.invoke(player);
            // 26.3 separates attack and interaction animations; preserve the actual observed swing's type.
            Object animation = description == null ? ITEM_ANIMATION.get(heldItem.getClass()).invoke(heldItem)
                : access.descriptionAnimation.invoke(description);
            return Math.max(1, ((Number)DURATION.get(animation.getClass()).invoke(animation)).intValue());
        } catch (ReflectiveOperationException error) { throw new IllegalStateException("Cannot read item swing duration", error); }
    }
    public static float progress(Object player) {
        try { return ((Number) ACCESS.get(player.getClass()).progress.invoke(player, 1f)).floatValue(); }
        catch (ReflectiveOperationException error) { throw new IllegalStateException("Cannot read swing progress", error); }
    }
    public static InteractionHand hand(Object player) {
        var access = ACCESS.get(player.getClass());
        try {
            if (access.hand != null) {
                var hand = (InteractionHand)access.hand.get(player);
                return hand == null ? InteractionHand.MAIN_HAND : hand;
            }
            Object description = access.description.invoke(player);
            return description == null ? InteractionHand.MAIN_HAND : (InteractionHand) access.descriptionHand.invoke(description);
        } catch (ReflectiveOperationException error) { throw new IllegalStateException("Cannot read swing hand", error); }
    }
}
