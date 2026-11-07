package io.github.arcadiumclient.runtime.qa;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

public final class VoxelProjectionShader073 {
    public record Patch(String vertex,String fragment){}
    private static final String VERTEX="97deaa4e64994086417b4aab617f0b1a0b21e3605729e5af13130bd6ffbf40b6",FRAGMENT="0ba35d3485f521d4767603c1200021f0270b17e96aa37bcb3f0d77cd5d65df30";
    private static final String DECLARATIONS="uniform int uArcQaProjectionMode;\nuniform int uArcQaProjectionCount;\nuniform mat4 uArcQaPhysicalMvp[32];\n";
    private static final String DOT="vec4 arcQaProjectivePosition(mat4 m, vec4 p) {\n    precise vec4 result = vec4(0.0);\n    result = fma(m[0], vec4(p.x), result);\n    result = fma(m[1], vec4(p.y), result);\n    result = fma(m[2], vec4(p.z), result);\n    result = fma(m[3], vec4(p.w), result);\n    return result;\n}\n\n";
    private VoxelProjectionShader073(){}
    public static Patch patch(String vertex,String fragment,boolean gl43){
        if(!gl43||vertex==null||fragment==null||!sha(vertex).equals(VERTEX)||!sha(fragment).equals(FRAGMENT))return null;
        String result=once(vertex,"#version 130","#version 430 compatibility\n#extension GL_ARB_shader_draw_parameters : require");result=once(result,"uniform bool uEyeLight;","uniform bool uEyeLight;\n"+DECLARATIONS);result=once(result,"void main() {",DOT+"void main() {");
        result=once(result,"gl_Position = gl_ProjectionMatrix * eyePosition;","gl_Position = gl_ProjectionMatrix * eyePosition;\n    if (uArcQaProjectionMode == 1 && uBoxInstancing == 0) {\n        int row = int(gl_BaseInstanceARB);\n        if (row >= 0 && row < uArcQaProjectionCount)\n            gl_Position = arcQaProjectivePosition(uArcQaPhysicalMvp[row], vec4(aPosition, 1.0));\n    }");
        return new Patch(result,once(fragment,"#version 130","#version 430 compatibility"));
    }
    private static String once(String source,String anchor,String replacement){int first=source.indexOf(anchor);if(first<0||source.indexOf(anchor,first+anchor.length())>=0)throw new IllegalArgumentException("Pinned shader anchor changed");return source.substring(0,first)+replacement+source.substring(first+anchor.length());}
    private static String sha(String source){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(source.getBytes(StandardCharsets.UTF_8)));}catch(java.security.GeneralSecurityException error){throw new IllegalStateException(error);}}
}
