#version 430 compatibility
// SPDX-License-Identifier: GPL-3.0-only
uniform sampler2D uDiffuse;
uniform sampler2D uLight;
uniform bool uTextured;
uniform bool uLightmap;
uniform int uFog;
in vec2 vUv;
in vec2 vLight;
in vec4 vColor;
in float vDistance;
void main(){
    vec4 color=vColor;
    if(uTextured)color*=texture(uDiffuse,vUv);
    if(uLightmap)color*=texture(uLight,vLight);
    if(uFog!=0){
        float factor;
        if(uFog==9729)factor=(gl_Fog.end-vDistance)*gl_Fog.scale;
        else if(uFog==2048)factor=exp(-gl_Fog.density*vDistance);
        else {float density=gl_Fog.density*vDistance;factor=exp(-density*density);}
        color.rgb=mix(gl_Fog.color.rgb,color.rgb,clamp(factor,0.0,1.0));
    }
    gl_FragColor=color;
}
