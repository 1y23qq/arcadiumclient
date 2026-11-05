// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.optimizations;

public final class PlayerPoseMath {
    public static final int[] BONES={0,1,2,3,4,5,0,5,4,3,2,1};
    private static final float PI=(float)Math.PI;
    private static final float[] SINES=new float[65536];
    static{for(int i=0;i<SINES.length;i++)SINES[i]=(float)Math.sin(i*Math.PI*2/65536);}
    private PlayerPoseMath(){}
    public static float sin(float angle){return SINES[(int)(angle*10430.378f)&65535];}
    public static float cos(float angle){return SINES[(int)(angle*10430.378f+16384f)&65535];}
    public static float[] sineTable(){return SINES.clone();}

    public static void part(float walk,float speed,float age,float yaw,float pitch,float swing,int heldLeft,int heldRight,
                            boolean sneak,boolean riding,boolean bow,int bone,float px,float py,float pz,float[] out){
        part(walk,speed,age,yaw,pitch,swing,heldLeft,heldRight,sneak,riding,bow,bone,px,py,pz,0,out);
    }
    public static void part(float walk,float speed,float age,float yaw,float pitch,float swing,int heldLeft,int heldRight,
                            boolean sneak,boolean riding,boolean bow,int bone,float px,float py,float pz,int options,float[] out){
        float headX=pitch/57.295776f,headY=yaw/57.295776f;
        float rightX=cos(walk*.6662f+PI)*2*speed*.5f,leftX=cos(walk*.6662f)*2*speed*.5f;
        float rightY=0,leftY=0,rightZ=0,leftZ=0;
        if((options&PlayerPoseOptions.WAVY_ARMS)!=0){
            rightX=cos(walk*.6662f+PI)*2*speed;leftX=cos(walk*.6662f)*2*speed;
            rightZ=(cos(walk*.2312f)+1)*speed;leftZ=(cos(walk*.2812f)-1)*speed;
        }
        float rightLegX=cos(walk*.6662f)*1.4f*speed,leftLegX=cos(walk*.6662f+PI)*1.4f*speed;
        float rightLegY=0,leftLegY=0,bodyY=0;
        if(riding){rightX+=-.62831855f;leftX+=-.62831855f;rightLegX=leftLegX=-1.2566371f;rightLegY=.31415927f;leftLegY=-.31415927f;}
        if(heldLeft!=0)leftX=leftX*.5f-.31415927f*heldLeft;
        if(heldRight==1||heldRight==3)rightX=rightX*.5f-.31415927f*heldRight;
        if(heldRight==3&&(options&PlayerPoseOptions.OLD_BLOCKING)==0)rightY=-.5235988f;
        float armX=5,armZ=0;
        if(swing>-9990){
            bodyY=sin((float)Math.sqrt(swing)*PI*2)*.2f;armX=cos(bodyY)*5;armZ=sin(bodyY)*5;
            rightY+=bodyY;leftY+=bodyY;leftX+=bodyY;
            float eased=1-swing;eased*=eased;eased*=eased;eased=1-eased;
            float attack=sin(eased*PI),headMotion=sin(swing*PI)*-(headX-.7f)*.75f;
            rightX=(float)((double)rightX-((double)attack*1.2+(double)headMotion));
            rightY+=bodyY*2;rightZ+=sin(swing*PI)*-.4f;
        }
        if(sneak){rightX+=.4f;leftX+=.4f;}
        float roll=cos(age*.09f)*.05f+.05f,breathe=sin(age*.067f)*.05f;
        rightZ+=roll;leftZ-=roll;rightX+=breathe;leftX-=breathe;
        if(bow){rightY=-.1f+headY;leftY=.5f+headY;rightX=leftX=-1.5707964f+headX;rightZ=roll;leftZ=-roll;rightX+=breathe;leftX-=breathe;}
        float rx=0,ry=0,rz=0;
        switch(bone){
            case 0 -> {rx=headX;ry=headY;py=sneak?1:0;}
            case 1 -> {rx=sneak?.5f:0;ry=bodyY;}
            case 2 -> {rx=rightX;ry=rightY;rz=rightZ;px=-armX;pz=armZ;}
            case 3 -> {rx=leftX;ry=leftY;rz=leftZ;px=armX;pz=-armZ;}
            case 4 -> {rx=rightLegX;ry=rightLegY;py=sneak?9:12;pz=sneak?4:.1f;}
            case 5 -> {rx=leftLegX;ry=leftLegY;py=sneak?9:12;pz=sneak?4:.1f;}
            default -> throw new IllegalArgumentException("Invalid player bone");
        }
        out[0]=px;out[1]=py;out[2]=pz;out[3]=rx;out[4]=ry;out[5]=rz;
    }
}
