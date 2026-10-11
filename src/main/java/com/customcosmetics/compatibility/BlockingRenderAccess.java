// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.compatibility;

import java.lang.reflect.Field;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.InteractionHand;

/** Read-only access to the first-person snapshot types added in 26.3. */
public final class BlockingRenderAccess {
    private static final ClassValue<Field> AVATAR = new ClassValue<>() {
        protected Field computeValue(Class<?> type) { return field(type, "avatarRenderState"); }
    };
    private record Hands(Field main, Field off) {}
    private static final ClassValue<Hands> HANDS = new ClassValue<>() {
        protected Hands computeValue(Class<?> type) {
            return new Hands(field(type, "mainHandRenderState"), field(type, "offHandRenderState"));
        }
    };
    private BlockingRenderAccess() {}
    private static Field field(Class<?> type, String name) {
        try { return type.getField(name); }
        catch (ReflectiveOperationException e) { throw new IllegalStateException("Missing blocking render snapshot", e); }
    }
    public static AvatarRenderState avatar(Object state) {
        try { return (AvatarRenderState) AVATAR.get(state.getClass()).get(state); }
        catch (IllegalAccessException e) { throw new IllegalStateException(e); }
    }
    public static ItemStackRenderState item(Object state, InteractionHand hand) {
        var fields = HANDS.get(state.getClass());
        try { return (ItemStackRenderState) (hand == InteractionHand.MAIN_HAND ? fields.main : fields.off).get(state); }
        catch (IllegalAccessException e) { throw new IllegalStateException(e); }
    }
}
