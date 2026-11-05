// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.optimizations;

import it.unimi.dsi.fastutil.objects.Reference2ObjectLinkedOpenHashMap;
import java.util.*;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class GpuVoxelSkinOrderingTest {
    @Test void voxelGeometryMovesBeforeDeferredBodiesWithoutChangingInstanceArrays() throws Throwable {
        var parts = new Reference2ObjectLinkedOpenHashMap<Object,Object>();
        Object body = new Object(), secondBody = new Object(), voxel = new Object();
        int[] bodyInstances = {1,2,3}, secondInstances = {4,5,6}, voxelInstances = {7,8,9};
        parts.put(body, bodyInstances); parts.put(secondBody, secondInstances); parts.put(voxel, voxelInstances);
        var method = GpuVoxelSkinNativeAccess.orderingMethod(parts.getClass());
        GpuVoxelSkinNativeAccess.prioritize(method, parts, voxel, voxelInstances);
        assertEquals(List.of(voxel, body, secondBody), new ArrayList<>(parts.keySet()));
        assertSame(bodyInstances, parts.get(body)); assertSame(voxelInstances, parts.get(voxel));
        assertArrayEquals(new int[]{7,8,9}, voxelInstances);
        GpuVoxelSkinNativeAccess.prioritize(method, parts, voxel, voxelInstances);
        assertEquals(List.of(voxel, body, secondBody), new ArrayList<>(parts.keySet()));
    }

    @Test void missingOrUnknownBindingsFailBeforeAnInstanceIsQueued() throws Throwable {
        assertThrows(NoSuchMethodException.class, () -> GpuVoxelSkinNativeAccess.orderingMethod(LinkedHashMap.class));
        var parts = new Reference2ObjectLinkedOpenHashMap<Object,Object>(); Object body = new Object(), instances = new Object(); parts.put(body, instances);
        var method = GpuVoxelSkinNativeAccess.orderingMethod(parts.getClass());
        assertThrows(IllegalStateException.class, () -> GpuVoxelSkinNativeAccess.prioritize(method, parts, new Object(), instances));
        assertEquals(List.of(body), new ArrayList<>(parts.keySet()));
    }

    @Test void pinnedNativeBatchUsesTheExpectedLinkedGeometryMap() throws Exception {
        String jar = System.getProperty("arcadium.voxelSkin072.argentumJar");
        assumeTrue(jar != null, "Run the native dependency ABI check with the pinned Argentum jar");
        List<String> found = new ArrayList<>();
        try (ZipFile zip = new ZipFile(jar)) {
            var entry = zip.getEntry("dev/rdh/argentum/impl/render/entity/instancing/InstanceBatcher$TextureBatch.class");
            assertNotNull(entry);
            new ClassReader(zip.getInputStream(entry)).accept(new ClassVisitor(Opcodes.ASM9) {
                @Override public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
                    if (name.equals("parts")) found.add(descriptor); return null;
                }
            }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        }
        assertEquals(List.of("Lit/unimi/dsi/fastutil/objects/Reference2ObjectLinkedOpenHashMap;"), found);
    }
}
