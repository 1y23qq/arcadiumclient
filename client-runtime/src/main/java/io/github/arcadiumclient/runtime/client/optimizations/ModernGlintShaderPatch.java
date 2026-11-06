// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.optimizations;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

public final class ModernGlintShaderPatch {
    private static final String VERTEX_SHA = "357ab22c211148962f7218b63bdaeaafb38074b565d82b25e8b55203143581df";
    private static final String FRAGMENT_SHA = "3d666a259d5dabbce045d0119b2d58f916424ffc967a0b93e5a2e50ec9e47b3d";
    private static final String TAG = "ARC_DEFAULT_MODERN_ARMOR_GLINT";
    public static boolean vertexPatched, fragmentPatched;

    private ModernGlintShaderPatch() {}

    public static boolean supportedCapabilities(boolean gl43, int vertexStorageBlocks, int storageBindings) {
        return gl43 && vertexStorageBlocks >= 1 && storageBindings >= 8;
    }
    public static boolean qualificationEnabled(boolean qualified, boolean experimental) { return qualified || experimental; }

    public static String peer(String name) {
        return switch (name) {
            case "argentum:entity_instancing.vert" -> "argentum:entity_instancing.frag";
            case "argentum:entity_instancing.frag" -> "argentum:entity_instancing.vert";
            default -> null;
        };
    }

    public static String patch(String name, String source, String peerSource, boolean gl43, boolean enabled) {
        if (!enabled || !gl43 || source == null || source.contains(TAG)) return source;
        boolean vertex = name.equals("argentum:entity_instancing.vert"), fragment = name.equals("argentum:entity_instancing.frag");
        if (!vertex && !fragment) return source;
        try {
            if (peerSource == null || !supportedHashes(hash(vertex ? source : peerSource), hash(vertex ? peerSource : source))) return source;
            String result = vertex ? vertex(source) : fragment(source);
            if (vertex) vertexPatched = true; else fragmentPatched = true;
            return result;
        } catch (Exception unsupported) { return source; }
    }

    private static String hash(String source) throws java.security.NoSuchAlgorithmException {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(source.getBytes(StandardCharsets.UTF_8)));
    }
    static boolean supportedHashes(String vertex, String fragment) { return VERTEX_SHA.equals(vertex) && FRAGMENT_SHA.equals(fragment); }

    static String vertex(String source) {
        source = replaceOnce(source, "#version 130", "#version 430 compatibility\n#define " + TAG);
        source = replaceOnce(source, "out vec2 vGlintCoord;", "out vec2 vGlintCoord;\nflat out int arcadiumModernGlint;\n" +
                "layout(std430,binding=7) readonly buffer ArcadiumModernGlintMatrices { mat4 arcadiumGlintMatrices[]; };\n" +
                "uniform int uArcadiumModernGlintCount;\nuniform int uArcadiumModernArmorMvp;\n" +
                "vec4 arcadiumProject(mat4 m, vec4 p) {\n" +
                "    precise vec4 result = vec4(0.0);\n" +
                "    result = fma(m[0], vec4(p.x), result);\n" +
                "    result = fma(m[1], vec4(p.y), result);\n" +
                "    result = fma(m[2], vec4(p.z), result);\n" +
                "    result = fma(m[3], vec4(p.w), result);\n" +
                "    return result;\n" +
                "}");
        source = replaceOnce(source, "gl_Position = gl_ProjectionMatrix * eyePosition;",
                "int arcadiumMvp = int(aBoxTexture.x) - 1;\n" +
                "    bool arcadiumMvpValid = uArcadiumModernArmorMvp != 0 && arcadiumMvp >= 0 && arcadiumMvp < uArcadiumModernGlintCount;\n" +
                "    gl_Position = arcadiumMvpValid ? arcadiumProject(arcadiumGlintMatrices[arcadiumMvp], vec4(aPosition, 1.0)) : gl_ProjectionMatrix * eyePosition;");
        source = replaceOnce(source, "if (uGlintPass >= 0) {", "int arcadiumMatrix = int(-aEffectTime - 1.0);\n" +
                "    bool arcadiumModern = uGlintPass >= 0 && aEffectTime <= -1.0 && arcadiumMatrix >= 0 && arcadiumMatrix < uArcadiumModernGlintCount;\n" +
                "    arcadiumModernGlint = arcadiumModern ? 1 : 0;\n" +
                "    if (arcadiumModern) {\n" +
                "        vTexCoord = (arcadiumGlintMatrices[arcadiumMatrix] * vec4(baseTexCoord, 0.0, 1.0)).xy;\n" +
                "        vGlintCoord = vTexCoord;\n" +
                "    } else if (uGlintPass >= 0) {");
        return source;
    }

    static String fragment(String source) {
        source = replaceOnce(source, "#version 130", "#version 430 compatibility\n#define " + TAG);
        source = replaceOnce(source, "in vec2 vGlintCoord;", "in vec2 vGlintCoord;\nflat in int arcadiumModernGlint;");
        source = replaceOnce(source, "if (uGlintPass >= 0 || uItemGlintPass == 1) {", "if (arcadiumModernGlint != 0) {\n" +
                "        if (color.a <= 0.1) discard;\n" +
                "        fragColor = shaded(color);\n" +
                "        return;\n" +
                "    }\n" +
                "    if (uGlintPass >= 0 || uItemGlintPass == 1) {");
        return source;
    }

    private static String replaceOnce(String input, String anchor, String replacement) {
        int first = input.indexOf(anchor);
        if (first < 0 || input.indexOf(anchor, first + anchor.length()) >= 0) throw new IllegalArgumentException("Unsupported native glint shader anchor");
        return input.substring(0, first) + replacement + input.substring(first + anchor.length());
    }
    public static void reset() { vertexPatched = fragmentPatched = false; }
}
