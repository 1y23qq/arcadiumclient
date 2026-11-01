// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.qa;

import com.google.gson.GsonBuilder;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.lwjgl.BufferUtils;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.opengl.*;

public final class ParticleShader065Probe {
    private static final int[] CORNERS = {0, 1, 2, 0, 2, 3};
    private static final int CAPTURE_FLOATS = 15;
    private final Map<String, Object> report = new LinkedHashMap<>();
    private double positionError, uvError, colorError, lightError, clipError;
    private int checkedVertices, nativeLightVertices;

    public static void main(String[] args) throws Exception {
        Path shader = Path.of(args.length > 0 ? args[0] : "client-runtime/src/main/resources/assets/arcadium/shaders/particle_packets.vert");
        Path output = Path.of(args.length > 1 ? args[1] : "build/release065/particles/hidden-gl.json");
        ParticleShader065Probe probe = new ParticleShader065Probe();
        Throwable failure = null;
        try { probe.run(shader); probe.report.put("passed", true); }
        catch (Throwable error) { failure = error; probe.report.put("passed", false); probe.report.put("error", error.toString()); }
        Files.createDirectories(output.toAbsolutePath().getParent());
        Files.writeString(output, new GsonBuilder().setPrettyPrinting().create().toJson(probe.report));
        System.out.println(new GsonBuilder().create().toJson(probe.report));
        if (failure != null) throw new IllegalStateException("Particle shader validation failed", failure);
    }

