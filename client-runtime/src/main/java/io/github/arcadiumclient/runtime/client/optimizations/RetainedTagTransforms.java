// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.optimizations;

import org.joml.Matrix4f;
import org.joml.Matrix4fc;

public final class RetainedTagTransforms {
    public static final class Local {
        private final Matrix4f source,linear=new Matrix4f();
        private RetainedTagTransforms owner;
        private long epoch;
        public Local(Matrix4fc source){this.source=new Matrix4f(source);}
    }
    private final Matrix4f input=new Matrix4f(),anchor=new Matrix4f(),basis=new Matrix4f();
    private int yaw,pitch,properties;
    private boolean valid;
    private long epoch=1;
    public long basisUpdates,linearUpdates,linearReuses;

    public Matrix4fc anchor(Matrix4fc camera,float x,float y,float z,float yawDegrees,float pitchDegrees){
        int yawBits=Float.floatToRawIntBits(yawDegrees),pitchBits=Float.floatToRawIntBits(pitchDegrees);
        if((camera.properties()&Matrix4fc.PROPERTY_AFFINE)==0){
            valid=false;epoch++;
            return anchor.set(camera).translate(x,y,z).rotateY((float)Math.toRadians(-yawDegrees)).rotateX((float)Math.toRadians(pitchDegrees));
        }
        if(!valid||yaw!=yawBits||pitch!=pitchBits||properties!=camera.properties()||!sameBasis(input,camera)){
            anchor.set(camera).translate(x,y,z).rotateY((float)Math.toRadians(-yawDegrees)).rotateX((float)Math.toRadians(pitchDegrees));
            basis.set(anchor);input.set(camera);yaw=yawBits;pitch=pitchBits;properties=camera.properties();valid=true;epoch++;basisUpdates++;
        }else{
            anchor.set(basis);
            if((camera.properties()&Matrix4fc.PROPERTY_IDENTITY)!=0)anchor.m30(x).m31(y).m32(z);
            else{
                anchor.m30(org.joml.Math.fma(camera.m00(),x,org.joml.Math.fma(camera.m10(),y,org.joml.Math.fma(camera.m20(),z,camera.m30()))));
                anchor.m31(org.joml.Math.fma(camera.m01(),x,org.joml.Math.fma(camera.m11(),y,org.joml.Math.fma(camera.m21(),z,camera.m31()))));
                anchor.m32(org.joml.Math.fma(camera.m02(),x,org.joml.Math.fma(camera.m12(),y,org.joml.Math.fma(camera.m22(),z,camera.m32()))));
            }
        }
        return anchor;
    }
    public Matrix4f combine(Local local,Matrix4f result){
        Matrix4f source=local.source;
        if(!valid||(source.properties()&Matrix4fc.PROPERTY_AFFINE)==0||
                (source.properties()&Matrix4fc.PROPERTY_IDENTITY)!=0||
                (anchor.properties()&(Matrix4fc.PROPERTY_IDENTITY|Matrix4fc.PROPERTY_TRANSLATION))!=0){
            return result.set(anchor).mul(source);
        }
        if(local.owner!=this||local.epoch!=epoch){
            result.set(anchor).mul(source);local.linear.set(result);local.epoch=epoch;local.owner=this;linearUpdates++;return result;
        }
        result.set(local.linear);
        result.m30(org.joml.Math.fma(anchor.m00(),source.m30(),org.joml.Math.fma(anchor.m10(),source.m31(),org.joml.Math.fma(anchor.m20(),source.m32(),anchor.m30()))));
        result.m31(org.joml.Math.fma(anchor.m01(),source.m30(),org.joml.Math.fma(anchor.m11(),source.m31(),org.joml.Math.fma(anchor.m21(),source.m32(),anchor.m31()))));
        result.m32(org.joml.Math.fma(anchor.m02(),source.m30(),org.joml.Math.fma(anchor.m12(),source.m31(),org.joml.Math.fma(anchor.m22(),source.m32(),anchor.m32()))));
        linearReuses++;return result;
    }
    public void invalidate(){valid=false;epoch++;}
    private static boolean sameBasis(Matrix4fc a,Matrix4fc b){
        return same(a.m00(),b.m00())&&same(a.m01(),b.m01())&&same(a.m02(),b.m02())&&same(a.m03(),b.m03())&&
                same(a.m10(),b.m10())&&same(a.m11(),b.m11())&&same(a.m12(),b.m12())&&same(a.m13(),b.m13())&&
                same(a.m20(),b.m20())&&same(a.m21(),b.m21())&&same(a.m22(),b.m22())&&same(a.m23(),b.m23())&&same(a.m33(),b.m33());
    }
    private static boolean same(float a,float b){return Float.floatToRawIntBits(a)==Float.floatToRawIntBits(b);}
}
