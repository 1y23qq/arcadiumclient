// SPDX-License-Identifier: GPL-3.0-only
package org.polyfrost.oneconfig.internal;

//? if = 26.2 {
/*import com.google.gson.JsonPrimitive;
import com.mojang.serialization.JsonOps;
import net.minecraft.client.PreferredGraphicsApi;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PreferredGraphicsBackendCodecTest {
    @Test
    void launcherOpenGlValueMatchesTheNativeOptionCodec() {
        var encoded = PreferredGraphicsApi.CODEC.encodeStart(JsonOps.INSTANCE, PreferredGraphicsApi.OPENGL).getOrThrow();
        assertEquals("\"opengl\"", encoded.toString());
        assertEquals(PreferredGraphicsApi.OPENGL,
            PreferredGraphicsApi.CODEC.parse(JsonOps.INSTANCE, new JsonPrimitive("opengl")).getOrThrow());
    }
}
*///? }