    private void run(Path shaderPath) throws Exception {
        String source = Files.readString(shaderPath);
        report.put("shader", shaderPath.toAbsolutePath().toString());
        report.put("shaderSha256", HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(source.getBytes(java.nio.charset.StandardCharsets.UTF_8))));
        List<ParticleNative065Reference.Sample> samples = ParticleNative065Reference.samples(2048);
        report.put("nativeMathCases", ParticleNative065Reference.verifyMath(samples));
        GLFWErrorCallback callback = GLFWErrorCallback.createPrint(System.err);
        callback.set();
        long window = 0;
        int program = 0, vao = 0, input = 0, feedback = 0;
        try {
            require(GLFW.glfwInit(), "GLFW initialization failed");
            GLFW.glfwDefaultWindowHints();
            GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE);
            GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR, 3);
            GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR, 3);
            GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_PROFILE, GLFW.GLFW_OPENGL_COMPAT_PROFILE);
            window = GLFW.glfwCreateWindow(128, 96, "Arcadium Particle QA", 0, 0);
            require(window != 0, "Hidden OpenGL compatibility context unavailable");
            GLFW.glfwMakeContextCurrent(window);
            GL.createCapabilities();
            report.put("renderer", GL11.glGetString(GL11.GL_RENDERER));
            report.put("glVersion", GL11.glGetString(GL11.GL_VERSION));
            program = program(source);
            vao = GL30.glGenVertexArrays();
            input = GL15.glGenBuffers();
            feedback = GL15.glGenBuffers();
            GL30.glBindVertexArray(vao);
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, input);
            attribute(0, 4, 0);
            attribute(1, 4, 16);
            GL20.glEnableVertexAttribArray(2);
            GL30.glVertexAttribIPointer(2, 2, GL11.GL_UNSIGNED_INT, 64, 32L);
            GL33.glVertexAttribDivisor(2, 1);
            attribute(3, 4, 40);
            attribute(4, 1, 56);
            verify(samples.subList(0, 64), program, input, feedback, 128, 96, 0);
            GLFW.glfwSetWindowSize(window, 512, 288);
            GLFW.glfwPollEvents();
            verify(samples, program, input, feedback, 512, 288, 1);
            GL20.glUseProgram(0);
            GL20.glDeleteProgram(program);
            require(!GL20.glIsProgram(program), "Deleted shader program remained live");
            program = program(source);
            verify(samples.subList(0, 384), program, input, feedback, 512, 288, 2);
            require(GL11.glGetError() == GL11.GL_NO_ERROR, "OpenGL error after transform feedback");
            report.put("checkedVertices", checkedVertices);
            report.put("checkedComponents", checkedVertices * CAPTURE_FLOATS);
            report.put("maximumPositionError", positionError);
            report.put("maximumUvError", uvError);
            report.put("maximumColorError", colorError);
            report.put("maximumLightError", lightError);
            report.put("maximumClipPositionError", clipError);
            report.put("inputGrowthCases", List.of(64, 2048, 384));
            report.put("hiddenWindowResize", "128x96 -> 512x288");
            report.put("shaderDeleteAndReload", true);
            report.put("nativeSignedLightReference", true);
            report.put("nativeSignedLightVertices", nativeLightVertices);
            report.put("comparison", "Production vertex shader transform feedback versus actual original Particle.render vertices");
        } finally {
            if (window != 0) {
                GL20.glUseProgram(0);
                GL30.glBindVertexArray(0);
                if (program != 0) GL20.glDeleteProgram(program);
                if (input != 0) GL15.glDeleteBuffers(input);
                if (feedback != 0) GL15.glDeleteBuffers(feedback);
                if (vao != 0) GL30.glDeleteVertexArrays(vao);
                GLFW.glfwDestroyWindow(window);
            }
            GLFW.glfwTerminate();
            callback.free();
        }
    }

    private void verify(List<ParticleNative065Reference.Sample> samples, int program, int input, int feedback, int width, int height, int frame) {
        GL11.glViewport(0, 0, width, height);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glLoadIdentity();
        GL11.glTranslatef(.75f + frame, -1.25f + frame * .25f, 2.5f - frame);
        GL11.glRotatef(37 + frame * 19, 0, 1, 0);
        GL11.glScalef(.9f, .8f, 1.1f);
        float[] model = matrix(GL11.GL_MODELVIEW_MATRIX);
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glLoadIdentity();
        GL11.glFrustum(-1, 1, -height / (double) width, height / (double) width, .1, 300);
        float[] projection = matrix(GL11.GL_PROJECTION_MATRIX);
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        GL11.glMatrixMode(GL11.GL_TEXTURE);
        GL11.glLoadIdentity();
        GL11.glTranslatef(.05f, -.02f, 0);
        GL11.glScalef(.8f, .6f, 1);
        float[] uv = matrix(GL11.GL_TEXTURE_MATRIX);
        GL13.glActiveTexture(GL13.GL_TEXTURE1);
        GL11.glLoadIdentity();
        GL11.glTranslatef(8 / 256f, 8 / 256f, 0);
        GL11.glScalef(1 / 256f, 1 / 256f, 1);
        float[] light = matrix(GL11.GL_TEXTURE_MATRIX);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        IntBuffer packets = BufferUtils.createIntBuffer(samples.size() * 16);
        for (var sample : samples) packets.put(sample.packet());
        packets.flip();
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, input);
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, packets, GL15.GL_STREAM_DRAW);
        GL15.glBindBuffer(GL30.GL_TRANSFORM_FEEDBACK_BUFFER, feedback);
        GL15.glBufferData(GL30.GL_TRANSFORM_FEEDBACK_BUFFER, (long) samples.size() * 6 * CAPTURE_FLOATS * 4, GL15.GL_STREAM_READ);
        GL30.glBindBufferBase(GL30.GL_TRANSFORM_FEEDBACK_BUFFER, 0, feedback);
        GL20.glUseProgram(program);
        GL11.glEnable(GL30.GL_RASTERIZER_DISCARD);
        try {
            GL30.glBeginTransformFeedback(GL11.GL_TRIANGLES);
            GL31.glDrawArraysInstanced(GL11.GL_TRIANGLES, 0, 6, samples.size());
            GL30.glEndTransformFeedback();
        } finally { GL11.glDisable(GL30.GL_RASTERIZER_DISCARD); }
        FloatBuffer result = BufferUtils.createFloatBuffer(samples.size() * 6 * CAPTURE_FLOATS);
        GL15.glGetBufferSubData(GL30.GL_TRANSFORM_FEEDBACK_BUFFER, 0, result);
        FloatBuffer nativeLight = nativeLight(samples);
        for (int i = 0; i < samples.size(); i++) for (int vertex = 0; vertex < 6; vertex++) {
            var sample = samples.get(i);
            int[] original = sample.original();
            int at = CORNERS[vertex] * 7, out = (i * 6 + vertex) * CAPTURE_FLOATS;
            float x = Float.intBitsToFloat(original[at]), y = Float.intBitsToFloat(original[at + 1]), z = Float.intBitsToFloat(original[at + 2]);
            float[] position = {x, y, z};
            for (int axis = 0; axis < 3; axis++) positionError = Math.max(positionError, check(position[axis], result.get(out + axis), 2e-5, sample.scenario(), "position"));
            float[] expectedUv = transform(uv, Float.intBitsToFloat(original[at + 3]), Float.intBitsToFloat(original[at + 4]), 0, 1);
            for (int axis = 0; axis < 2; axis++) uvError = Math.max(uvError, check(expectedUv[axis], result.get(out + 3 + axis), 5e-6, sample.scenario(), "uv"));
            int color = original[at + 5];
            for (int channel = 0; channel < 4; channel++) colorError = Math.max(colorError,
                    check((color >>> (channel * 8) & 255) / 255f, result.get(out + 5 + channel), 5e-6, sample.scenario(), "color"));
            int packed = original[at + 6];
            float[] expectedLight = transform(light, (short) packed, (short) (packed >>> 16), 0, 1);
            for (int axis = 0; axis < 2; axis++) {
                float nativeValue = nativeLight.get((i * 4 + CORNERS[vertex]) * 2 + axis);
                check(expectedLight[axis], nativeValue, 5e-6, sample.scenario(), "native signed light attribute");
                lightError = Math.max(lightError, check(nativeValue, result.get(out + 9 + axis), 5e-6, sample.scenario(), "light"));
            }
            float[] eye = transform(model, x, y, z, 1), clip = transform(projection, eye[0], eye[1], eye[2], eye[3]);
            for (int axis = 0; axis < 4; axis++) clipError = Math.max(clipError, check(clip[axis], result.get(out + 11 + axis), 3e-5, sample.scenario(), "clip"));
            checkedVertices++;
        }
    }

    private FloatBuffer nativeLight(List<ParticleNative065Reference.Sample> samples) {
        int previousProgram = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM), previousVao = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
        int previousArray = GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING), previousFeedback = GL11.glGetInteger(GL30.GL_TRANSFORM_FEEDBACK_BUFFER_BINDING);
        boolean discard = GL11.glIsEnabled(GL30.GL_RASTERIZER_DISCARD);
        int program = 0, vao = 0, input = 0, feedback = 0;
        try {
            program = program("""
                    #version 330 compatibility
                    layout(location=0) in vec2 aNativeLight;
                    out vec2 vNativeLight;
                    void main(){
                        vNativeLight=(gl_TextureMatrix[1]*vec4(aNativeLight,0.0,1.0)).xy;
                        gl_Position=vec4(0.0,0.0,0.0,1.0);
                    }
                    """, new CharSequence[]{"vNativeLight"});
            vao = GL30.glGenVertexArrays(); input = GL15.glGenBuffers(); feedback = GL15.glGenBuffers();
            GL30.glBindVertexArray(vao);
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, input);
            IntBuffer words = BufferUtils.createIntBuffer(samples.size() * 28);
            for (var sample : samples) words.put(sample.original());
            words.flip();
            GL15.glBufferData(GL15.GL_ARRAY_BUFFER, words, GL15.GL_STREAM_DRAW);
            GL20.glEnableVertexAttribArray(0);
            GL20.glVertexAttribPointer(0, 2, GL11.GL_SHORT, false, 28, 24L);
            GL15.glBindBuffer(GL30.GL_TRANSFORM_FEEDBACK_BUFFER, feedback);
            GL15.glBufferData(GL30.GL_TRANSFORM_FEEDBACK_BUFFER, (long) samples.size() * 4 * 2 * 4, GL15.GL_STREAM_READ);
            GL30.glBindBufferBase(GL30.GL_TRANSFORM_FEEDBACK_BUFFER, 0, feedback);
            GL20.glUseProgram(program);
            GL11.glEnable(GL30.GL_RASTERIZER_DISCARD);
            GL30.glBeginTransformFeedback(GL11.GL_POINTS);
            GL11.glDrawArrays(GL11.GL_POINTS, 0, samples.size() * 4);
            GL30.glEndTransformFeedback();
            FloatBuffer result = BufferUtils.createFloatBuffer(samples.size() * 4 * 2);
            GL15.glGetBufferSubData(GL30.GL_TRANSFORM_FEEDBACK_BUFFER, 0, result);
            nativeLightVertices += samples.size() * 4;
            return result;
        } finally {
            if (!discard) GL11.glDisable(GL30.GL_RASTERIZER_DISCARD);
            GL20.glUseProgram(previousProgram); GL30.glBindVertexArray(previousVao);
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, previousArray);
            GL30.glBindBufferBase(GL30.GL_TRANSFORM_FEEDBACK_BUFFER, 0, previousFeedback);
            GL15.glBindBuffer(GL30.GL_TRANSFORM_FEEDBACK_BUFFER, previousFeedback);
            if (program != 0) GL20.glDeleteProgram(program);
            if (vao != 0) GL30.glDeleteVertexArrays(vao);
            if (input != 0) GL15.glDeleteBuffers(input);
            if (feedback != 0) GL15.glDeleteBuffers(feedback);
        }
    }

    private static double check(float expected, float actual, double tolerance, String sample, String component) {
        double error = Math.abs(expected - actual);
        require(Float.isFinite(actual) && error <= Math.max(tolerance, Math.abs(expected) * 2e-6),
                String.format(Locale.ROOT, "%s %s expected=%.9f actual=%.9f error=%.9f", sample, component, expected, actual, error));
        return error;
    }

    private static float[] transform(float[] matrix, float x, float y, float z, float w) {
        float[] result = new float[4];
        for (int row = 0; row < 4; row++) result[row] = matrix[row] * x + matrix[4 + row] * y + matrix[8 + row] * z + matrix[12 + row] * w;
        return result;
    }

    private static float[] matrix(int name) {
        FloatBuffer values = BufferUtils.createFloatBuffer(16);
        GL11.glGetFloatv(name, values);
        float[] matrix = new float[16];
        values.get(matrix);
        return matrix;
    }

    private static void attribute(int index, int size, long offset) {
        GL20.glEnableVertexAttribArray(index);
        GL20.glVertexAttribPointer(index, size, GL11.GL_FLOAT, false, 64, offset);
        GL33.glVertexAttribDivisor(index, 1);
    }

    private static int program(String source) {
        return program(source, new CharSequence[]{"qaPosition", "vUv", "vColor", "vLight", "gl_Position"});
    }

    private static int program(String source, CharSequence[] varyings) {
        int shader = GL20.glCreateShader(GL20.GL_VERTEX_SHADER), program = GL20.glCreateProgram();
        try {
            GL20.glShaderSource(shader, source);
            GL20.glCompileShader(shader);
            require(GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) != 0, GL20.glGetShaderInfoLog(shader));
            GL20.glAttachShader(program, shader);
            GL30.glTransformFeedbackVaryings(program, varyings, GL30.GL_INTERLEAVED_ATTRIBS);
            GL20.glLinkProgram(program);
            require(GL20.glGetProgrami(program, GL20.GL_LINK_STATUS) != 0, GL20.glGetProgramInfoLog(program));
            return program;
        } catch (RuntimeException failure) { GL20.glDeleteProgram(program); throw failure; }
        finally { GL20.glDeleteShader(shader); }
    }

    private static void require(boolean condition, String message) { ParticleNative065Reference.require(condition, message); }
}
