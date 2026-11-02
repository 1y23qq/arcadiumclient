// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.optimizations;

import java.util.Random;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RetainedTagPreparationTest {
    @Test void reusedBillboardsKeepEveryMatrixBitWithLivePositionsAndCameraChanges(){
        Random random=new Random(66066);
        RetainedTagTransforms cache=new RetainedTagTransforms();
        Matrix4f camera=new Matrix4f(),local=new Matrix4f(),expected=new Matrix4f(),actual=new Matrix4f();
        for(int view=0;view<160;view++){
            if(view%8==0)camera.identity();
            else camera.identity().rotateXYZ(next(random),next(random),next(random)).scale(.5f+random.nextFloat());
            if(view%7==0)camera.m10(camera.m10()+.13f);
            float yaw=next(random)*180,pitch=next(random)*90;
            for(int label=0;label<16;label++){
                if(label==0)local.identity();
                else local.identity().translate(next(random),next(random),next(random)).rotateXYZ(next(random),next(random),next(random)).scale(-.02666667f,-.02666667f,.02666667f);
                RetainedTagTransforms.Local packet=new RetainedTagTransforms.Local(local);
                for(int frame=0;frame<24;frame++){
                    camera.m30(next(random)*1000).m31(next(random)*1000).m32(next(random)*1000);
                    if(frame==7)yaw=Math.nextUp(yaw);
                    if(frame==11)pitch=Math.nextDown(pitch);
                    if(frame==16)camera.m11(Math.nextUp(camera.m11()));
                    float x=next(random)*128,y=next(random)*128,z=next(random)*128;
                    expected.set(camera).translate(x,y,z).rotateY((float)Math.toRadians(-yaw)).rotateX((float)Math.toRadians(pitch)).mul(local);
                    cache.anchor(camera,x,y,z,yaw,pitch);cache.combine(packet,actual);
                    exact(expected,actual);
                }
            }
        }
        assertTrue(cache.linearReuses>40000);assertTrue(cache.basisUpdates>1000);
    }
    @Test void unknownTransformsAndInvalidationUseOriginalMath(){
        RetainedTagTransforms cache=new RetainedTagTransforms();Matrix4f actual=new Matrix4f();
        Matrix4f[] inputs={new Matrix4f().perspective(1.2f,1.4f,.1f,100),new Matrix4f().rotationY(.4f),new Matrix4f(),new Matrix4f().m03(.2f)};
        for(Matrix4f camera:inputs)for(Matrix4f local:inputs){
            RetainedTagTransforms.Local packet=new RetainedTagTransforms.Local(local);
            for(int i=0;i<4;i++){
                if(i==2)cache.invalidate();
                Matrix4f expected=new Matrix4f(camera).translate(i-.2f,2-i,7+i).rotateY((float)Math.toRadians(-31)).rotateX((float)Math.toRadians(12)).mul(local);
                cache.anchor(camera,i-.2f,2-i,7+i,31,12);cache.combine(packet,actual);exact(expected,actual);
            }
        }
    }
    @Test void signedZeroUsesTheNativeIdentityShortcut(){
        RetainedTagTransforms cache=new RetainedTagTransforms();Matrix4f actual=new Matrix4f(),camera=new Matrix4f(),local=new Matrix4f().scale(.025f);
        RetainedTagTransforms.Local packet=new RetainedTagTransforms.Local(local);
        for(float x:new float[]{-0.0f,0.0f})for(float y:new float[]{-0.0f,0.0f})for(float z:new float[]{-0.0f,0.0f}){
            Matrix4f expected=new Matrix4f(camera).translate(x,y,z).rotateY(-0.0f).rotateX(0.0f).mul(local);
            cache.anchor(camera,x,y,z,0,0);cache.combine(packet,actual);exact(expected,actual);
        }
    }
    private static float next(Random random){return random.nextFloat()*2-1;}
    private static void exact(Matrix4f expected,Matrix4f actual){
        for(int col=0;col<4;col++)for(int row=0;row<3;row++)assertEquals(Float.floatToRawIntBits(expected.get(col,row)),Float.floatToRawIntBits(actual.get(col,row)),"matrix "+col+","+row);
    }
    enum Choice { FIRST,SECOND }
    public static final class PolyColor {
        int value=0x80402010;
        public int getArgb(){return value;}
    }
    static final class Options {
        static boolean rounded,useInterInGame;
        static float paddingX,paddingY;
        static boolean bool=true;
        static byte tiny=1;
        static short small=2;
        static char character='A';
        static int integer=3;
        static long wide=4;
        static float decimal=5;
        static double precise=6;
        static Choice choice=Choice.FIRST;
        static PolyColor color=new PolyColor();
        static Object ignored=new Object();
    }
    @Test void primitiveAndMutableColorOptionsInvalidateImmediatelyWithoutPollingAllocations()throws Throwable {
        Options.rounded=Options.useInterInGame=false;Options.paddingX=Options.paddingY=0;
        RetainedTagOptions options=new RetainedTagOptions(Options.class);
        assertTrue(options.poll());assertFalse(options.poll());assertFalse(options.customBackground());assertFalse(options.customFont());
        Options.rounded=true;assertTrue(options.poll());assertTrue(options.customBackground());
        Options.rounded=false;Options.paddingX=.1f;assertTrue(options.poll());assertTrue(options.customBackground());
        Options.paddingX=-1;assertTrue(options.poll());assertFalse(options.customBackground());
        Options.useInterInGame=true;assertTrue(options.poll());assertTrue(options.customFont());
        Options.color.value++;assertTrue(options.poll());assertFalse(options.poll());
        Options.color=null;assertTrue(options.poll());assertFalse(options.poll());
        Options.color=new PolyColor();assertTrue(options.poll());
        Options.bool=!Options.bool;Options.tiny++;Options.small++;Options.character++;Options.integer++;Options.wide++;Options.decimal=Math.nextUp(Options.decimal);Options.precise=Math.nextUp(Options.precise);Options.choice=Choice.SECOND;
        assertTrue(options.poll());assertFalse(options.poll());
        Options.choice=null;assertTrue(options.poll());assertFalse(options.poll());
        Options.decimal=-0.0f;assertTrue(options.poll());Options.decimal=0.0f;assertTrue(options.poll());
        Options.ignored=new Object();assertFalse(options.poll());
    }
    public static void main(String[] args)throws Throwable {
        RetainedTagPreparationTest test=new RetainedTagPreparationTest();
        test.reusedBillboardsKeepEveryMatrixBitWithLivePositionsAndCameraChanges();
        test.unknownTransformsAndInvalidationUseOriginalMath();
        test.signedZeroUsesTheNativeIdentityShortcut();
        test.primitiveAndMutableColorOptionsInvalidateImmediatelyWithoutPollingAllocations();
        System.out.println("Retained tag preparation: 61,512 exact matrix comparisons and live option checks passed");
    }
}
