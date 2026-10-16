// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.compatibility;
import net.minecraft.world.scores.*;
import java.lang.reflect.Method;
import java.util.Optional;

/** Team color changed from ChatFormatting to Optional<TeamColor> in 26.2. */
public final class SidebarAccess {
    private static final Method COLOR;
    static {try{COLOR=PlayerTeam.class.getMethod("getColor");}catch(NoSuchMethodException error){throw new ExceptionInInitializerError(error);}}
    private SidebarAccess(){}
    public static DisplaySlot teamSlot(PlayerTeam team){
        if(team==null)return null;
        Object color;try{color=COLOR.invoke(team);}catch(ReflectiveOperationException error){throw new IllegalStateException("Could not read sidebar team color",error);}
        if(color instanceof Optional<?> optional)color=optional.orElse(null);
        if(!(color instanceof Enum<?> value))return null;
        try{return DisplaySlot.valueOf("TEAM_"+value.name());}catch(IllegalArgumentException ignored){return null;}
    }
}
