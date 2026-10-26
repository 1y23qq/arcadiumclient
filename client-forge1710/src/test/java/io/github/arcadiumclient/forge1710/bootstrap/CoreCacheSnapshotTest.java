package io.github.arcadiumclient.forge1710.bootstrap;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import makamys.coretweaks.repackage.com.esotericsoftware.kryo.kryo5.Kryo;
import makamys.coretweaks.repackage.com.esotericsoftware.kryo.kryo5.unsafe.UnsafeInput;
import makamys.coretweaks.repackage.com.esotericsoftware.kryo.kryo5.unsafe.UnsafeOutput;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class CoreCacheSnapshotTest {
    public static final class Data {
        String transformerClassName;
        Map<String, byte[]> transformationMap = new ConcurrentHashMap<>();
        public Data() { }
        Data(String name) { transformerClassName = name; }
    }

    public static final class SizeRaceMap extends ConcurrentHashMap<String, byte[]> {
        boolean armed;
        @Override public int size() {
            int size = super.size();
            if (armed) { armed = false; put("late", new byte[]{9, 8, 7}); }
            return size;
        }
    }

    private static Kryo codec() {
        Kryo kryo = new Kryo();
        kryo.register(ConcurrentHashMap.class);
        kryo.register(Data.class);
        kryo.register(byte[].class);
        kryo.register(SizeRaceMap.class);
        return kryo;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Data> roundTrip(Map<?, ?> source) {
        var bytes = new ByteArrayOutputStream();
        try (var output = new UnsafeOutput(bytes)) { codec().writeObject(output, source); }
        try (var input = new UnsafeInput(new ByteArrayInputStream(bytes.toByteArray()))) {
            Map<String, Data> result = codec().readObject(input, ConcurrentHashMap.class);
            assertEquals(bytes.size(), input.total());
            return result;
        }
    }

    @Test void reproducesThePinnedKryoMapSizeRaceAndSnapshotsItsMembership() {
        Data data = new Data("transformer");
        SizeRaceMap racing = new SizeRaceMap();
        racing.put("first", new byte[]{1, 2, 3});
        data.transformationMap = racing;
        var live = new ConcurrentHashMap<String, Data>();
        live.put("transformer", data);
        racing.armed = true;
        assertThrows(AssertionError.class, () -> roundTrip(live));
        racing.remove("late");
        racing.armed = true;
        Map<String, Data> result = roundTrip(CoreCacheSnapshot.snapshot(live));
        assertEquals("transformer", result.get("transformer").transformerClassName);
        assertArrayEquals(new byte[]{1, 2, 3}, result.get("transformer").transformationMap.get("first"));
        assertArrayEquals(new byte[]{9, 8, 7}, result.get("transformer").transformationMap.get("late"));
        assertEquals(ConcurrentHashMap.class, result.get("transformer").transformationMap.getClass());
    }

    @Test void roundTripsWhileLiveClassLoadingKeepsChangingTheOriginalMaps() throws Exception {
        Data data = new Data("side-transformer");
        byte[] bytes = new byte[]{4, 5, 6};
        for (int i = 0; i < 256; i++) data.transformationMap.put("class" + i, bytes);
        var live = new ConcurrentHashMap<String, Data>();
        live.put("side", data);
        AtomicBoolean running = new AtomicBoolean(true);
        Thread writer = new Thread(() -> {
            int i = 256;
            while (running.get()) {
                data.transformationMap.put("class" + i, bytes);
                data.transformationMap.remove("class" + (i++ - 256));
            }
        });
        writer.start();
        try {
            for (int i = 0; i < 25; i++) {
                Map<?, ?> snapshot = CoreCacheSnapshot.snapshot(live);
                Map<String, Data> restored = roundTrip(snapshot);
                Data copy = (Data) snapshot.get("side");
                assertNotSame(data, copy);
                assertNotSame(data.transformationMap, copy.transformationMap);
                assertEquals(copy.transformationMap.keySet(), restored.get("side").transformationMap.keySet());
                assertEquals("side-transformer", restored.get("side").transformerClassName);
                for (String key : copy.transformationMap.keySet()) {
                    assertSame(bytes, copy.transformationMap.get(key));
                    assertArrayEquals(bytes, restored.get("side").transformationMap.get(key));
                }
            }
        } finally { running.set(false); writer.join(5000); }
        assertFalse(writer.isAlive());
    }

    public static final class ChangedData {
        String transformerClassName;
        Map<String, byte[]> transformationMap = new ConcurrentHashMap<>();
        int unknownMetadata;
        public ChangedData() { }
    }

    @Test void rejectsAnUnexpectedDataSchemaInsteadOfDroppingMetadata() {
        var live = new ConcurrentHashMap<String, ChangedData>();
        live.put("changed", new ChangedData());
        assertThrows(IllegalStateException.class, () -> CoreCacheSnapshot.snapshot(live));
    }
}
