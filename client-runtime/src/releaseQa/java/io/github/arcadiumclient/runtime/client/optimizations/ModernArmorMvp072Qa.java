// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.optimizations;

import io.github.arcadiumclient.runtime.qa.ModernGlint072Qa;
import java.lang.reflect.Field;
import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.client.render.model.ModelPart;
import org.lwjgl.opengl.*;

public final class ModernArmorMvp072Qa {
    private static final Snapshot[] states=new Snapshot[8];
    private static final int[] programs=new int[16],locations=new int[16],uniforms=new int[16];
    private static int depth;
    private static long matrixRestores,nativeMvpChecks,uniformRestores,ownedDraws,controlDraws;
    private static Field nativeViews,arenaField;
    private static final class Snapshot{int mode,stack;final float[] model=new float[16],projection=new float[16];}
    static{for(int i=0;i<states.length;i++)states[i]=new Snapshot();}
    private ModernArmorMvp072Qa(){}
    public static int beforeClient(){
        if(!ModernGlint072Qa.enabled())return 0;require(depth<states.length,"MVP client QA stack budget");Snapshot s=states[depth++];s.mode=GL11.glGetInteger(GL11.GL_MATRIX_MODE);s.stack=GL11.glGetInteger(GL11.GL_MODELVIEW_STACK_DEPTH);GL11.glGetFloatv(GL11.GL_MODELVIEW_MATRIX,s.model);GL11.glGetFloatv(GL11.GL_PROJECTION_MATRIX,s.projection);return depth;
    }
    public static void afterClient(int token,ModelPart part,int index){
        if(token==0)return;require(token==depth,"MVP client QA scope order");Snapshot s=states[--depth];float[] model=new float[16],projection=new float[16];GL11.glGetFloatv(GL11.GL_MODELVIEW_MATRIX,model);GL11.glGetFloatv(GL11.GL_PROJECTION_MATRIX,projection);
        require(GL11.glGetInteger(GL11.GL_MATRIX_MODE)==s.mode&&GL11.glGetInteger(GL11.GL_MODELVIEW_STACK_DEPTH)==s.stack&&same(model,s.model)&&same(projection,s.projection),"Native client MVP construction leaked matrix state");matrixRestores++;
        if(index<0||!ModernGlint072Qa.needsModelViewReference())return;
        try{
            if(nativeViews==null){nativeViews=ModernGlint072Qa.class.getDeclaredField("nativeModelViews");nativeViews.setAccessible(true);arenaField=ModernArmorGlintPipeline.class.getDeclaredField("matrices");arenaField.setAccessible(true);}
            float[] reference=((IdentityHashMap<ModelPart,float[]>)nativeViews.get(null)).get(part);require(reference!=null,"No actual native part matrix for MVP comparison");float[] expected=new float[16];ModernArmorMvpMath.multiply(projection,reference,expected);ModernGlintMatrices table=(ModernGlintMatrices)arenaField.get(null);require(index<table.count(),"MVP row outside actual shared arena");for(int i=0;i<16;i++)require(Float.floatToRawIntBits(expected[i])==Float.floatToRawIntBits(table.values()[index*16+i]),"Native/client MVP raw bits differ at"+i);nativeMvpChecks++;
        }catch(ReflectiveOperationException e){throw new IllegalStateException(e);}
    }
    public static int uniformLocation(int program){return program>0&&GL20.glIsProgram(program)?GL20.glGetUniformLocation(program,"uArcadiumModernArmorMvp"):-1;}
    public static void program(int token,int program,int location,int value){if(token>0){require(token<=programs.length,"MVP uniform scope budget");programs[token-1]=program;locations[token-1]=location;uniforms[token-1]=value;}}
    public static void restored(int token){if(token>0&&locations[token-1]>=0){require(GL20.glGetUniformi(programs[token-1],locations[token-1])==uniforms[token-1],"MVP mode uniform leaked between native programs");uniformRestores++;}}
    public static void actualInstance(int program,int[] words){
        if(!ModernArmorAuxiliary.ownsProgram(program))return;int location=uniformLocation(program);require(location>=0,"Owned MVP control uniform missing");int flag=GL20.glGetUniformi(program,location);
        if(ModernArmorGlintPipeline.mvpMode()==0){require(flag==0,"Private control changed the original vertex path");controlDraws++;return;}
        require(flag==1,"Owned MVP path is not enabled at its actual native draw");int index=words[16]-1;
        try{if(arenaField==null){arenaField=ModernArmorGlintPipeline.class.getDeclaredField("matrices");arenaField.setAccessible(true);}ModernGlintMatrices table=(ModernGlintMatrices)arenaField.get(null);require(index>=0&&index<table.count(),"Actual MVP marker is invalid");int buffer=GL30.glGetIntegeri(GL43.GL_SHADER_STORAGE_BUFFER_BINDING,7);long start=buffer==0?0:GL32.glGetInteger64i(GL43.GL_SHADER_STORAGE_BUFFER_START,7),size=buffer==0?0:GL32.glGetInteger64i(GL43.GL_SHADER_STORAGE_BUFFER_SIZE,7);require(buffer>0,"MVP storage is not bound");int generic=GL11.glGetInteger(GL43.GL_SHADER_STORAGE_BUFFER_BINDING);long store;try{GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER,buffer);store=GL32.glGetBufferParameteri64(GL43.GL_SHADER_STORAGE_BUFFER,GL15.GL_BUFFER_SIZE);}finally{GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER,generic);}long needed=(long)(index+1)*64;require(start>=0&&size>=0&&needed<=store-start&&(size==0?start==0:needed<=size),"MVP matrix range exceeds actual storage");ownedDraws++;
        }catch(ReflectiveOperationException e){throw new IllegalStateException(e);}
    }
    public static Map<String,Object> report(){
        var result=new java.util.LinkedHashMap<String,Object>();result.put("mode",ModernArmorGlintPipeline.mvpMode());result.put("matrixStateRestores",matrixRestores);result.put("actualNativeMvpRawBitChecks",nativeMvpChecks);result.put("uniformScopeRestores",uniformRestores);result.put("actualOwnedMvpInstances",ownedDraws);result.put("mode0ControlInstances",controlDraws);result.put("balancedClientScopes",depth==0);result.put("productionMvpGpuReadbacks",0);result.put("layerRootCaptures",ModernArmorMvp.layerRoots);result.put("clientMatrixConstructions",ModernArmorMvp.clientMatrixConstructions);result.put("cachedPartMvpHits",ModernArmorMvp.cacheHits);return result;
    }
    private static boolean same(float[]a,float[]b){for(int i=0;i<a.length;i++)if(Float.floatToRawIntBits(a[i])!=Float.floatToRawIntBits(b[i]))return false;return true;}
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException(message);}
}
