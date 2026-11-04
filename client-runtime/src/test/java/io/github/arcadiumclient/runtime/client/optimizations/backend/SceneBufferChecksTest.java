// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.optimizations.backend;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SceneBufferChecksTest {
    @Test void allocationBoundsAndOverflowAreRejectedBeforeNativeCalls() {
        assertEquals(1_048_576, new ResidentSceneBackend.Layout(8192, 128, 32, 64).actorBytes());
        assertThrows(IllegalArgumentException.class, () -> new ResidentSceneBackend.Layout(65_537, 128, 32, 64));
        assertThrows(IllegalArgumentException.class, () -> new ResidentSceneBackend.Layout(8192, 127, 32, 64));
        assertThrows(IllegalArgumentException.class, () -> new ResidentSceneBackend.Layout(8192, 128, 1028, 64));
        assertThrows(IllegalArgumentException.class, () -> new ResidentSceneBackend.Layout(8192, 128, 32, 16_388));
        assertDoesNotThrow(() -> SceneBufferChecks.range(Long.MAX_VALUE, 0, Long.MAX_VALUE));
        assertThrows(IndexOutOfBoundsException.class, () -> SceneBufferChecks.range(Long.MAX_VALUE, 1, Long.MAX_VALUE));
        assertThrows(IndexOutOfBoundsException.class, () -> SceneBufferChecks.range(1, Long.MAX_VALUE, Long.MAX_VALUE));
        assertThrows(IndexOutOfBoundsException.class, () -> SceneBufferChecks.range(129, 128, 256));
        assertEquals(8_294_400, SceneBufferChecks.rgbaBytes(1920, 1080));
        assertThrows(IllegalArgumentException.class, () -> SceneBufferChecks.rgbaBytes(16_384, 16_384));
    }

    @Test void commandsRespectTheCallersBufferWindowAndDoNotChangeItsState() {
        ByteBuffer command = ByteBuffer.allocateDirect(64).order(ByteOrder.LITTLE_ENDIAN);
        command.position(8);
        command.putInt(3).putInt(0).putInt(0).putInt(36).putInt(4).putInt(2);
        command.position(8).limit(32);
        command.order(ByteOrder.BIG_ENDIAN);
        SceneBufferChecks.commands(command, 1, 6);
        assertEquals(8, command.position()); assertEquals(32, command.limit());
        assertEquals(ByteOrder.BIG_ENDIAN, command.order());
        assertThrows(IndexOutOfBoundsException.class, () -> SceneBufferChecks.commands(command, 1, 5));
        assertThrows(IllegalArgumentException.class, () -> SceneBufferChecks.commands(command, 2, 6));
        assertThrows(IllegalArgumentException.class, () -> SceneBufferChecks.commands(command, Integer.MAX_VALUE, 6));
        assertThrows(IllegalArgumentException.class, () -> SceneBufferChecks.commands(ByteBuffer.allocate(24), 1, 6));
    }

    @Test void invalidResourcesAndSignedRangesCannotReachTheGpu() {
        ByteBuffer command = ByteBuffer.allocateDirect(24).order(ByteOrder.LITTLE_ENDIAN);
        command.putInt(1).putInt(0).putInt(0).putInt(3).putInt(0).putInt(1).flip();
        SceneBufferChecks.commands(command, 1, 1);
        command.putInt(0, 0);
        assertThrows(IllegalArgumentException.class, () -> SceneBufferChecks.commands(command, 1, 1));
        command.putInt(0, 1).putInt(16, -1);
        assertThrows(IndexOutOfBoundsException.class, () -> SceneBufferChecks.commands(command, 1, 1));
        command.putInt(16, 0).putInt(12, -1);
        assertThrows(IllegalArgumentException.class, () -> SceneBufferChecks.commands(command, 1, 1));
    }

    @Test void nonMacFactoryLeavesTheExistingRendererAlone() {
        if (System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("mac")) return;
        assertThrows(UnsupportedOperationException.class, () -> MetalResidentSceneBackend.open(
                Path.of("not-a-library"), new ResidentSceneBackend.Layout(1, 128, 32, 64), "", "vertexMain", "fragmentMain"));
    }
}
