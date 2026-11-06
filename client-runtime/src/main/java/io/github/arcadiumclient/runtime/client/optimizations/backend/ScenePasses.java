// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.optimizations.backend;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;
import java.util.Objects;

public final class ScenePasses {
    public static final int PIPELINE_BYTES=48, PASS_BYTES=96, LIMIT=64, UNIFORM_ALIGNMENT=256;
    public static final int DEPTH_TEST=1, DEPTH_WRITE=2, BLEND=4, COLOR=1, DEPTH=2, SCISSOR=1;
    public static final int LOAD=0, CLEAR=1, DISCARD=2;
    public static final int NEVER=0, LESS=1, EQUAL=2, LEQUAL=3, GREATER=4, NOTEQUAL=5, GEQUAL=6, ALWAYS=7;
    public static final int CULL_NONE=0, CULL_FRONT=1, CULL_BACK=2, CCW=0, CW=1;
    public static final int ZERO=0, ONE=1, SRC_COLOR=2, INV_SRC_COLOR=3, DST_COLOR=4, INV_DST_COLOR=5,
            SRC_ALPHA=6, INV_SRC_ALPHA=7, DST_ALPHA=8, INV_DST_ALPHA=9;
    public static final int ADD=0, SUBTRACT=1, REVERSE_SUBTRACT=2, MIN=3, MAX=4;
    private ScenePasses() {}

    public record Pipeline(int flags,int depthCompare,int cull,int winding,int srcRgb,int dstRgb,
                           int srcAlpha,int dstAlpha,int rgbOperation,int alphaOperation,int colorMask) {
        public Pipeline {
            require((flags&~7)==0&&((flags&DEPTH_WRITE)==0||(flags&DEPTH_TEST)!=0),"Invalid depth or pipeline flags");
            require(between(depthCompare,0,7)&&between(cull,0,2)&&between(winding,0,1),"Invalid raster state");
            require(between(srcRgb,0,9)&&between(dstRgb,0,9)&&alphaFactor(srcAlpha)&&alphaFactor(dstAlpha),"Invalid blend factors");
            require(between(rgbOperation,0,4)&&between(alphaOperation,0,4)&&between(colorMask,0,15),"Invalid blend operation or mask");
        }
        public void write(ByteBuffer destination) {
            ByteBuffer out=output(destination,PIPELINE_BYTES);
            out.putInt(flags).putInt(depthCompare).putInt(cull).putInt(winding).putInt(srcRgb).putInt(dstRgb)
                    .putInt(srcAlpha).putInt(dstAlpha).putInt(rgbOperation).putInt(alphaOperation).putInt(colorMask).putInt(0);
        }
    }

    public record Pass(int pipeline,int firstCommand,int commandCount,int uniformOffset,int uniformBytes,
                       int loadActions,int storeActions,int flags,int viewportX,int viewportY,int viewportWidth,int viewportHeight,
                       int scissorX,int scissorY,int scissorWidth,int scissorHeight,
                       float red,float green,float blue,float alpha,float clearDepth,float minDepth,float maxDepth) {
        public Pass {
            require(between(pipeline,1,LIMIT)&&firstCommand>=0&&between(commandCount,0,65_536),"Invalid pass command range");
            require(uniformOffset>=0&&(uniformOffset&255)==0&&between(uniformBytes,4,16_384)&&(uniformBytes&3)==0,"Invalid aligned uniform range");
            require((loadActions&~15)==0&&(loadActions&3)<=DISCARD&&((loadActions>>>2)&3)<=DISCARD
                    &&(storeActions&~3)==0&&(flags&~SCISSOR)==0,"Invalid attachment actions or pass flags");
            require(viewportX>=0&&viewportY>=0&&between(viewportWidth,1,16_384)&&between(viewportHeight,1,16_384),"Invalid viewport");
            require((flags&SCISSOR)==0||scissorX>=0&&scissorY>=0&&between(scissorWidth,1,16_384)&&between(scissorHeight,1,16_384),"Invalid scissor");
            require(unit(red)&&unit(green)&&unit(blue)&&unit(alpha)&&unit(clearDepth)&&unit(minDepth)&&unit(maxDepth)&&minDepth<=maxDepth,"Invalid clear or depth range");
        }
        private void write(ByteBuffer out) {
            out.putInt(pipeline).putInt(firstCommand).putInt(commandCount).putInt(uniformOffset)
                    .putInt(uniformBytes).putInt(loadActions).putInt(storeActions).putInt(flags)
                    .putInt(viewportX).putInt(viewportY).putInt(viewportWidth).putInt(viewportHeight)
                    .putInt(scissorX).putInt(scissorY).putInt(scissorWidth).putInt(scissorHeight)
                    .putFloat(red).putFloat(green).putFloat(blue).putFloat(alpha).putFloat(clearDepth)
                    .putFloat(minDepth).putFloat(maxDepth).putInt(0);
        }
    }

