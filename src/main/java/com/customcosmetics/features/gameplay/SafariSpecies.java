// SPDX-License-Identifier: BSD-3-Clause
// Species, matching rules and defaults adapted from Nebulune SafariESP, Copyright (c) 2025 Starred.
package com.customcosmetics.features.gameplay;

import java.util.Locale;

public enum SafariSpecies {
    FOXTROT(Biome.FOREST, "Foxtrot", 0xF06E14, "fox"),
    BLUEBIRD(Biome.FOREST, "Bluebird", 0x1432C8, "parrot:BLUE"),
    HONEYBUG(Biome.FOREST, "Honeybug", 0xF0BE1E, "bee"),
    TREEFROG(Biome.FOREST, "Treefrog", 0x6E825A, "frog"),
    WOODCHUCKER(Biome.FOREST, "Woodchucker", 0x504646, "creaking"),
    FLUFFLING(Biome.FOREST, "Fluffling", 0xE6E6E6, "panda"),
    HIDEONFLOOR(Biome.FOREST, "Hideonfloor", 0x648228, "shulker:GREEN"),
    PARAKEET(Biome.FOREST, "Parakeet", 0x5AC832, "parrot:GREEN"),
    MACAW(Biome.FOREST, "Macaw", 0xD21E1E, "parrot:RED_BLUE"),
    AREITA(Biome.HAUNTED, "Areita", 0x145A5A, "cave_spider"),
    BLOODBAT(Biome.HAUNTED, "Bloodbat", 0x5A281E, "bat"),
    DUPLICO(Biome.HAUNTED, "Duplico", 0x787878, "interaction"),
    GAZER(Biome.HAUNTED, "Gazer", 0x1E3246, "head:GAZER"),
    LITTERBUG(Biome.HAUNTED, "Litterbug", 0x8232AA, "endermite"),
    SOLSNATCHER(Biome.HAUNTED, "Solsnatcher", 0x323C82, "phantom"),
    GIMMIEGOLD(Biome.HAUNTED, "Gimmiegold", 0xFFD200, "display:GIMMIEGOLD"),
    HIDEONWALL(Biome.HAUNTED, "Hideonwall", 0x8C468C, "shulker:PURPLE"),
    HIDEYHO(Biome.HAUNTED, "Hideyho", 0xBEBEBE, "player:HIDEYHO"),
    DOOMSPIRAL(Biome.HAUNTED, "Doomspiral", 0x148C96, "warden"),
    CAVERNFISH(Biome.CAVERN, "Cavernfish", 0xB4641E, "fish:CLAYFISH:GRAY:BROWN"),
    FLITTER(Biome.CAVERN, "Flitter", 0x285A6E, "display:FLITTER"),
    SHYWORM(Biome.CAVERN, "Shyworm", 0x50A032, "head:SHYWORM"),
    DRIFTLING(Biome.CAVERN, "Driftling", 0x966E3C, "head:DRIFTLING"),
    CHUCKWALLA(Biome.CAVERN, "Chuckwalla", 0x3C3228, "display:CHUCKWALLA"),
    ROCKMITE(Biome.CAVERN, "Rockmite", 0xA0A0A0, "silverfish", "display:ROCKMITE_DISPLAY"),
    SCRAPPY(Biome.CAVERN, "Scrappy", 0xBE7882, "armadillo"),
    SNOOZLE(Biome.CAVERN, "Snoozle", 0xA02828, "sniffer"),
    GEMZIE(Biome.CAVERN, "Gemzie", 0x96B4F0, "vex"),
    STRONGARM(Biome.ICY, "Strongarm", 0xDC7814, "snow_golem"),
    TEPID(Biome.ICY, "Tepid", 0xC8D2C8, "fish:SNOOPER:WHITE:WHITE"),
    POLARIS(Biome.ICY, "Polaris", 0xFFFFFF, "polar_bear"),
    SHUDDERSQUID(Biome.ICY, "Shuddersquid", 0x32BEB4, "glow_squid"),
    BILLYGOAT(Biome.ICY, "Billygoat", 0xE6DCC8, "goat"),
    MANTIS_SHRIMP(Biome.ICY, "Mantis Shrimp", 0x285A64, "display:MANTIS_SHRIMP"),
    NOZZLENOSE(Biome.ICY, "Nozzlenose", 0x8C96AA, "dolphin"),
    TROODON(Biome.ICY, "Troodon", 0x7850BE, "display:TROODON"),
    WUMPA(Biome.ICY, "Wumpa", 0x645A5A, "ravager");

    public enum Biome {
        FOREST("Forest"), HAUNTED("Haunted"), CAVERN("Cavern"), ICY("Icy");
        public final String label;
        private final String key = name().toLowerCase(Locale.ROOT);
        Biome(String label) { this.label = label; }
        public String key() { return key; }

        public static Biome at(double x, double z) {
            if (x < -200 || x > 100 || z < -150 || z > 150) return null;
            if (x >= -50) return z >= 0 ? FOREST : HAUNTED;
            return z >= 0 ? CAVERN : ICY;
        }
    }

    public final Biome biome;
    public final String label;
    public final int color;
    final String[] identities;
    private final String key = name().toLowerCase(Locale.ROOT);

    SafariSpecies(Biome biome, String label, int color, String... identities) {
        this.biome = biome;
        this.label = label;
        this.color = color;
        this.identities = identities;
    }

    public String key() { return key; }
}
