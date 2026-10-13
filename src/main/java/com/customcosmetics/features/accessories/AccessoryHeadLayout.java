// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.features.accessories;
import java.util.function.Function;
/** Keep floating headwear above the actual equipped model, including custom part sizes. */
public final class AccessoryHeadLayout {
    private AccessoryHeadLayout(){}
    public static double baseHeight(AccessoriesSettings settings,boolean helmet,Function<AccessoryCatalog.Style,AccessoryGeometry.Bounds> geometry){
        double top=helmet?.575:.525;var head=settings.part("head");if(head.enabled()){var b=geometry.apply(AccessoryCatalog.style("head",head.style()));top=Math.max(top,.25-(b.minY()+4)*AccessoryPlacement.size("head",head)*(helmet?1.14:1)/16);}
        else if(settings.part("hair").enabled()){var hair=settings.part("hair");top=Math.max(top,.25-(geometry.apply(AccessoryCatalog.style("hair",hair.style())).minY()+4)*AccessoryPlacement.size("hair",hair)*(helmet?1.2:1.04)/16);}return top;
    }
    public static double hatHeight(AccessoriesSettings settings,boolean helmet,Function<AccessoryCatalog.Style,AccessoryGeometry.Bounds> geometry){var hat=settings.part("hat");return baseHeight(settings,helmet,geometry)+Math.max(0,geometry.apply(AccessoryCatalog.style("hat",hat.style())).maxY())*hat.scale()/16+AccessoryPlacement.GAP;}
    private static boolean sideEars(AccessoryCatalog.Style style){return style.kind().equals("ears")&&(style.variant()==4||style.variant()==5);}
    public static double earHeight(AccessoriesSettings settings,boolean helmet,Function<AccessoryCatalog.Style,AccessoryGeometry.Bounds> geometry){var ear=settings.ears();var style=AccessoryCatalog.style("ears",ear.style());if(sideEars(style))return .25;var b=AccessoryPlacement.rotated(geometry.apply(style),0,0,.13f);return baseHeight(settings,helmet,geometry)+Math.max(0,b.maxY())*ear.scale()/16+AccessoryPlacement.GAP;}
    public static double earX(AccessoriesSettings settings,Function<AccessoryCatalog.Style,AccessoryGeometry.Bounds> geometry){
        return earX(settings,false,geometry);
    }
    public static double earX(AccessoriesSettings settings,boolean helmet,Function<AccessoryCatalog.Style,AccessoryGeometry.Bounds> geometry){
        var style=AccessoryCatalog.style("ears",settings.ears().style());if(sideEars(style)){
            double half=helmet?.32:.266;String slot=settings.part("head").enabled()?"head":settings.part("hair").enabled()?"hair":null;
            if(slot!=null){var part=settings.part(slot);var b=geometry.apply(AccessoryCatalog.style(slot,part.style()));double pad=slot.equals("head")?(helmet?1.14:1):(helmet?1.2:1.04);half=Math.max(half,Math.max(Math.abs(b.minX()),Math.abs(b.maxX()))*AccessoryPlacement.size(slot,part)*pad/16);}
            var ear=AccessoryPlacement.rotated(geometry.apply(style),0,0,.13f);return half+AccessoryPlacement.GAP-ear.minX()*settings.ears().scale()/16;
        }
        double x=.205;var hat=settings.part("hat");if(hat.enabled()){var b=geometry.apply(AccessoryCatalog.style("hat",hat.style()));var ear=AccessoryPlacement.rotated(geometry.apply(AccessoryCatalog.style("ears",settings.ears().style())),0,0,.13f);x=Math.max(x,Math.max(Math.abs(b.minX()),Math.abs(b.maxX()))*hat.scale()/16-ear.minX()*settings.ears().scale()/16+AccessoryPlacement.GAP);}return x;
    }
    public static double haloHeight(AccessoriesSettings settings,Function<AccessoryCatalog.Style,AccessoryGeometry.Bounds> geometry){
        return haloHeight(settings,false,geometry);
    }
    public static double haloHeight(AccessoriesSettings settings,boolean helmet,Function<AccessoryCatalog.Style,AccessoryGeometry.Bounds> geometry){
        double highest=baseHeight(settings,helmet,geometry);var hat=settings.part("hat");if(hat.enabled())highest=Math.max(highest,hatHeight(settings,helmet,geometry)-geometry.apply(AccessoryCatalog.style("hat",hat.style())).minY()*hat.scale()/16);
        if(settings.ears().enabled()){var b=AccessoryPlacement.rotated(geometry.apply(AccessoryCatalog.style("ears",settings.ears().style())),0,0,.13f);highest=Math.max(highest,earHeight(settings,helmet,geometry)-b.minY()*settings.ears().scale()/16);}
        var halo=settings.halo();double bottom=Math.max(0,geometry.apply(AccessoryCatalog.style("halo",halo.style())).maxY())*halo.scale()/16;
        return Math.max(.74,highest+.14+bottom);
    }
}
