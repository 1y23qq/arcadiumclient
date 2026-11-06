// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.optimizations;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

public final class GpuResidentBodyShaders {
    static final String TAG="ARC_RESIDENT_PLAYER_BODIES";
    private static final String FRAGMENT="3d666a259d5dabbce045d0119b2d58f916424ffc967a0b93e5a2e50ec9e47b3d";
    private static final String MODE=System.getProperty("arcadium.residentBodies072.mode","off");
    public static boolean vertexPatched;
    public static boolean enabled() { return MODE.equals("control")||MODE.equals("active"); }
    static boolean active() { return MODE.equals("active"); }
    public static String patch(String name,String source,String fragment) {
        if(!enabled()||!name.equals("argentum:entity_instancing.vert")||source==null)return source;
        try {if(fragment==null||!FRAGMENT.equals(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(fragment.getBytes(StandardCharsets.UTF_8)))))return source;String result=vertex(source);vertexPatched=true;return result;}
        catch(Exception unsupported) {return source;}
    }
    private static final String ORIGINAL="357ab22c211148962f7218b63bdaeaafb38074b565d82b25e8b55203143581df";
    private static final String MODEL="mat4 model = mat4(vec4(aModel0, 0.0), vec4(aModel1, 0.0), vec4(aModel2, 0.0), vec4(aModel3, 1.0));";
    private static final String FUNCTIONS="""
        uniform int uArcResidentBodies;
        layout(std430,binding=0) readonly buffer ArcBodyBones { float arcBoneWords[]; };
        layout(std430,binding=1) readonly buffer ArcBodyUv { uint arcUvWords[]; };
        void arcClipBodyVertex() {
            gl_Position=vec4(2.0,2.0,2.0,1.0);
            vTexCoord=vGlintCoord=vLightCoord=vec2(0.0);
            vTextureLayer=vLighting=0.0;
            vColor=vOverlay=vec4(0.0);
        #ifdef USE_FOG
            vFogDistance=0.0;
        #endif
        }
        mat4 arcBodyMatrix(uint offset) {
            return mat4(vec4(arcBoneWords[offset],arcBoneWords[offset+1u],arcBoneWords[offset+2u],0.0),
                        vec4(arcBoneWords[offset+3u],arcBoneWords[offset+4u],arcBoneWords[offset+5u],0.0),
                        vec4(arcBoneWords[offset+6u],arcBoneWords[offset+7u],arcBoneWords[offset+8u],0.0),
                        vec4(arcBoneWords[offset+9u],arcBoneWords[offset+10u],arcBoneWords[offset+11u],1.0));
        }
        """;
    private static final String RESIDENT="""
        vec4 arcBoxTexture=aBoxTexture;
        vec3 arcBoxSize=aBoxSize;
        if(uArcResidentBodies!=0) {
            uint bone=uint(aTexCoord.x),profile=uint(aBoxTexture.z),mask=uint(aBoxTexture.y);
            if(bone>=12u||profile>=256u||uint(aBoxTexture.x)>=8192u||(mask&(1u<<bone))==0u) {arcClipBodyVertex();return;}
            uint actor=uArcResidentBodies==2?uint(aBoxTexture.x):uint(gl_InstanceID);
            uint matrixOffset=(actor*12u+bone)*12u,uv=profile*36u+bone*3u;
            if(matrixOffset+11u>=uint(arcBoneWords.length())||uv+2u>=uint(arcUvWords.length())) {arcClipBodyVertex();return;}
            model=arcBodyMatrix(matrixOffset);
            uvec3 arcMetadata=uvec3(arcUvWords[uv],arcUvWords[uv+1u],arcUvWords[uv+2u]);
            arcBoxTexture=vec4(float(arcMetadata.x&65535u),float(arcMetadata.x>>16u),float(arcMetadata.y&65535u),float(arcMetadata.y>>16u));
            arcBoxSize=vec3(float(arcMetadata.z&255u),float((arcMetadata.z>>8u)&255u),float((arcMetadata.z>>16u)&255u));
        }
        """;
    private GpuResidentBodyShaders() { }
    static String vertex(String nativeSource)throws Exception {
        String hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(nativeSource.getBytes(StandardCharsets.UTF_8)));
        if(!hash.equals(ORIGINAL)||!nativeSource.startsWith("#version 130\n"))throw new IllegalArgumentException("Unknown native body shader; keep original rendering");
        int main=nativeSource.indexOf("void main() {");
        if(main<0||nativeSource.indexOf(MODEL,main)<0||nativeSource.indexOf(MODEL,nativeSource.indexOf(MODEL)+1)>=0)
            throw new IllegalArgumentException("Unknown native body shader structure");
        String head=nativeSource.substring(0,main).replaceFirst("#version 130","#version 430 compatibility\n#define "+TAG);
        String body=nativeSource.substring(main).replace("aBoxTexture.","arcBoxTexture.").replace("aBoxSize.","arcBoxSize.");
        return head+FUNCTIONS+body.replace(MODEL,MODEL+"\n"+RESIDENT);
    }
}
