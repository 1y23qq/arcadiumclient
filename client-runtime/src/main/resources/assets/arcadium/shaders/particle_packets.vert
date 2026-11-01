#version 330 compatibility
// SPDX-License-Identifier: GPL-3.0-only
layout(location=0) in vec4 aPositionSize;
layout(location=1) in vec4 aUv;
layout(location=2) in uvec2 aPacked;
layout(location=3) in vec4 aBasis;
layout(location=4) in float aSide;
uniform int uRadialFog;
out vec2 vUv;
out vec2 vLight;
out vec4 vColor;
out float vDistance;
out vec3 qaPosition;
const int QUAD[6]=int[6](0,1,2,0,2,3);
void main(){
    int corner=QUAD[gl_VertexID];
    bool right=corner>=2;
    bool top=corner==1||corner==2;
    float size=aPositionSize.w;
    vec3 p=aPositionSize.xyz;
    p.x=right?p.x+aBasis.x*size:p.x-aBasis.x*size;
    p.z=right?p.z+aBasis.z*size:p.z-aBasis.z*size;
    p.x=top?p.x+aBasis.w*size:p.x-aBasis.w*size;
    p.y=top?p.y+aBasis.y*size:p.y-aBasis.y*size;
    p.z=top?p.z+aSide*size:p.z-aSide*size;
    qaPosition=p;
    vec4 eye=gl_ModelViewMatrix*vec4(p,1.0);
    gl_Position=gl_ProjectionMatrix*eye;
    vUv=(gl_TextureMatrix[0]*vec4(right?aUv.x:aUv.y,top?aUv.z:aUv.w,0.0,1.0)).xy;
    vLight=(gl_TextureMatrix[1]*vec4(float(int(aPacked.y<<16u)>>16),float(int(aPacked.y)>>16),0.0,1.0)).xy;
    vColor=vec4(aPacked.x&255u,(aPacked.x>>8u)&255u,(aPacked.x>>16u)&255u,aPacked.x>>24u)/255.0;
    vDistance=uRadialFog==1?length(eye.xyz):(uRadialFog==2?eye.z:abs(eye.z));
}
