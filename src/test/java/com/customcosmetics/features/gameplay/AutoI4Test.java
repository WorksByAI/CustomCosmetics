package com.customcosmetics.features.gameplay;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;

class AutoI4Test {
    @Test void oneArrowCrossesTheCenterOfEveryLamp() {
        for (var origin : new Vec3[]{new Vec3(62.5, 128.52, 34.5), new Vec3(64.5, 128.52, 36.5)}) {
            for (int x = 64; x <= 68; x += 2) {
                for (int y = 126; y <= 130; y += 2) {
                    var lamp = new BlockPos(x, y, 50);
                    var center = AutoI4.center(lamp);
                    assertEquals(new Vec3(x + .5, y + .5, 50), center);
                    var direction = AutoI4.centerShot(origin, center);
                    assertNotNull(direction);
                    var impact = flyToLamp(origin, direction.scale(3));
                    assertEquals(center.x, impact.x, .001);
                    assertEquals(center.y, impact.y, .001);
                }
            }
        }
    }

    private static Vec3 flyToLamp(Vec3 position, Vec3 velocity) {
        for (int tick = 0; tick < 24; tick++) {
            var next = position.add(velocity);
            if (next.z >= 50) return position.lerp(next, (50 - position.z) / velocity.z);
            position = next;
            velocity = velocity.scale(.99).add(0, -.05, 0);
        }
        return fail("Arrow never reached the lamps");
    }
}
