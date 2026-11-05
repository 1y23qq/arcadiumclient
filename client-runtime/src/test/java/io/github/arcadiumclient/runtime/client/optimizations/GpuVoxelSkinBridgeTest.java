// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.optimizations;

import java.lang.reflect.Modifier;
import java.net.URLClassLoader;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

public class GpuVoxelSkinBridgeTest {
    @Test void primitiveScaleGettersReadTheCurrentConfigurationObject() throws Throwable {
        Config first = new Config(1.15f, 1.05f, 1.18f, 1.1f, 1.1f);
        Config replacement = new Config(1.3f, 1.2f, 1.4f, 1.05f, 1.25f);
        assertEquals(new GpuVoxelSkinMath.Options(1.15f, 1.05f, 1.18f, 1.1f, 1.1f), GpuVoxelSkinBridge.readOptions(first));
        assertEquals(new GpuVoxelSkinMath.Options(1.3f, 1.2f, 1.4f, 1.05f, 1.25f), GpuVoxelSkinBridge.readOptions(replacement));
        first.base = 1.6f;
        assertEquals(1.6f, GpuVoxelSkinBridge.readOptions(first).base());
    }

    @Test void invalidOrUnknownScaleLayoutCannotEnterTheBatchedPath() {
        assertThrows(IllegalArgumentException.class, () -> GpuVoxelSkinBridge.readOptions(new Config(Float.NaN, 1, 1, 1, 1)));
        assertThrows(IllegalArgumentException.class, () -> GpuVoxelSkinBridge.readOptions(new Config(0, 1, 1, 1, 1)));
        assertThrows(IllegalStateException.class, () -> GpuVoxelSkinBridge.readOptions(new Object()));
    }

    @Test void pinnedLegacySkinLayersHasTheExpectedSingletonPrimitiveAndMeshOffsetAbi() throws Throwable {
        String jar = System.getProperty("arcadium.voxelSkin072.pinnedJar");
        assumeTrue(jar != null, "Run the native dependency ABI check with the pinned LegacySkinLayers jar");
        try (URLClassLoader loader = new URLClassLoader(new java.net.URL[]{Path.of(jar).toUri().toURL()}, getClass().getClassLoader())) {
            Class<?> mod = Class.forName("org.polyfrost.skinlayers.SkinLayers", false, loader);
            Class<?> config = Class.forName("org.polyfrost.skinlayers.config.SkinLayersConfig", false, loader);
            var singleton = mod.getField("INSTANCE");
            assertEquals(mod, singleton.getType()); assertTrue(Modifier.isStatic(singleton.getModifiers()));
            assertEquals(config, mod.getMethod("getConfig").getReturnType());
            assertEquals(new GpuVoxelSkinMath.Options(1.15f, 1.05f, 1.18f, 1.1f, 1.1f),
                    GpuVoxelSkinBridge.readOptions(config.getConstructor().newInstance()));
            Class<?> offset = Class.forName("org.polyfrost.skinlayers.render.MeshOffset", false, loader);
            assertEquals("org.polyfrost.skinlayers.render.MeshShape", offset.getDeclaredField("shape").getType().getName());
            for (String field : new String[]{"mirrored", "firstPerson", "skull"}) assertEquals(boolean.class, offset.getDeclaredField(field).getType());
            Class<?> shape = offset.getDeclaredField("shape").getType();
            var constructor = offset.getConstructor(shape, boolean.class, boolean.class, boolean.class);
            for (Object value : shape.getEnumConstants()) {
                String name = ((Enum<?>)value).name();
                for (boolean mirrored : new boolean[]{false, true}) {
                    Object actual = constructor.newInstance(value, mirrored, false, false);
                    assertEquals(new GpuVoxelSkinBridge.Parameters(name, mirrored, false, false), GpuVoxelSkinBridge.parameters(actual));
                }
            }
            assertEquals(float[].class, Class.forName("org.polyfrost.skinlayers.render.CompiledMesh", false, loader).getDeclaredField("quads").getType());
            Class<?> part = Class.forName("org.polyfrost.skinlayers.mixin.ModelPartMixin", false, loader);
            assertEquals(offset, part.getDeclaredField("skinLayers$offset").getType());
            assertEquals("org.polyfrost.skinlayers.render.CompiledMesh", part.getDeclaredField("skinLayers$mesh").getType().getName());
        }
    }

    public static final class Config {
        private float base;
        private final float body, head, first, skull;
        Config(float base, float body, float head, float first, float skull) { this.base = base; this.body = body; this.head = head; this.first = first; this.skull = skull; }
        public float getBaseVoxelSize() { return base; }
        public float getBodyVoxelWidthSize() { return body; }
        public float getHeadVoxelSize() { return head; }
        public float getFirstPersonPixelScaling() { return first; }
        public float getSkullVoxelSize() { return skull; }
    }
}
