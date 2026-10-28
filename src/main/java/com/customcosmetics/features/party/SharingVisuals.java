package com.customcosmetics.features.party;

import com.customcosmetics.config.ConfigManager;

/** Select one authorized source per player; rendering preferences never grant a sharing permission. */
public final class SharingVisuals {
    public enum Source { NONE, PARTY, LOCATION }
    private SharingVisuals(){}
    public static Source source(boolean partyEligible,boolean locationEligible){return partyEligible?Source.PARTY:locationEligible?Source.LOCATION:Source.NONE;}
    public static boolean esp(boolean party){return party?ConfigManager.partyEspEnabled:ConfigManager.locationEspEnabled;}
    public static boolean chams(boolean party){return party?ConfigManager.sharingChamsEnabled:ConfigManager.locationChamsEnabled;}
    public static String style(boolean party){return party?ConfigManager.partyEspStyle:ConfigManager.locationEspStyle;}
    public static int color(boolean party){return party?ConfigManager.partyEspColor:ConfigManager.locationEspColor;}
    public static double opacity(boolean party){return party?ConfigManager.partyEspOpacity:ConfigManager.locationEspOpacity;}
    public static double lineWidth(boolean party){return party?ConfigManager.partyEspLineWidth:ConfigManager.locationEspLineWidth;}
    public static boolean farMarkers(boolean party){return party?ConfigManager.partyFarMarkers:ConfigManager.locationFarMarkers;}
    public static boolean names(boolean party){return party?ConfigManager.partyShowNames:ConfigManager.locationShowNames;}
    public static String nametagMode(boolean party){return party?ConfigManager.partyNametagMode:ConfigManager.locationNametagMode;}
    public static String nametagStyle(boolean party){return party?ConfigManager.partyNametagStyle:ConfigManager.locationNametagStyle;}
    public static double nametagSize(boolean party){return party?ConfigManager.partyNametagSize:ConfigManager.locationNametagSize;}
    public static double hideNamesWithin(boolean party){return party?ConfigManager.partyNametagHideDistance:ConfigManager.locationNametagHideDistance;}
    public static double maxDistance(boolean party){return party?ConfigManager.partyMaxDisplayDistance:ConfigManager.locationMaxDisplayDistance;}
}
