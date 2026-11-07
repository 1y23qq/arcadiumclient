// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.optimizations;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

public final class GpuVoxelSkinProjectionShaderPatch {
    public static final String TAG="ARC_VOXEL_PHYSICAL_MVP";
    private static final String DECLARATIONS="\n#define "+TAG+"\nlayout(location=14) in uint aArcVoxelProjectionRow;\nlayout(std430,binding=0) readonly buffer ArcVoxelPhysicalRows { mat4 arcVoxelMvp[]; };\nuniform int uArcVoxelProjectionMode;\nuniform uint uArcVoxelProjectionCount;\nuniform int uArcVoxelProjectionFallback;\nuniform mat4 uArcVoxelFallbackMvp;\n";
    private static final String DOT="vec4 arcVoxelProject(mat4 m, vec4 p) {\n    precise vec4 v = vec4(0.0);\n    v = fma(m[0], vec4(p.x), v);\n    v = fma(m[1], vec4(p.y), v);\n    v = fma(m[2], vec4(p.z), v);\n    v = fma(m[3], vec4(p.w), v);\n    return v;\n}\n\n";
    private GpuVoxelSkinProjectionShaderPatch(){}
    public static String patch(String name,String source,String peer) {
        boolean vertex="argentum:entity_instancing.vert".equals(name),fragment="argentum:entity_instancing.frag".equals(name);
        if((!vertex&&!fragment)||source==null||peer==null||source.contains(TAG))return source;
        if(!ModernGlintShaderPatch.supportedHashes(hash(vertex?source:peer),hash(vertex?peer:source)))return source;
        String result=once(source,"#version 130","#version 430 compatibility");
        if(!vertex)return result;
        result=once(result,"uniform bool uEyeLight;","uniform bool uEyeLight;"+DECLARATIONS);
        result=once(result,"void main() {",DOT+"void main() {");
        return once(result,"gl_Position = gl_ProjectionMatrix * eyePosition;","gl_Position = gl_ProjectionMatrix * eyePosition;\n    if (uArcVoxelProjectionMode == 1 && uBoxInstancing == 0) {\n        if (uArcVoxelProjectionFallback != 0) gl_Position = arcVoxelProject(uArcVoxelFallbackMvp, vec4(aPosition, 1.0));\n        else if (aArcVoxelProjectionRow < uArcVoxelProjectionCount) gl_Position = arcVoxelProject(arcVoxelMvp[aArcVoxelProjectionRow], vec4(aPosition, 1.0));\n    }");
    }
    private static String once(String source,String anchor,String replacement){int first=source.indexOf(anchor);if(first<0||source.indexOf(anchor,first+anchor.length())>=0)throw new IllegalArgumentException("Pinned voxel shader changed");return source.substring(0,first)+replacement+source.substring(first+anchor.length());}
    private static String hash(String source){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(source.getBytes(StandardCharsets.UTF_8)));}catch(java.security.GeneralSecurityException error){throw new IllegalStateException(error);}}
}
