#version 430 compatibility
// SPDX-License-Identifier: GPL-3.0-only
layout(location=0) in vec3 aPosition;
layout(location=1) in vec2 aUv;
layout(location=2) in vec4 aColor;
layout(location=3) in vec3 aMatrix0;
layout(location=4) in vec3 aMatrix1;
layout(location=5) in vec3 aMatrix2;
layout(location=6) in vec3 aMatrix3;
layout(location=7) in vec4 aParameters;
uniform int uRadialFog;
out vec2 vUv;
out vec2 vLight;
out vec4 vColor;
out float vDistance;
void main(){
    precise vec3 eye=aMatrix0*aPosition.x+aMatrix1*aPosition.y+aMatrix2*aPosition.z+aMatrix3;
    gl_Position=gl_ProjectionMatrix*vec4(eye,1.0);
    vUv=(gl_TextureMatrix[0]*vec4(aUv,0.0,1.0)).xy;
    vLight=(gl_TextureMatrix[1]*vec4(aParameters.yz,0.0,1.0)).xy;
    vColor=vec4(aColor.rgb,aParameters.x<0.0?aColor.a:aParameters.x/255.0);
    vDistance=uRadialFog==1?length(eye):(uRadialFog==2?eye.z:abs(eye.z));
}
