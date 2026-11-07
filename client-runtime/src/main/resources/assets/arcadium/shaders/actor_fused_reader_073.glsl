// SPDX-License-Identifier: GPL-3.0-only
layout(std430,binding=3) readonly buffer ArcActorInputBits {uint actorInputBits[];};
uniform uint arcActorMetadataBase;
float arcActorAngle(float previous,float current,float partial){
    precise float difference=current-previous;
    while(difference< -180.0)difference+=360.0;
    while(difference>=180.0)difference-=360.0;
    precise float step=partial*difference;
    precise float value=previous+step;return value;
}
void arcActorPose(uint p,inout float walk,inout float speed,inout float age,inout float yaw,inout float pitch){
    if(arcActorMetadataBase==0u)return;
    uint meta=arcActorMetadataBase+(p/32u)*4u;
    if(actorInputBits[meta]!=1u)return;
    uint t=actorInputBits[meta+1u];
    precise float partial=pd[meta+2u];
    precise float remaining=1.0-partial;
    precise float walkStep=pd[t+7u]*remaining;
    precise float newWalk=pd[t+6u]-walkStep;
    precise float speedDifference=pd[t+7u]-pd[t+8u];
    precise float speedStep=speedDifference*partial;
    precise float newSpeed=pd[t+8u]+speedStep;
    precise float newAge=pd[t+9u]+partial;
    precise float body=arcActorAngle(pd[t],pd[t+1u],partial);
    precise float head=arcActorAngle(pd[t+2u],pd[t+3u],partial);
    precise float newYaw=head-body;
    precise float pitchDifference=pd[t+5u]-pd[t+4u];
    precise float pitchStep=pitchDifference*partial;
    precise float newPitch=pd[t+4u]+pitchStep;
    walk=newWalk;speed=min(1.0,newSpeed);age=newAge;yaw=newYaw;pitch=newPitch;
    uint descriptor=actorInputBits[meta+3u],mask=descriptor&31u,offset=descriptor>>5u;
    if((mask&1u)!=0u)walk=pd[offset++];
    if((mask&2u)!=0u)speed=pd[offset++];
    if((mask&4u)!=0u)age=pd[offset++];
    if((mask&8u)!=0u)yaw=pd[offset++];
    if((mask&16u)!=0u)pitch=pd[offset++];
}
