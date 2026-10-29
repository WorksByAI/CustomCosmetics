// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.features.items;

import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import static org.junit.jupiter.api.Assertions.*;

/** Optional integration check against freshly fetched, unmodified upstream inputs. */
@EnabledIfSystemProperty(named="customcosmetics.liveHeadInputs", matches=".+")
class HeadSkinLiveDataTest {
    @Test void liveRepositoryContainsCelestialGoldorAndItsFullAnimation() throws Exception {
        Path inputs=Path.of(System.getProperty("customcosmetics.liveHeadInputs"));
        HeadSkinCatalog.Data repository;
        try(var input=Files.newInputStream(inputs.resolve("neu.zip"))){repository=HeadSkinCatalog.parseRepository(input);}
        var api=HeadSkinCatalog.parse(Files.readString(inputs.resolve("hypixel-items.json")));
        var combined=HeadSkinCatalog.combine(repository,api);
        assertTrue(repository.entries().size()>4000);assertTrue(combined.entries().size()>=repository.entries().size()+combined.animations().size());
        assertTrue(combined.entries().stream().anyMatch(e->e.id().equals("GOLDOR_CELESTIAL")&&e.name().equals("Celestial Goldor's Helmet Skin")));
        var animated=combined.entries().stream().filter(e->e.id().equals("animated:GOLDOR_CELESTIAL")).findFirst().orElseThrow();
        assertTrue(animated.texture().startsWith("animated:GOLDOR_CELESTIAL:"));
        assertEquals(24,combined.animations().get("GOLDOR_CELESTIAL").frames().size());
        assertEquals(3,combined.animations().get("GOLDOR_CELESTIAL").ticks());
        assertEquals(repository,HeadSkinCatalog.cached(HeadSkinCatalog.cache(repository)));
        String report="{\"neu_static_heads\":"+repository.entries().size()+",\"hypixel_static_heads\":"+api.size()+",\"animated_skins\":"+combined.animations().size()+",\"combined_choices\":"+combined.entries().size()+"}";
        Files.writeString(inputs.resolve("live-counts.json"),report+"\n");System.out.println("LIVE_HEAD_CATALOG "+report);
    }
}
