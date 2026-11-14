// SPDX-License-Identifier: GPL-3.0-only
// Route coordinates and Red/Green room actions from Quoi. Maze and lava parkour data are credited upstream to SkyHanni-REPO.
package com.customcosmetics.features.gameplay;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

final class MirrorverseRoutes {
    private MirrorverseRoutes() {}

    static final List<Vec3> MAZE = List.of(
        new Vec3(-84.0, 51.0, -112.0), new Vec3(-86.0, 51.0, -112.0), new Vec3(-86.0, 51.0, -115.0),
        new Vec3(-88.0, 51.0, -115.0), new Vec3(-88.0, 51.0, -110.0), new Vec3(-86.0, 51.0, -110.0),
        new Vec3(-86.0, 51.0, -109.0), new Vec3(-84.0, 51.0, -109.0), new Vec3(-84.0, 51.0, -107.0),
        new Vec3(-90.0, 51.0, -107.0), new Vec3(-90.0, 51.0, -108.0), new Vec3(-93.0, 51.0, -108.0),
        new Vec3(-93.0, 51.0, -110.0), new Vec3(-90.0, 51.0, -110.0), new Vec3(-90.0, 51.0, -112.0),
        new Vec3(-92.0, 51.0, -112.0), new Vec3(-92.0, 51.0, -114.0), new Vec3(-90.0, 51.0, -114.0),
        new Vec3(-90.0, 51.0, -116.0), new Vec3(-94.0, 51.0, -116.0), new Vec3(-94.0, 51.0, -114.0),
        new Vec3(-96.0, 51.0, -114.0), new Vec3(-96.0, 51.0, -116.0), new Vec3(-98.0, 51.0, -116.0),
        new Vec3(-98.0, 51.0, -112.0), new Vec3(-96.0, 51.0, -112.0), new Vec3(-96.0, 51.0, -111.0),
        new Vec3(-95.0, 51.0, -111.0), new Vec3(-95.0, 51.0, -108.0), new Vec3(-98.0, 51.0, -108.0),
        new Vec3(-98.0, 51.0, -110.0), new Vec3(-100.0, 51.0, -110.0), new Vec3(-100.0, 51.0, -108.0),
        new Vec3(-102.0, 51.0, -108.0), new Vec3(-102.0, 51.0, -112.0), new Vec3(-100.0, 51.0, -112.0),
        new Vec3(-100.0, 51.0, -116.0), new Vec3(-102.0, 51.0, -116.0), new Vec3(-102.0, 51.0, -114.0),
        new Vec3(-104.0, 51.0, -114.0), new Vec3(-104.0, 51.0, -112.0), new Vec3(-105.0, 51.0, -112.0)
    );

    static final List<Vec3> PARKOUR = List.of(
        new Vec3(-121, 39, -108), new Vec3(-125, 39, -108), new Vec3(-129, 39, -108), new Vec3(-133, 40, -108),
        new Vec3(-137, 39, -104), new Vec3(-133, 40, -100), new Vec3(-137, 41, -96), new Vec3(-141, 42, -100),
        new Vec3(-145, 40, -104), new Vec3(-145, 40, -108), new Vec3(-141, 39, -112), new Vec3(-137, 40, -116),
        new Vec3(-141, 41, -120), new Vec3(-145, 42, -116), new Vec3(-149, 43, -112), new Vec3(-153, 42, -108),
        new Vec3(-157, 41, -104), new Vec3(-153, 40, -100), new Vec3(-157, 40, -96), new Vec3(-161, 39, -92),
        new Vec3(-165, 40, -96), new Vec3(-165, 41, -100), new Vec3(-165, 42, -104), new Vec3(-165, 43, -108),
        new Vec3(-161, 42, -112), new Vec3(-157, 39, -116), new Vec3(-161, 40, -120), new Vec3(-165, 41, -124),
        new Vec3(-169, 41, -120), new Vec3(-169, 42, -116), new Vec3(-173, 43, -112), new Vec3(-173, 43, -108),
        new Vec3(-177, 40, -104), new Vec3(-173, 41, -100), new Vec3(-173, 41, -96), new Vec3(-177, 39, -92),
        new Vec3(-181, 40, -96), new Vec3(-185, 41, -100), new Vec3(-181, 42, -104), new Vec3(-185, 41, -108),
        new Vec3(-181, 40, -112), new Vec3(-185, 41, -116), new Vec3(-181, 40, -120), new Vec3(-185, 41, -124),
        new Vec3(-189, 42, -120), new Vec3(-189, 43, -116), new Vec3(-189, 43, -112), new Vec3(-193, 41, -108),
        new Vec3(-193, 42, -104), new Vec3(-189, 43, -100), new Vec3(-193, 42, -96), new Vec3(-197, 39, -92),
        new Vec3(-201, 40, -96), new Vec3(-205, 41, -100), new Vec3(-209, 42, -104), new Vec3(-205, 43, -108),
        new Vec3(-201, 43, -108), new Vec3(-197, 43, -112), new Vec3(-201, 40, -116), new Vec3(-205, 41, -116),
        new Vec3(-205, 42, -120), new Vec3(-209, 43, -120), new Vec3(-213, 41, -116), new Vec3(-213, 41, -112),
        new Vec3(-217, 42, -108), new Vec3(-221, 43, -104), new Vec3(-223, 43, -104)
    );

