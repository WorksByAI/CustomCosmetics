// SPDX-License-Identifier: GPL-3.0-only
package com.customcosmetics.features.gameplay;

import com.google.common.collect.ImmutableMultimap;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ChocolateFactoryTest {
    // Raw texture properties from Quoi's ChocolateFactory.kt (1640208), independent of the hash table.
    private static final String BREAKFAST =
        "ewogICJ0aW1lc3RhbXAiIDogMTcxMTQ2MjY3MzE0OSwKICAicHJvZmlsZUlkIiA6ICJiN2I4ZTlhZjEwZGE0NjFmOTY2YTQxM2Rm"
        + "OWJiM2U4OCIsCiAgInByb2ZpbGVOYW1lIiA6ICJBbmFiYW5hbmFZZzciLAogICJzaWduYXR1cmVSZXF1aXJlZCIgOiB0cnVlLAog"
        + "ICJ0ZXh0dXJlcyIgOiB7CiAgICAiU0tJTiIgOiB7CiAgICAgICJ1cmwiIDogImh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0"
        + "L3RleHR1cmUvYTQ5MzMzZDg1YjhhMzE1ZDAzMzZlYjJkZjM3ZDhhNzE0Y2EyNGM1MWI4YzYwNzRmMWI1YjkyN2RlYjUxNmMyNCIK"
        + "ICAgIH0KICB9Cn0";
    private static final String LUNCH =
        "ewogICJ0aW1lc3RhbXAiIDogMTcxMTQ2MjU2ODExMiwKICAicHJvZmlsZUlkIiA6ICI3NzUwYzFhNTM5M2Q0ZWQ0Yjc2NmQ4ZGUw"
        + "OWY4MjU0NiIsCiAgInByb2ZpbGVOYW1lIiA6ICJSZWVkcmVsIiwKICAic2lnbmF0dXJlUmVxdWlyZWQiIDogdHJ1ZSwKICAidGV4"
        + "dHVyZXMiIDogewogICAgIlNLSU4iIDogewogICAgICAidXJsIiA6ICJodHRwOi8vdGV4dHVyZXMubWluZWNyYWZ0Lm5ldC90ZXh0"
        + "dXJlLzdhZTZkMmQzMWQ4MTY3YmNhZjk1MjkzYjY4YTRhY2Q4NzJkNjZlNzUxZGI1YTM0ZjJjYmM2NzY2YTAzNTZkMGEiCiAgICB9"
        + "CiAgfQp9";
    private static final String DINNER =
        "ewogICJ0aW1lc3RhbXAiIDogMTcxMTQ2MjY0OTcwMSwKICAicHJvZmlsZUlkIiA6ICI3NGEwMzQxNWY1OTI0ZTA4YjMyMGM2MmU1"
        + "NGE3ZjJhYiIsCiAgInByb2ZpbGVOYW1lIiA6ICJNZXp6aXIiLAogICJzaWduYXR1cmVSZXF1aXJlZCIgOiB0cnVlLAogICJ0ZXh0"
        + "dXJlcyIgOiB7CiAgICAiU0tJTiIgOiB7CiAgICAgICJ1cmwiIDogImh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1"
        + "cmUvZTVlMzYxNjU4MTlmZDI4NTBmOTg1NTJlZGNkNzYzZmY5ODYzMTMxMTkyODNjMTI2YWNlMGM0Y2M0OTVlNzZhOCIKICAgIH0K"
        + "ICB9Cn0";

    @Test void recognizesAllThreeEggSkulls() {
        assertEquals(0, ChocolateFactory.eggTexture(profile(BREAKFAST)));
        assertEquals(1, ChocolateFactory.eggTexture(profile(LUNCH)));
        assertEquals(2, ChocolateFactory.eggTexture(profile(DINNER)));
    }

    @Test void ignoresMissingAndInvalidTexturesWithoutHidingValidEggs() {
        assertEquals(-1, ChocolateFactory.eggTexture(profile()));
        assertEquals(-1, ChocolateFactory.eggTexture(profile("invalid")));
        assertEquals(1, ChocolateFactory.eggTexture(profile("invalid", LUNCH)));
    }

    private static GameProfile profile(String... textures) {
        var properties = ImmutableMultimap.<String, Property>builder();
        for (String texture : textures) properties.put("textures", new Property("textures", texture));
        return new GameProfile(new UUID(0, 0), "Egg", new PropertyMap(properties.build()));
    }
}