    public static void write(ByteBuffer destination,List<Pass> passes) {
        Objects.requireNonNull(passes);
        require(!passes.isEmpty()&&passes.size()<=LIMIT,"Invalid pass count");
        for(Pass pass:passes)Objects.requireNonNull(pass);
        ByteBuffer out=output(destination,Math.multiplyExact(passes.size(),PASS_BYTES));
        for(Pass pass:passes)pass.write(out);
    }

    public static int validate(ByteBuffer bytes,int count,int commandCount,int width,int height,int uniformBytes,int previousStored) {
        SceneBufferChecks.rgbaBytes(width,height);SceneBufferChecks.direct(bytes);aligned(bytes);
        require(count>=1&&count<=LIMIT&&(long)count*PASS_BYTES<=bytes.remaining()&&between(commandCount,0,65_536),"Invalid pass buffer bounds");
        require(between(uniformBytes,4,16_384)&&(uniformBytes&3)==0&&(previousStored&~3)==0,"Invalid frame uniform range or attachment history");
        ByteBuffer view=bytes.duplicate().order(ByteOrder.LITTLE_ENDIAN);
        int stored=previousStored,cursor=0;
        for(int i=0,base=view.position();i<count;i++,base+=PASS_BYTES) {
            int pipeline=view.getInt(base),first=view.getInt(base+4),commands=view.getInt(base+8),offset=view.getInt(base+12),length=view.getInt(base+16);
            int loads=view.getInt(base+20),stores=view.getInt(base+24),flags=view.getInt(base+28),color=loads&3,depth=(loads>>>2)&3;
            require(between(pipeline,1,LIMIT)&&first==cursor&&commands>=0&&commands<=commandCount-cursor,"Invalid ordered command ownership");
            require(offset>=0&&(offset&255)==0&&length>0&&(length&3)==0&&offset<=uniformBytes&&length<=uniformBytes-offset,"Invalid pass uniform window");
            require((flags&~SCISSOR)==0&&(loads&~15)==0&&color<=DISCARD&&depth<=DISCARD&&(stores&~3)==0&&view.getInt(base+92)==0,"Unknown pass bits");
            rectangle(view,base+32,width,height);
            if((flags&SCISSOR)!=0)rectangle(view,base+48,width,height);
            for(int j=64;j<=88;j+=4)require(unit(view.getFloat(base+j)),"Invalid pass float");
            require(view.getFloat(base+84)<=view.getFloat(base+88),"Invalid depth interval");
            require((color!=LOAD||(stored&COLOR)!=0)&&(depth!=LOAD||(stored&DEPTH)!=0),"Loading undefined attachments");
            stored=((color!=DISCARD?COLOR:0)|(depth!=DISCARD?DEPTH:0))&stores;cursor+=commands;
        }
        require(cursor==commandCount&&(stored&COLOR)!=0,"Commands are unowned or output color is undefined");
        return stored;
    }
    private static void rectangle(ByteBuffer in,int base,int width,int height) {
        int x=in.getInt(base),y=in.getInt(base+4),w=in.getInt(base+8),h=in.getInt(base+12);
        require(x>=0&&y>=0&&w>0&&h>0&&x<=width&&w<=width-x&&y<=height&&h<=height-y,"Rectangle escapes its target");
    }
    private static ByteBuffer output(ByteBuffer bytes,int length) {
        SceneBufferChecks.direct(bytes);aligned(bytes);
        require(!bytes.isReadOnly()&&bytes.remaining()>=length,"Pass destination must be writable and bounded");
        return bytes.duplicate().order(ByteOrder.LITTLE_ENDIAN);
    }
    static void aligned(ByteBuffer bytes) {
        require(bytes.alignmentOffset(bytes.position(),Integer.BYTES)==0,"Native scene records require four-byte alignment");
    }
    private static boolean alphaFactor(int value){return between(value,0,1)||between(value,6,9);}
    private static boolean between(int value,int min,int max){return value>=min&&value<=max;}
    private static boolean unit(float value){return Float.isFinite(value)&&value>=0&&value<=1;}
    private static void require(boolean condition,String message){if(!condition)throw new IllegalArgumentException(message);}
}
