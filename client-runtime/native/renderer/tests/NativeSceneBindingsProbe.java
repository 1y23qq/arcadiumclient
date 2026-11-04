// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.optimizations.backend;

import java.nio.file.Path;

public final class NativeSceneBindingsProbe {
    public static void main(String[] arguments) {
        if (arguments.length != 1) throw new IllegalArgumentException("Pass the unsupported-platform native library");
        try {
            new MetalResidentSceneBackend(Path.of(arguments[0]), new ResidentSceneBackend.Layout(8192, 128, 32, 64), "", "vertexMain", "fragmentMain");
            throw new AssertionError("An unsupported platform must not create a Metal renderer");
        } catch (IllegalStateException failure) {
            if (!failure.getMessage().contains("Metal is unavailable")) throw failure;
        }
        System.out.println("Resident scene FFM ABI symbols, create/error calls and unsupported-device fallback passed");
    }
}
