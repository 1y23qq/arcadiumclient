// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.features;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.polyfrost.oneconfig.api.config.v1.Node;
import org.polyfrost.oneconfig.api.config.v1.Properties;
import org.polyfrost.oneconfig.api.config.v1.Tree;
import static org.junit.jupiter.api.Assertions.*;

class OptimizationDefaults074Test {
    private static Map<String, Node> values(int version) {
        Map<String, Node> nodes = new LinkedHashMap<>();
        nodes.put("rendererDefaultsVersion", Properties.simple(version));
        for (String name : OptimizationDefaults074.RESET) nodes.put(name, Properties.simple(true));
        nodes.put("cachedNameMasking", Properties.simple(false));
        nodes.put("worldRenderScale", Properties.simple(true));
        nodes.put("worldResolution", Properties.simple(95));
        nodes.put("gpuVoxelSkinLayers", Properties.simple(true));
        return nodes;
    }
    private static Tree tree(Map<String, Node> values) { return new Tree("test", null, null, values); }

    @Test void existingRolloutValuesResetWithoutChangingUnrelatedOrUnknownSettings() {
        Tree tree = tree(values(0));
        assertTrue(OptimizationDefaults074.apply(tree));
        for (String name : OptimizationDefaults074.RESET) assertEquals(false, tree.getProp(name).get(), name);
        assertEquals(1, tree.getProp("rendererDefaultsVersion").get());
        assertEquals(false, tree.getProp("cachedNameMasking").get());
        assertEquals(true, tree.getProp("worldRenderScale").get());
        assertEquals(95, tree.getProp("worldResolution").get());
        assertEquals(true, tree.getProp("gpuVoxelSkinLayers").get());
    }

    @Test void completedRevisionPreservesLaterUserChoicesAcrossFreshTrees() {
        Tree first = tree(values(0));
        assertTrue(OptimizationDefaults074.apply(first));
        Map<String, Node> saved = values((Integer) first.getProp("rendererDefaultsVersion").get());
        Tree nextLaunch = tree(saved);
        assertFalse(OptimizationDefaults074.apply(nextLaunch));
        assertEquals(true, nextLaunch.getProp("gpuPlayerPipeline").get());
        assertEquals(true, nextLaunch.getProp("gpuParticles").get());
    }

    @Test void missingOptionCannotLeaveAPartiallyMigratedTree() {
        Map<String, Node> nodes = values(0);
        nodes.remove("directFramebufferPresentation");
        Tree tree = tree(nodes);
        assertThrows(IllegalStateException.class, () -> OptimizationDefaults074.apply(tree));
        assertEquals(0, tree.getProp("rendererDefaultsVersion").get());
        assertEquals(true, tree.getProp("gpuNameTags").get());
        assertEquals(true, tree.getProp("gpuPlayerPipeline").get());
    }

    @Test void futureRevisionAndInvalidMarkerCannotOverwriteSettings() {
        Tree future = tree(values(2));
        assertFalse(OptimizationDefaults074.apply(future));
        assertEquals(true, future.getProp("gpuParticles").get());
        Map<String, Node> invalid = values(0);
        invalid.put("rendererDefaultsVersion", Properties.simple((Object) "invalid"));
        Tree tree = tree(invalid);
        assertThrows(IllegalStateException.class, () -> OptimizationDefaults074.apply(tree));
        assertEquals(true, tree.getProp("gpuNameTags").get());
    }
}
