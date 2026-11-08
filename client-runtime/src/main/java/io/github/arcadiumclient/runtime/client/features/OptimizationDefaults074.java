// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.features;

import java.util.ArrayList;
import java.util.List;
import org.polyfrost.oneconfig.api.config.v1.Property;
import org.polyfrost.oneconfig.api.config.v1.Tree;

public final class OptimizationDefaults074 {
    static final List<String> RESET = List.of("gpuNameTags", "retainedNameTagScenes", "compactNameTagPreparation",
            "fastInvisibleHolograms", "reduceRenderHookAllocations", "efficientMotionBlur", "retainedScoreboardPreparation",
            "gpuParticles", "retainedParticleRecipes", "retainedHudBatches", "gpuModelTransforms", "gpuPlayerPipeline",
            "gpuEquipmentPipeline", "directFramebufferPresentation");
    private OptimizationDefaults074() { }

    @SuppressWarnings("unchecked")
    public static boolean apply(Tree tree) {
        Property<?> marker = tree.getProp("rendererDefaultsVersion");
        if (marker == null || !(marker.get() instanceof Integer version)) throw new IllegalStateException("Missing renderer defaults revision");
        if (version >= 1) return false;
        var targets = new ArrayList<Property<Boolean>>(RESET.size());
        for (String name : RESET) {
            Property<?> property = tree.getProp(name);
            if (property == null || !(property.get() instanceof Boolean)) throw new IllegalStateException("Invalid renderer option: " + name);
            targets.add((Property<Boolean>) property);
        }
        for (Property<Boolean> target : targets) target.set(false);
        ((Property<Integer>) marker).set(1);
        return true;
    }
}
