// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.qa;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

public final class GpuVoxelSkin072EyeShader {
    public static final String VERTEX_SHA="357ab22c211148962f7218b63bdaeaafb38074b565d82b25e8b55203143581df";
    public static final String FRAGMENT_SHA="3d666a259d5dabbce045d0119b2d58f916424ffc967a0b93e5a2e50ec9e47b3d";
    public static final String TAG="ARC_VOXEL_EYE_POSITION_QA";
    private static final String DECLARATIONS="\n#define "+TAG+"\n"
            +"uniform mat4 uArcVoxelEyeMatrices[64];\n"
            +"uniform int uArcVoxelEyeMode;\nuniform int uArcVoxelEyeCount;\n";
    private static final String OVERRIDE="\n    if (uArcVoxelEyeMode != 0) {\n"
            +"        int arcIndex = int(-aEffectTime - 1.0);\n"
            +"        if (arcIndex >= 0 && arcIndex < uArcVoxelEyeCount) {\n"
            +"            mat4 arcMatrix = uArcVoxelEyeMatrices[arcIndex];\n"
            +"            if (uArcVoxelEyeMode == 1) {\n"
            +"                gl_Position = gl_ProjectionMatrix * (arcMatrix * vec4(aPosition, 1.0));\n"
            +"            } else {\n"
            +"                precise vec4 arcX = arcMatrix[0] * aPosition.x;\n"
            +"                precise vec4 arcY = arcMatrix[1] * aPosition.y;\n"
            +"                precise vec4 arcZ = arcMatrix[2] * aPosition.z;\n"
            +"                precise vec4 arcXY = arcX + arcY;\n"
            +"                precise vec4 arcXYZ = arcXY + arcZ;\n"
            +"                precise vec4 arcEye = arcXYZ + arcMatrix[3];\n"
            +"                precise vec4 arcPX = gl_ProjectionMatrix[0] * arcEye.x;\n"
            +"                precise vec4 arcPY = gl_ProjectionMatrix[1] * arcEye.y;\n"
            +"                precise vec4 arcPZ = gl_ProjectionMatrix[2] * arcEye.z;\n"
            +"                precise vec4 arcPW = gl_ProjectionMatrix[3] * arcEye.w;\n"
            +"                precise vec4 arcPXY = arcPX + arcPY;\n"
            +"                precise vec4 arcPXYZ = arcPXY + arcPZ;\n"
            +"                precise vec4 arcClip = arcPXYZ + arcPW;\n"
            +"                gl_Position = arcClip;\n"
            +"            }\n"
            +"        } else { gl_Position = vec4(2.0, 2.0, 2.0, 1.0); }\n"
            +"    }\n";
    private GpuVoxelSkin072EyeShader(){}
    public static String hash(String source){
        try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(source.getBytes(StandardCharsets.UTF_8)));}
        catch(Exception failure){throw new IllegalStateException(failure);}
    }
    public static String patch(String name,String source){
        boolean vertex=name.equals("argentum:entity_instancing.vert"),fragment=name.equals("argentum:entity_instancing.frag");
        if(!vertex&&!fragment)return source;
        if(source==null||!(vertex?VERTEX_SHA:FRAGMENT_SHA).equals(hash(source)))throw new IllegalArgumentException("Unknown native shader source for the eye-position diagnostic");
        String changed=replace(source,"#version 130","#version 430 compatibility");
        if(!vertex)return changed;
        changed=replace(changed,"#version 430 compatibility","#version 430 compatibility"+DECLARATIONS);
        return replace(changed,"gl_Position = gl_ProjectionMatrix * eyePosition;","gl_Position = gl_ProjectionMatrix * eyePosition;"+OVERRIDE);
    }
    public static String undoForSourceAudit(String name,String source){
        if(name.endsWith(".vert"))source=source.replace(DECLARATIONS,"").replace(OVERRIDE,"");
        return source.replace("#version 430 compatibility","#version 130");
    }
    private static String replace(String source,String token,String replacement){
        int at=source.indexOf(token);if(at<0||source.indexOf(token,at+token.length())>=0)throw new IllegalArgumentException("Unknown native eye-position shader anchor");
        return source.substring(0,at)+replacement+source.substring(at+token.length());
    }
}
