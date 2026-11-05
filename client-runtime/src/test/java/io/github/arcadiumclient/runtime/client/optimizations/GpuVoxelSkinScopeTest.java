// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.optimizations;

import java.util.*;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class GpuVoxelSkinScopeTest {
    @Test void borrowedScopeRemainsActiveAfterSuccessfulAndFailedCleanup() throws Exception {
        Device device = new Device(); device.isActive = true;
        cleanup(device, () -> device.access());
        assertThrows(IllegalStateException.class, () -> cleanup(device, () -> { throw new IllegalStateException("delete failed"); }));
        device.access(); assertTrue(device.isActive); assertEquals(0, device.enters); assertEquals(0, device.exits);
    }

    @Test void ownedScopeIsReleasedAfterSuccessfulAndFailedCleanup() throws Exception {
        Device device = new Device(); cleanup(device, () -> device.access());
        assertFalse(device.isActive); assertEquals(1, device.enters); assertEquals(1, device.exits);
        assertThrows(IllegalStateException.class, () -> cleanup(device, () -> { throw new IllegalStateException("command list creation failed"); }));
        assertFalse(device.isActive); assertEquals(2, device.enters); assertEquals(2, device.exits);
    }

    @Test void nestedCleanupDoesNotDeactivateItsOwningCaller() throws Exception {
        Device device = new Device();
        cleanup(device, () -> {
            try { cleanup(device, () -> device.access()); }
            catch (Exception failure) { throw new AssertionError(failure); }
            device.access(); assertEquals(1, device.enters); assertEquals(0, device.exits);
        });
        assertFalse(device.isActive); assertEquals(1, device.enters); assertEquals(1, device.exits);
    }

    @Test void incompatibleScopeAbiCannotBeUsed() {
        assertThrows(NoSuchFieldException.class, () -> GpuVoxelSkinNativeAccess.managedActiveField(WrongDevice.class));
    }

    @Test void pinnedDeviceUsesBooleanActiveStateAndExpectedScopeMethods() throws Exception {
        String jar = System.getProperty("arcadium.voxelSkin072.celeritasJar");
        assumeTrue(jar != null, "Run the native dependency ABI check with the pinned Celeritas jar");
        Map<String,String> fields = new HashMap<>(), methods = new HashMap<>();
        try (ZipFile zip = new ZipFile(jar)) {
            var entry = zip.getEntry("org/embeddedt/embeddium/impl/gl/device/GLRenderDevice.class"); assertNotNull(entry);
            new ClassReader(zip.getInputStream(entry)).accept(new ClassVisitor(Opcodes.ASM9) {
                @Override public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) { fields.put(name, descriptor); return null; }
                @Override public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) { methods.put(name, descriptor); return null; }
            }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        }
        assertEquals("Z", fields.get("isActive")); assertEquals("()V", methods.get("makeActive")); assertEquals("()V", methods.get("makeInactive"));
        assertEquals("()Lorg/embeddedt/embeddium/impl/gl/device/CommandList;", methods.get("createCommandList"));
    }

    private static void cleanup(Device device, Runnable operation) throws Exception {
        boolean owned = GpuVoxelSkinNativeAccess.acquireManagedScope(GpuVoxelSkinNativeAccess.managedActiveField(device.getClass()), device, device::enter);
        try { operation.run(); } finally { GpuVoxelSkinNativeAccess.releaseManagedScope(owned, device::exit); }
    }
    private static final class Device {
        private boolean isActive;
        private int enters, exits;
        void enter() { if (!isActive) { isActive = true; enters++; } }
        void exit() { if (isActive) { isActive = false; exits++; } }
        void access() { if (!isActive) throw new IllegalStateException("Caller scope was closed"); }
    }
    private static final class WrongDevice { @SuppressWarnings("unused") private int isActive; }
}
