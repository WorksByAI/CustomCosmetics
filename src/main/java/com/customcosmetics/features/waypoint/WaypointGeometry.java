// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.features.waypoint;

import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Matrix4fc;

/** Fixed vertex counts and no temporary coordinate arrays on the render thread. */
final class WaypointGeometry {
    private WaypointGeometry() {}

    static void box(VertexConsumer out, Matrix4fc pose, float x0, float y0, float z0,
                    float x1, float y1, float z1, int color) {
        quad(out, pose, x0,y0,z0, x1,y0,z0, x1,y1,z0, x0,y1,z0, color);
        quad(out, pose, x1,y0,z1, x0,y0,z1, x0,y1,z1, x1,y1,z1, color);
        quad(out, pose, x0,y0,z1, x0,y0,z0, x0,y1,z0, x0,y1,z1, color);
        quad(out, pose, x1,y0,z0, x1,y0,z1, x1,y1,z1, x1,y1,z0, color);
        quad(out, pose, x0,y1,z0, x1,y1,z0, x1,y1,z1, x0,y1,z1, color);
        quad(out, pose, x0,y0,z1, x1,y0,z1, x1,y0,z0, x0,y0,z0, color);
    }

    static void outline(VertexConsumer out, Matrix4fc pose, float x0, float y0, float z0,
                        float x1, float y1, float z1, float width, int color) {
        for (int a = 0; a < 2; a++) for (int b = 0; b < 2; b++) {
            float x = a == 0 ? x0 : x1, y = b == 0 ? y0 : y1;
            quad(out, pose, x-width,y,z0, x+width,y,z0, x+width,y,z1, x-width,y,z1, color);
            quad(out, pose, x,y-width,z0, x,y+width,z0, x,y+width,z1, x,y-width,z1, color);
        }
        for (int a = 0; a < 2; a++) for (int b = 0; b < 2; b++) {
            float x = a == 0 ? x0 : x1, z = b == 0 ? z0 : z1;
            quad(out, pose, x-width,y0,z, x+width,y0,z, x+width,y1,z, x-width,y1,z, color);
            quad(out, pose, x,y0,z-width, x,y0,z+width, x,y1,z+width, x,y1,z-width, color);
        }
        for (int a = 0; a < 2; a++) for (int b = 0; b < 2; b++) {
            float y = a == 0 ? y0 : y1, z = b == 0 ? z0 : z1;
            quad(out, pose, x0,y-width,z, x1,y-width,z, x1,y+width,z, x0,y+width,z, color);
            quad(out, pose, x0,y,z-width, x1,y,z-width, x1,y,z+width, x0,y,z+width, color);
        }
    }

    static void beam(VertexConsumer out, Matrix4fc pose, float x, float y, float z, float radius, int color) {
        float top = y + 48;
        quad(out, pose, x-radius,y,z-radius, x+radius,y,z-radius, x+radius,top,z-radius, x-radius,top,z-radius, color);
        quad(out, pose, x+radius,y,z+radius, x-radius,y,z+radius, x-radius,top,z+radius, x+radius,top,z+radius, color);
        quad(out, pose, x-radius,y,z+radius, x-radius,y,z-radius, x-radius,top,z-radius, x-radius,top,z+radius, color);
        quad(out, pose, x+radius,y,z-radius, x+radius,y,z+radius, x+radius,top,z+radius, x+radius,top,z-radius, color);
    }

    private static void quad(VertexConsumer out, Matrix4fc pose,
                             float x0,float y0,float z0, float x1,float y1,float z1,
                             float x2,float y2,float z2, float x3,float y3,float z3, int color) {
        out.addVertex(pose,x0,y0,z0).setColor(color);
        out.addVertex(pose,x1,y1,z1).setColor(color);
        out.addVertex(pose,x2,y2,z2).setColor(color);
        out.addVertex(pose,x3,y3,z3).setColor(color);
    }
}