    static final List<Vec3> TUBULATOR = List.of(
        new Vec3(-302.0, 1.0, -107.0), new Vec3(-304.0, 3.0, -107.0), new Vec3(-306.0, 5.0, -103.0), new Vec3(-308.0, 7.0, -105.0),
        new Vec3(-304.0, 9.0, -109.0), new Vec3(-300.0, 11.0, -111.0), new Vec3(-304.0, 13.0, -107.0), new Vec3(-308.0, 15.0, -103.0),
        new Vec3(-306.0, 17.0, -107.0), new Vec3(-302.0, 19.0, -111.0), new Vec3(-300.0, 21.0, -107.0), new Vec3(-304.0, 23.0, -111.0),
        new Vec3(-306.0, 25.0, -109.0), new Vec3(-302.0, 27.0, -111.0), new Vec3(-300.0, 29.0, -107.0), new Vec3(-304.0, 31.0, -103.0),
        new Vec3(-306.0, 33.0, -105.0), new Vec3(-302.0, 35.0, -107.0), new Vec3(-300.0, 37.0, -109.0), new Vec3(-302.0, 39.0, -105.0),
        new Vec3(-304.0, 41.0, -107.0), new Vec3(-306.0, 43.0, -111.0), new Vec3(-302.0, 45.0, -107.0), new Vec3(-300.0, 47.0, -103.0),
        new Vec3(-304.0, 49.0, -107.0), new Vec3(-308.0, 51.0, -105.0), new Vec3(-304.0, 53.0, -109.0), new Vec3(-306.0, 55.0, -111.0)
    );

    static final List<Room> RED_GREEN = List.of(
        new Room(
            new BlockPos(-228, 42, -107),
            List.of(
                swap(Side.LEFT),
                move(-228, 42, -109),
                swap(),
                move(-230, 42, -107)
            )
        ),
        new Room(
            new BlockPos(-234, 42, -107),
            List.of(
                swap(Side.LEFT),
                move(-234, 42, -109),

                swap(),
                move(-234, 42, -105),
                swap(),
                move(-236, 42, -109),

                swap(),
                move(-236, 42, -105),
                swap(),
                move(-238, 42, -109),

                swap(),
                move(-238, 42, -105),
                swap(),

                move(-240, 42, -107)
            )
        ),
        new Room(
            new BlockPos(-243, 42, -107),
            List.of(
                swap(Side.LEFT),

                move(-243, 42, -109), // plate
                swap(),

                move(-248, 42, -108), // button
                interact(-249, 44, -109),

                move(-244, 42, -109), // go up
                awaitSound(),
                move(-244, 46, -107),

                swap(),
                interact(-243, 48, -107), // button 2
                swap(),
                move(-244, 45, -109), // go down

                move(-244, 42, -105), // go up
                awaitSound(),
                move(-245, 46, -105),

                move(-246, 46, -106), // top plate
                move(-246, 46, -107),
                swap(),

                move(-246, 45, -105), // go down
                move(-248, 42, -107)
            )
        ),

        new Room(
            new BlockPos(-251, 42, -107),
            List.of(
                swap(Side.RIGHT),

                move(-251, 42, -105), // plate
                swap(),

                move(-258, 42, -105), // button
                interact(-258, 42, -105),

                move(-253, 42, -109), // go up
                awaitSound(),
                pause(),
                move(-253, 46, -108),

                interact(-253, 48, -108), // lever
                move(-253, 45, -109), // go down

                move(-259, 42, -106), // button 2
                interact(-258, 42, -105),

                awaitSound(), // go up
                pause(),
                move(-255, 46, -105.2),

                move(-255, 46, -107), // plate
                swap(),

                interact(-257, 48, -108), // button 3

                move(-257, 46, -106), // go down
                move(-259, 45, -106),

                move(-252, 42, -109), // go up
                awaitSound(),
                pause(),
                move(-252, 46, -108),

                interact(-252, 47, -107), // button 4
                swap(),

                move(-252, 45, -109), // go down

                move(-252.5, 42, -105), // go up
                awaitSound(),
                pause(),
                move(-252, 46, -105),

                move(-253, 45, -105), // go down
                move(-261, 42, -107)
            )
        )
    );

    record Room(BlockPos start, List<Action> actions) {}
    enum Side { LEFT, RIGHT }
    sealed interface Action permits Move, Swap, Interact, Pause, AwaitSound {}
    record Move(Vec3 position) implements Action {}
    record Swap(Side side) implements Action {}
    record Interact(BlockPos position) implements Action {}
    record Pause(int ticks) implements Action {}
    enum AwaitSound implements Action { INSTANCE }

    private static Move move(double x, double y, double z) {
        return new Move(x == Math.floor(x) && z == Math.floor(z)
            ? Vec3.atCenterOf(BlockPos.containing(x, y, z)) : new Vec3(x, y, z));
    }
    private static Swap swap(Side side) { return new Swap(side); }
    private static Swap swap() { return new Swap(null); }
    private static Interact interact(int x, int y, int z) { return new Interact(new BlockPos(x, y, z)); }
    private static Pause pause() { return new Pause(5); }
    private static AwaitSound awaitSound() { return AwaitSound.INSTANCE; }
}
