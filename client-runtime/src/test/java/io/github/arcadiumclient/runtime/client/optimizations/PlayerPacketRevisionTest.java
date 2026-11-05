// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.optimizations;

import java.lang.reflect.Field;
import org.joml.Matrix4f;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PlayerPacketRevisionTest {
    @BeforeEach @AfterEach void clear() { GpuPlayerPackets.resetTemplates(); }

    @Test void poseMutationInvalidatesResidentAndPoseStreamsWithoutInvalidatingActorWords() throws Exception {
        int template = GpuPlayerPackets.registerTemplate(template());
        int job = GpuPlayerPackets.addPose(new Matrix4f(), template);
        long actor = number("playerRevision"), pose = number("poseRevision"), state = number("stateRevision");
        GpuPlayerPackets.writePose(job, 0, 1, 2, 3, .1f, .2f, .3f);
        assertEquals(actor, number("playerRevision"));
        assertTrue(number("poseRevision") > pose);
        assertTrue(number("stateRevision") > state);
        assertEquals(1, GpuPlayerPackets.count());
    }

    @Test void sameCountJobReplacementCannotReuseEitherPreviouslyUploadedStream() throws Exception {
        int template = GpuPlayerPackets.registerTemplate(template());
        int first = GpuPlayerPackets.addPose(new Matrix4f().translation(1, 2, 3), template);
        GpuPlayerPackets.writePose(first, 0, 0, 0, 0, 0, 0, 0);
        long actor = number("playerRevision"), pose = number("poseRevision"), state = number("stateRevision");
        GpuPlayerPackets.rollback(first);
        int replacement = GpuPlayerPackets.addPose(new Matrix4f().translation(7, 8, 9), template);
        GpuPlayerPackets.writePose(replacement, 0, 0, 0, 0, 0, 0, 0);
        assertEquals(first, replacement);
        assertEquals(1, GpuPlayerPackets.count());
        assertTrue(number("playerRevision") > actor);
        assertTrue(number("poseRevision") > pose);
        assertTrue(number("stateRevision") > state);
        assertEquals(7f, GpuPlayerPackets.resolve(replacement * 16, new Matrix4f()).m30());
    }

    @Test void aNewBatchInvalidatesStreamingAndResidentReuseEvenIfNoRevisionWasMutated() throws Exception {
        int template = GpuPlayerPackets.registerTemplate(template());
        GpuPlayerPackets.addPose(new Matrix4f(), template);
        long state = number("stateRevision"), generation = residentBatch();
        GpuPlayerPackets.beginBatch();
        assertEquals(state, number("stateRevision"));
        assertEquals(-1, number("uploadedPlayerRevision"));
        assertEquals(-1, number("uploadedPoseRevision"));
        assertTrue(residentBatch() > generation);
        assertEquals(0, GpuPlayerPackets.count());
    }

    private static long number(String name) throws Exception {
        Field field = GpuPlayerPackets.class.getDeclaredField(name); field.setAccessible(true);
        return field.getLong(null);
    }
    private static long residentBatch() throws Exception {
        Field field = GpuResidentPlayerState.class.getDeclaredField("batch"); field.setAccessible(true);
        return field.getLong(null);
    }
    private static float[] template() {
        float[] data = new float[12 * 16];
        for (int part = 0; part < 12; part++) data[part * 16 + 12] = data[part * 16 + 13] = data[part * 16 + 14] = 1;
        return data;
    }
}
