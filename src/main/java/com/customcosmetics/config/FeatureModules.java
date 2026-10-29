// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.config;

/** Master switches preserve each feature's detailed settings while disabling local and remote effects. */
public final class FeatureModules {
    public enum Feature {
        ANIMATIONS("Animations"),CAPE("Cape"),DAMAGE("Damage Splash"),EQUIPMENT("Equipments"),ITEMS("Item Customization"),
        MAP("Map Editor"),NICK("NickTweaks"),PLAYER_SIZE("Player Size"),SMOL("SmolPeople"),SHARING("Sharing"),
        SKINS("Skins"),SNAPLOOK("Snaplook"),COMMAND_TOOLTIPS("Command Tooltips"),CREATURE_MODEL("Creature Model"),DROP_2D("2D Drop");
        public final String label;
        Feature(String label){this.label=label;}
        public int bit(){return 1<<ordinal();}
    }
    public static final int ALL=(1<<Feature.values().length)-1;
    private FeatureModules(){}
    public static boolean enabled(Feature feature){return (ConfigManager.disabledModules&feature.bit())==0;}
    public static void set(Feature feature,boolean enabled){ConfigManager.disabledModules=enabled?ConfigManager.disabledModules&~feature.bit():ConfigManager.disabledModules|feature.bit();}
    public static void all(boolean enabled){ConfigManager.disabledModules=enabled?0:ALL;}
    public static boolean remoteConsumers(){int remote=ALL&~(Feature.MAP.bit()|Feature.DAMAGE.bit()|Feature.ITEMS.bit()|Feature.SNAPLOOK.bit()|Feature.COMMAND_TOOLTIPS.bit()|Feature.DROP_2D.bit());return (ConfigManager.disabledModules&remote)!=remote;}
}
