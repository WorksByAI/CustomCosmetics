// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.features.party;

import net.minecraft.world.phys.Vec3;
import java.util.List;

/** Camera-facing ribbon with perspective-correct width, using the existing through-wall pipeline. */
public final class PartyTracer {
    private PartyTracer(){}
    public static List<Vec3> ribbon(Vec3 forward,Vec3 right,Vec3 target,double range){
        double length=target.length();
        if(!Double.isFinite(length)||length<.01)return List.of();
        Vec3 start=forward.scale(.5),end=target.scale(Math.min(1,range/length));
        Vec3 side=end.subtract(start).cross(end.add(start));
        if(side.lengthSqr()<1e-12)side=right;
        side=side.normalize();
        Vec3 near=side.scale(.00065),far=side.scale(.0013*end.length());
        return List.of(start.add(near),start.subtract(near),end.subtract(far),end.add(far));
    }
}
