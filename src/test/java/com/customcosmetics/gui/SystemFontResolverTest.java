// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.gui;
import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
class SystemFontResolverTest {
    @Test void findsWindowsFontWithoutEnumeratingDirectories(){
        Path expected=Path.of("windows","Fonts","msyhbd.ttc");var checks=new AtomicInteger();
        assertEquals(expected,SystemFontResolver.find("Windows 11","windows","local","home",p->{checks.incrementAndGet();return p.equals(expected);}).orElseThrow());
        assertEquals(1,checks.get());
    }
    @Test void missingLinuxFontFallsBackAfterBoundedChecks(){
        var checks=new AtomicInteger();assertTrue(SystemFontResolver.find("Linux",null,null,"home",p->{checks.incrementAndGet();return false;}).isEmpty());
        assertEquals(8,checks.get());
    }
    @Test void supportsUserInstalledFontAndMissingEnvironment(){
        Path expected=Path.of("local","Microsoft","Windows","Fonts","msyh.ttf");
        assertEquals(expected,SystemFontResolver.find("Windows 11",null,"local",null,expected::equals).orElseThrow());
        assertTrue(SystemFontResolver.find(null,null,null,null,p->{throw new AssertionError("No roots to search");}).isEmpty());
    }
}
