// SPDX-License-Identifier: GPL-3.0-only
layout(std430,binding=3) readonly buffer Players {float pd[];};
layout(std430,binding=4) readonly buffer Templates {float td[];};
layout(std430,binding=5) readonly buffer Sines {float sineTable[];};
float mcSin(float a){precise float index=a*10430.378;return sineTable[int(index)&65535];}
float mcCos(float a){precise float product=a*10430.378;precise float index=product+16384.0;return sineTable[int(index)&65535];}
mat4 playerRoot(uint p){return mat4(vec4(pd[p],pd[p+1],pd[p+2],pd[p+3]),vec4(pd[p+4],pd[p+5],pd[p+6],pd[p+7]),vec4(pd[p+8],pd[p+9],pd[p+10],pd[p+11]),vec4(pd[p+12],pd[p+13],pd[p+14],pd[p+15]));}
mat4 playerPart(uint encoded){
    uint p=(encoded/16u)*32u,part=encoded%16u,t=uint(pd[p+27u])+part*16u;
    uint bones[12]=uint[](0u,1u,2u,3u,4u,5u,0u,5u,4u,3u,2u,1u);uint bone=bones[part];
    float walk=pd[p+16u],speed=pd[p+17u],age=pd[p+18u],yaw=pd[p+19u],pitch=pd[p+20u],swing=pd[p+21u];
    bool sneak=pd[p+22u]!=0.0,bow=pd[p+23u]!=0.0,riding=pd[p+26u]!=0.0;int left=int(pd[p+24u]),right=int(pd[p+25u]),options=int(pd[p+28u]);
    precise float hx=pitch/57.295776,hy=yaw/57.295776;
    precise float rx=mcCos(walk*.6662+3.1415927)*2.0*speed*.5,lx=mcCos(walk*.6662)*2.0*speed*.5;
    float ry=0.0,ly=0.0,rz=0.0,lz=0.0;
    precise float rleg=mcCos(walk*.6662)*1.4*speed,lleg=mcCos(walk*.6662+3.1415927)*1.4*speed;
    float rlegy=0.0,llegy=0.0,bodyy=0.0,armx=5.0,armz=0.0;
    if(riding){rx+=-.62831855;lx+=-.62831855;rleg=-1.2566371;lleg=-1.2566371;rlegy=.31415927;llegy=-.31415927;}
    if(left!=0)lx=lx*.5-.31415927*float(left);
    if(right==1||right==3)rx=rx*.5-.31415927*float(right);if(right==3&&(options&1)==0)ry=-.5235988;
    if(swing> -9990.0){
        precise float root=sqrt(swing);precise float arg=root*3.1415927;arg=arg*2.0;bodyy=mcSin(arg)*.2;
        armx=mcCos(bodyy)*5.0;armz=mcSin(bodyy)*5.0;ry+=bodyy;ly+=bodyy;lx+=bodyy;
        precise float eased=1.0-swing;eased=eased*eased;eased=eased*eased;eased=1.0-eased;
        precise float hit=mcSin(eased*3.1415927);precise float headMotion=mcSin(swing*3.1415927)*-(hx-.7)*.75;
        rx=float(double(rx)-(double(hit)*1.2lf+double(headMotion)));ry+=bodyy*2.0;rz+=mcSin(swing*3.1415927)*-.4;
    }
    if(sneak){rx+=.4;lx+=.4;}
    precise float roll=mcCos(age*.09)*.05+.05,breathe=mcSin(age*.067)*.05;rz+=roll;lz-=roll;rx+=breathe;lx-=breathe;
    if(bow){ry=-.1+hy;ly=.5+hy;rx=-1.5707964+hx;lx=rx;rz=roll;lz=-roll;rx+=breathe;lx-=breathe;}
    vec3 pivot=vec3(td[t],td[t+1u],td[t+2u]),angle=vec3(0.0);
    if(bone==0u){angle=vec3(hx,hy,0.0);pivot.y=sneak?1.0:0.0;}
    else if(bone==1u)angle=vec3(sneak?.5:0.0,bodyy,0.0);
    else if(bone==2u){angle=vec3(rx,ry,rz);pivot.x=-armx;pivot.z=armz;}
    else if(bone==3u){angle=vec3(lx,ly,lz);pivot.x=armx;pivot.z=-armz;}
    else if(bone==4u){angle=vec3(rleg,rlegy,0.0);pivot.y=sneak?9.0:12.0;pivot.z=sneak?4.0:.1;}
    else {angle=vec3(lleg,llegy,0.0);pivot.y=sneak?9.0:12.0;pivot.z=sneak?4.0:.1;}
    vec3 s=sin(angle),c=cos(angle);
    mat4 x=mat4(1.0);x[1].yz=vec2(c.x,s.x);x[2].yz=vec2(-s.x,c.x);
    mat4 y=mat4(1.0);y[0].xz=vec2(c.y,-s.y);y[2].xz=vec2(s.y,c.y);
    mat4 z=mat4(1.0);z[0].xy=vec2(c.z,s.z);z[1].xy=vec2(-s.z,c.z);
    mat4 rootMatrix=playerRoot(p),translation=mat4(1.0);if(sneak&&(options&2)==0)rootMatrix[3]+=rootMatrix[1]*.2;
    translation[3].xyz=pivot*.0625+vec3(td[t+4u],td[t+5u],td[t+6u]);
    mat4 box=mat4(1.0);box[0].x=td[t+12u];box[1].y=td[t+13u];box[2].z=td[t+14u];box[3].xyz=vec3(td[t+8u],td[t+9u],td[t+10u]);
    return rootMatrix*translation*z*y*x*box;
}
