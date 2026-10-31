// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.features;

import io.github.arcadiumclient.shared.debug.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class CustomF3Test {
    @Test void allBetterF3ModulesHaveEditableLineAndColorDefinitions() {
        var ids=DebugLayout.catalog().stream().map(DebugLayout.Definition::id).toList();
        assertEquals(Set.of("minecraft","fps","graphics","server","coords","chunks","location","entity","sound","help","system","target","empty","miscleft","miscright"),new HashSet<>(ids));
        var layout=DebugLayout.defaults();var system=layout.right.getFirst();
        assertTrue(system.lines.keySet().containsAll(Set.of("cpu","gpu","gpu_driver","display","java_version")));
        var coords=layout.left.stream().filter(m->m.kind.equals("coords")).findFirst().orElseThrow();
        assertTrue(coords.colors.keySet().containsAll(Set.of("colorX","colorY","colorZ")));
    }
    @Test void arbitraryOrderDuplicatesSidesAndLineOptionsRoundTrip() {
        var layout=DebugLayout.defaults();var duplicate=new DebugLayout.Module("coords");duplicate.lines.put("velocity",false);duplicate.colors.put("colorX",0x123456);layout.right.addFirst(duplicate);
        var restored=DebugLayout.decode(layout.encode());assertEquals(duplicate.key,restored.right.getFirst().key);assertFalse(restored.right.getFirst().lines.get("velocity"));assertEquals(0xFF123456,restored.right.getFirst().color("colorX"));
        assertThrows(IllegalArgumentException.class,()->DebugLayout.decode("{\"left\":[{\"kind\":\"unknown\"}],\"right\":[]}"));
    }
    @Test void rawLegacyLinesKeepRealFpsAndHardwareSeparate() {
        var parsed=LegacyDebugParser.parse(List.of("Minecraft 1.8.9 (Arcadium)","421 fps (0 chunk updates)","XYZ: -4.250 / 80.000 / 32.125","Biome: Plains"),List.of("Java: 25 64bit","CPU: Actual CPU","Display: 1920x1080 (Vendor)","Actual GPU","4.6 Driver"),Map.of());
        assertEquals("421 fps (0 chunk updates)",parsed.get("fps"));assertEquals("Actual GPU",parsed.get("gpu"));assertEquals("Actual CPU",parsed.get("cpu"));assertEquals("Plains",parsed.get("biome"));
    }
    @Test void closeAnimationAndDisabledAnimationReachTheirEndpoints() {
        var view=new DebugView();long start=1_000_000_000L;view.animate(true,true,1,start);
        assertTrue(view.animate(true,true,1,start+100_000_000)>0);
        assertEquals(1,view.animate(true,false,1,start+200_000_000));
        assertEquals(0,view.animate(false,false,1,start+300_000_000));
    }
    @Test void miscModulesKeepModDetailsWithoutDuplicatingVanillaStats() {
        assertEquals(List.of("Sodium Renderer","GPU Buffer: 64 MiB"),DebugLineFilter.extras(List.of("Minecraft 1.21.11","421 fps T: 260","C: 20/1344 D: 3","XYZ: 4 60 1","Sodium Renderer","GPU Buffer: 64 MiB")));
        assertEquals(List.of("Sodium Renderer"),DebugLineFilter.extras(List.of("Display: 1920x1080 (Vendor)","Actual GPU Name","Actual Driver","Sodium Renderer")));
    }
    @Test void separatorVisibilityAndUnsupportedClockPatternsAreHandled() {
        var layout=DebugLayout.defaults();layout.left.clear();layout.right.clear();var separator=new DebugLayout.Module("empty");layout.left.add(separator);
        assertEquals(1,DebugView.columns(layout,Map.of(),false).left().size());separator.lines.put("nothing",false);assertTrue(DebugView.columns(layout,Map.of(),false).left().isEmpty());
        var system=new DebugLayout.Module("system");system.timeFormat="z";layout.right.add(system);assertThrows(java.time.DateTimeException.class,()->DebugLayout.decode(layout.encode()));
    }
}
