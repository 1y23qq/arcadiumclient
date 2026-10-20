package org.polyfrost.overflowparticles.test;

import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicReference;
import org.polyfrost.overflowparticles.utils.NativeParticleType;
import static org.junit.jupiter.api.Assertions.*;

final class NativeParticleTypeTest {
    @Test void nativeCaseSensitiveNamesRemainDistinct() {
        assertSame(NativeParticleType.CRIT, NativeParticleType.fromName("crit"));
        assertSame(NativeParticleType.CRIT_MAGIC, NativeParticleType.fromName("magicCrit"));
        assertSame(NativeParticleType.SPELL_MOB_AMBIENT, NativeParticleType.fromName("mobSpellAmbient"));
        assertSame(NativeParticleType.FIREWORKS_SPARK, NativeParticleType.fromName("fireworksSpark"));
        assertNull(NativeParticleType.fromName("magiccrit"));
    }

    @Test void itemAndBlockMetadataNamesUseTheirOwnControls() {
        assertSame(NativeParticleType.ITEM_CRACK, NativeParticleType.fromName("iconcrack_351_4"));
        assertSame(NativeParticleType.BLOCK_CRACK, NativeParticleType.fromName("blockcrack_35_14"));
        assertSame(NativeParticleType.BLOCK_DUST, NativeParticleType.fromName("blockdust_35_14"));
        assertNotSame(NativeParticleType.fromName("blockcrack_35_14"), NativeParticleType.fromName("blockdust_35_14"));
    }

    @Test void unsupportedAndUnknownTypesAreNotFabricated() {
        assertNull(NativeParticleType.fromName("barrier"));
        assertNull(NativeParticleType.fromName("mobappearance"));
        assertNull(NativeParticleType.fromName("unknown_mod:effect"));
        assertNull(NativeParticleType.fromName(null));
    }

    @Test void everySupportedTypeHasAnUnambiguousNativeName() {
        for (NativeParticleType type : NativeParticleType.values())
            assertSame(type, NativeParticleType.fromName(type.getParticleName()));
    }

    @Test void nestedFactoriesAndOtherThreadsKeepSeparateIdentities() throws InterruptedException {
        try {
            NativeParticleType.current(NativeParticleType.BLOCK_CRACK);
            NativeParticleType previous = NativeParticleType.current();
            NativeParticleType.current(NativeParticleType.CRIT_MAGIC);
            assertSame(NativeParticleType.CRIT_MAGIC, NativeParticleType.current());
            NativeParticleType.current(previous);
            assertSame(NativeParticleType.BLOCK_CRACK, NativeParticleType.current());
            AtomicReference<Throwable> failure = new AtomicReference<>();
            Thread thread = new Thread(() -> {
                assertNull(NativeParticleType.current());
                NativeParticleType.current(NativeParticleType.WATER_DROP);
                assertSame(NativeParticleType.WATER_DROP, NativeParticleType.current());
                NativeParticleType.current(null);
            });
            thread.setUncaughtExceptionHandler((ignored, error) -> failure.set(error));
            thread.start(); thread.join();
            assertNull(failure.get(), "A different thread shared the active particle identity");
            assertSame(NativeParticleType.BLOCK_CRACK, NativeParticleType.current());
        } finally {
            NativeParticleType.current(null);
        }
        assertNull(NativeParticleType.current());
    }
}
