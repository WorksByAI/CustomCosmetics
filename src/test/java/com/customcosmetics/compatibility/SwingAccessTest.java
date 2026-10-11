package com.customcosmetics.compatibility;

import net.minecraft.world.InteractionHand;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SwingAccessTest {
    public static class OldPlayer {
        public InteractionHand swingingArm = InteractionHand.OFF_HAND;
        public float getAttackAnim(float partial) { return .4f * partial; }
    }
    public record Animation(int duration) {}
    public static class OldItem { public Animation getSwingAnimation() { return new Animation(12); } }
    public static class ModernItem { public Animation getAttackAnimation() { return new Animation(15); } }
    public record Description(InteractionHand hand, Animation animation) {}
    public static class ModernPlayer {
        public Description current = new Description(InteractionHand.OFF_HAND, new Animation(18));
        public Description getCurrentSwing() { return current; }
        public float getSwingAnimation(float partial) { return .7f * partial; }
    }
    @Test void bothSnapshotVersionsAreReadWithoutWritingOrStartingSwings() {
        var old = new OldPlayer(); var modern = new ModernPlayer();
        SwingAccess.validate(OldPlayer.class); SwingAccess.validate(ModernPlayer.class);
        assertEquals(.4f, SwingAccess.progress(old)); assertEquals(.7f, SwingAccess.progress(modern));
        assertEquals(InteractionHand.OFF_HAND, SwingAccess.hand(old)); assertEquals(InteractionHand.OFF_HAND, SwingAccess.hand(modern));
        assertEquals(InteractionHand.OFF_HAND, old.swingingArm); assertEquals(InteractionHand.OFF_HAND, modern.current.hand());
        old.swingingArm = null; assertEquals(InteractionHand.MAIN_HAND, SwingAccess.hand(old));
        modern.current = null; assertEquals(InteractionHand.MAIN_HAND, SwingAccess.hand(modern));
    }
    @Test void baseDurationUsesItemTimingAndModernObservedInteractionAnimation() {
        var old = new OldPlayer(); var modern = new ModernPlayer();
        SwingAccess.validateItem(OldItem.class); SwingAccess.validateItem(ModernItem.class);
        assertEquals(12, SwingAccess.baseDuration(old, new OldItem()));
        assertEquals(18, SwingAccess.baseDuration(modern, new ModernItem()));
        modern.current = null; assertEquals(15, SwingAccess.baseDuration(modern, new ModernItem()));
    }
}
