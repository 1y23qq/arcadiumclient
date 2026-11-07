// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.qa;

import java.lang.reflect.Field;
import java.nio.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import net.minecraft.client.render.vertex.BufferBuilder;
import net.minecraft.client.render.vertex.VertexFormat;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.*;

public final class HologramNameFont073Evidence {
    private static final IdentityHashMap<Object, String> labels = new IdentityHashMap<>();
    private static final IdentityHashMap<Object, Map<String, Object>> active = new IdentityHashMap<>();
    private static final List<Map<String, Object>> calls = new ArrayList<>();
    private static final List<String> failures = new ArrayList<>();
    private static int ordinal;

    private HologramNameFont073Evidence() { }
    public static Map<String, Object> report() { return Map.of("readOnly", true, "target", "TextBatcher only", "calls", calls, "failures", failures); }
    public static void label(Object batcher, String text) {
        if (HologramNameNative073Qa.observeFontPhase()) labels.put(batcher, text);
    }
    public static void begin(Object batcher, Object segment, int alpha, float x, float y) {
        if (!HologramNameNative073Qa.observeFontPhase() || !String.valueOf(labels.get(batcher)).contains("Changed073 9")) return;
        try {
            int bufferAlpha = (int) field(segment, "bufferAlpha"), lastAlpha = (int) field(segment, "lastAlpha"); Object buffer = field(segment, "buffer");
            Map<String, Object> call = new LinkedHashMap<>(); call.put("ordinal", ++ordinal); call.put("imageSerial", HologramNameNative073Qa.observerImageSerial()); call.put("label", labels.get(batcher));
            call.put("alpha", alpha); call.put("xBits", Float.floatToRawIntBits(x)); call.put("yBits", Float.floatToRawIntBits(y)); call.put("bufferAlphaBefore", bufferAlpha); call.put("lastAlphaBefore", lastAlpha); call.put("bufferPresentBefore", buffer != null);
            VertexFormat format = (VertexFormat) field(segment, "format"); int[] words = (int[]) field(segment, "vertices"); call.put("strideBytes", format.getVertexSize()); call.put("sourceVertexBytes", words.length * 4); call.put("vertexCount", words.length * 4 / format.getVertexSize()); call.put("texture", String.valueOf(field(segment, "texture")));
            call.put("expectedNativeRecipe", buffer != null && bufferAlpha == alpha ? "existing VBO" : lastAlpha == alpha ? "upload then VBO" : "CPU translated vertices");
            call.put("modelViewBefore", matrix()); call.put("segmentBefore", saveWords(words, prefix(call) + "-segment-before.bin"));
            calls.add(call); active.put(batcher, call);
        } catch (Exception error) { failures.add("begin: " + error); }
    }
    public static void end(Object batcher, Object segment) {
        Map<String, Object> call = active.remove(batcher); if (call == null) return;
        try {
            call.put("bufferAlphaAfter", field(segment, "bufferAlpha")); call.put("lastAlphaAfter", field(segment, "lastAlpha")); call.put("bufferPresentAfter", field(segment, "buffer") != null);
            call.put("segmentAfter", saveWords((int[]) field(segment, "vertices"), prefix(call) + "-segment-after.bin")); call.put("modelViewAfter", matrix());
        } catch (Exception error) { failures.add("end: " + error); }
    }
    public static void cpu(Object batcher, BufferBuilder builder) {
        Map<String, Object> call = active.get(batcher); if (call == null) return;
        try {
            List<Field> fields = Arrays.stream(builder.getClass().getDeclaredFields()).filter(field -> field.getType() == ByteBuffer.class).toList();
            if (fields.size() != 1) throw new IllegalStateException("Ambiguous native builder storage: " + fields.size());
            fields.getFirst().setAccessible(true); ByteBuffer raw = ((ByteBuffer) fields.getFirst().get(builder)).duplicate();
            int bytes = Math.multiplyExact(builder.getVertexCount(), builder.getFormat().getVertexSize()); raw.position(0).limit(bytes);
            call.put("actualNativeRecipe", "CPU translated vertices"); call.put("drawModelView", matrix()); call.put("actualVertices", save(raw, prefix(call) + "-cpu-vertices.bin"));
            call.put("vertexCount", builder.getVertexCount()); call.put("strideBytes", builder.getFormat().getVertexSize());
        } catch (Exception error) { failures.add("cpu: " + error); }
    }
    public static void vbo(Object batcher) {
        Map<String, Object> call = active.get(batcher); if (call == null) return;
        try {
            if (!GL.getCapabilities().OpenGL45) throw new IllegalStateException("Read-only named-buffer observation requires GL4.5 in this synthetic fixture");
            int id = GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING); if (id <= 0) throw new IllegalStateException("Native font VBO was not bound");
            int bytes = GL45.glGetNamedBufferParameteri(id, GL15.GL_BUFFER_SIZE); if (bytes < 0 || bytes > 2 * 1024 * 1024) throw new IllegalStateException("Unexpected native font VBO size: " + bytes);
            ByteBuffer data = BufferUtils.createByteBuffer(bytes); GL45.glGetNamedBufferSubData(id, 0, data);
            call.put("actualNativeRecipe", "VBO matrix translation"); call.put("nativeVboId", id); call.put("drawModelView", matrix()); call.put("actualVertices", save(data, prefix(call) + "-vbo-vertices.bin"));
            if (GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING) != id) throw new IllegalStateException("Read-only VBO observation changed the binding");
        } catch (Exception error) { failures.add("vbo: " + error); }
    }
    public static void endImage() { active.clear(); labels.clear(); }
    private static String prefix(Map<String, Object> call) { return String.format(java.util.Locale.ROOT, "font-image%02d-call%03d", (int) call.get("imageSerial"), (int) call.get("ordinal")); }
    private static Object field(Object instance, String name) throws Exception { Field field = instance.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(instance); }
    private static Map<String, Object> saveWords(int[] words, String name) throws Exception { ByteBuffer data = ByteBuffer.allocate(words.length * 4).order(ByteOrder.nativeOrder()); data.asIntBuffer().put(words); return save(data, name); }
    private static Map<String, Object> save(ByteBuffer data, String name) throws Exception {
        ByteBuffer view = data.duplicate(); byte[] bytes = new byte[view.remaining()]; view.get(bytes); Path path = Path.of(System.getProperty("arcadium.hologramNames073.output")).resolveSibling(name); Files.write(path, bytes);
        return Map.of("path", path.toString(), "bytes", bytes.length, "sha256", HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)), "byteOrder", ByteOrder.nativeOrder().toString());
    }
    private static List<Integer> matrix() { FloatBuffer data = BufferUtils.createFloatBuffer(16); GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, data); List<Integer> bits = new ArrayList<>(16); for (int i = 0; i < 16; i++) bits.add(Float.floatToRawIntBits(data.get(i))); return bits; }
}
