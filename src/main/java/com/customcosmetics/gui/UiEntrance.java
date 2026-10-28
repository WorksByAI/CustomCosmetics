// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.gui;
/** Pure geometry shared by drawing and pointer dispatch. Idle is exactly identity. */
public final class UiEntrance {
    private UiEntrance(){}
    public static float scale(float progress){return 1-.055f*(1-progress);}
    public static double pointer(double position,double center,float scale){return scale==1?position:center+(position-center)/scale;}
}
