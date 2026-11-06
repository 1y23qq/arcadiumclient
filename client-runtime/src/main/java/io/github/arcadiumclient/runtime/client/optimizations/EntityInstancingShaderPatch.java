// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.optimizations;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

public final class EntityInstancingShaderPatch {
    record Selection(boolean body, boolean modern) {}

    private EntityInstancingShaderPatch() {}

    static Selection select(boolean body, boolean modern, boolean gl43, int blocks, int bindings) {
        boolean bodySupported = body && gl43 && blocks >= 2 && bindings >= 7;
        boolean modernSupported = modern && ModernGlintShaderPatch.supportedCapabilities(gl43, blocks, bindings);
        if (bodySupported && modernSupported && blocks < 3) bodySupported = false;
        return new Selection(bodySupported, modernSupported);
    }

    public static String patch(String name, String source, String peer, boolean body, boolean modern,
                               boolean gl43, int blocks, int bindings) {
        boolean vertex = "argentum:entity_instancing.vert".equals(name);
        if ((!vertex && !"argentum:entity_instancing.frag".equals(name)) || source == null || peer == null) return source;
        Selection selected = select(body, modern, gl43, blocks, bindings);
        if (!selected.body && !selected.modern) return source;
        try {
            String rawVertex = vertex ? source : peer, rawFragment = vertex ? peer : source;
            if (!ModernGlintShaderPatch.supportedHashes(hash(rawVertex), hash(rawFragment))) return source;
            String patchedVertex = selected.body ? GpuResidentBodyShaders.verifiedVertex(rawVertex) : rawVertex;
            String patchedFragment = rawFragment;
            if (selected.modern) {
                patchedVertex = selected.body ? ModernGlintShaderPatch.residentVertex(patchedVertex) : ModernGlintShaderPatch.vertex(patchedVertex);
                patchedFragment = ModernGlintShaderPatch.fragment(rawFragment);
            }
            if (selected.body && vertex) GpuResidentBodyShaders.vertexPatched = true;
            if (selected.modern) {
                if (vertex) ModernGlintShaderPatch.vertexPatched = true;
                else ModernGlintShaderPatch.fragmentPatched = true;
            }
            return vertex ? patchedVertex : patchedFragment;
        } catch (Exception unsupported) {
            return source;
        }
    }

    private static String hash(String source) throws java.security.NoSuchAlgorithmException {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(source.getBytes(StandardCharsets.UTF_8)));
    }
}
