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
    private static Field nativeViews,arenaField,nativeRoots;
    private static long nativeEntryChecks,nativeEntryRootMatches,nativeEntryProjectionMatches;
    private static Map<String,Object> firstEntryComparison,firstEntryDifference;
    private static Map<String,Object> firstMismatch;
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
            if(nativeViews==null){nativeViews=ModernGlint072Qa.class.getDeclaredField("nativeModelViews");nativeViews.setAccessible(true);nativeRoots=ModernGlint072Qa.class.getDeclaredField("nativeModelRoots");nativeRoots.setAccessible(true);arenaField=ModernArmorGlintPipeline.class.getDeclaredField("matrices");arenaField.setAccessible(true);}
            float[] reference=((IdentityHashMap<ModelPart,float[]>)nativeViews.get(null)).get(part);require(reference!=null,"No actual native part matrix for MVP comparison");Object entryValue=((IdentityHashMap<?,?>)nativeRoots.get(null)).get(part);require(entryValue instanceof Map,"No actual native part entry matrix for MVP comparison");var entry=(Map<?,?>)entryValue;float[] entryRoot=(float[])entry.get("modelView"),entryProjection=(float[])entry.get("projection");boolean rootMatch=same(entryRoot,s.model),projectionMatch=same(entryProjection,projection);nativeEntryChecks++;if(rootMatch)nativeEntryRootMatches++;if(projectionMatch)nativeEntryProjectionMatches++;if(!rootMatch||!projectionMatch)entryDifference(part,entry,s,projection,index);
            if(firstEntryComparison==null){var sample=new java.util.LinkedHashMap<String,Object>();firstEntryComparison=sample;sample.put("nativeRoot",entryRoot.clone());sample.put("nativeRootBits",bits(entryRoot));sample.put("gpuPhysicalRoot",s.model.clone());sample.put("gpuPhysicalRootBits",bits(s.model));sample.put("nativeMode",entry.get("matrixMode"));sample.put("nativeDepth",entry.get("matrixDepth"));sample.put("gpuMode",s.mode);sample.put("gpuDepth",s.stack);sample.put("rootRaw16Exact",rootMatch);sample.put("projectionRaw16Exact",projectionMatch);sample.put("noAdditionalGlQueries",true);}
            float[] expected=new float[16];ModernArmorMvpMath.multiply(projection,reference,expected);ModernGlintMatrices table=(ModernGlintMatrices)arenaField.get(null);require(index<table.count(),"MVP row outside actual shared arena");for(int i=0;i<16;i++)if(Float.floatToRawIntBits(expected[i])!=Float.floatToRawIntBits(table.values()[index*16+i])){captureMismatch(part,s.model,reference,projection,expected,table,index,i);var failure=new IllegalStateException("Native/client MVP raw bits differ at"+i);ModernGlint072Qa.referenceFailure(failure);throw failure;}nativeMvpChecks++;
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
        var result=new java.util.LinkedHashMap<String,Object>();result.put("mode",ModernArmorGlintPipeline.mvpMode());result.put("matrixStateRestores",matrixRestores);result.put("actualNativeMvpRawBitChecks",nativeMvpChecks);result.put("uniformScopeRestores",uniformRestores);result.put("actualOwnedMvpInstances",ownedDraws);result.put("mode0ControlInstances",controlDraws);result.put("balancedClientScopes",depth==0);result.put("productionMvpGpuReadbacks",0);result.put("layerRootCaptures",ModernArmorMvp.layerRoots);result.put("clientMatrixConstructions",ModernArmorMvp.clientMatrixConstructions);result.put("cachedPartMvpHits",ModernArmorMvp.cacheHits);result.put("nativeSneakPreludeRoots",ModernArmorMvp.nativeSneakPreludeRoots);result.put("identityPreludeRoots",ModernArmorMvp.identityPreludeRoots);result.put("actualNativeEntryComparisons",nativeEntryChecks);result.put("actualNativeEntryRootRaw16Matches",nativeEntryRootMatches);result.put("actualNativeEntryProjectionRaw16Matches",nativeEntryProjectionMatches);if(firstEntryComparison!=null)result.put("firstNativeEntryComparison",firstEntryComparison);if(firstEntryDifference!=null)result.put("firstNonmatchingNativeEntry",firstEntryDifference);if(firstMismatch!=null)result.put("firstNativeMvpMismatch",firstMismatch);return result;
    }
    private static void entryDifference(ModelPart part,Map<?,?> entry,Snapshot state,float[] projection,int index){
        if(firstEntryDifference!=null)return;
        var record=new java.util.LinkedHashMap<String,Object>();firstEntryDifference=record;
        float[] nativeRoot=(float[])entry.get("modelView"),nativeProjection=(float[])entry.get("projection");
        record.put("nativeRoot",nativeRoot.clone());record.put("nativeRootBits",bits(nativeRoot));record.put("gpuPhysicalRoot",state.model.clone());record.put("gpuPhysicalRootBits",bits(state.model));
        record.put("nativeProjection",nativeProjection.clone());record.put("nativeProjectionBits",bits(nativeProjection));record.put("gpuProjection",projection.clone());record.put("gpuProjectionBits",bits(projection));
        record.put("nativeMode",entry.get("matrixMode"));record.put("nativeDepth",entry.get("matrixDepth"));record.put("gpuMode",state.mode);record.put("gpuDepth",state.stack);record.put("tableRow",index);
        record.put("partIdentity",System.identityHashCode(part));record.put("pivot",new float[]{part.x,part.y,part.z});record.put("translation",new float[]{part.translateX,part.translateY,part.translateZ});record.put("anglesRadians",new float[]{part.rotationX,part.rotationY,part.rotationZ});record.put("visible",part.visible);record.put("invisible",part.invisible);
        Object model=((io.github.arcadiumclient.runtime.mixin.client.access.BetaModelPartAccess)part).arcadium$baseModel();record.put("modelIdentity",System.identityHashCode(model));
        if(model instanceof net.minecraft.client.render.model.entity.HumanoidModel h)record.put("part",part==h.head?"head":part==h.hat?"hat":part==h.body?"body":part==h.rightArm?"rightArm":part==h.leftArm?"leftArm":part==h.rightLeg?"rightLeg":part==h.leftLeg?"leftLeg":"unknown");
        var differences=new java.util.ArrayList<Object>();
        for(int i=0;i<16;i++){
            int a=Float.floatToRawIntBits(nativeRoot[i]),b=Float.floatToRawIntBits(state.model[i]);if(a==b)continue;
            var difference=new java.util.LinkedHashMap<String,Object>();difference.put("word",i);difference.put("nativeBits",a);difference.put("gpuBits",b);
            if(Float.isFinite(nativeRoot[i])&&Float.isFinite(state.model[i])){
                boolean signedZero=nativeRoot[i]==0&&state.model[i]==0;long orderedA=a<0?(long)Integer.MIN_VALUE-a:a,orderedB=b<0?(long)Integer.MIN_VALUE-b:b;
                difference.put("classification",signedZero?"signed-zero":"finite-numeric");difference.put("numericDeltaGpuMinusNative",(double)state.model[i]-nativeRoot[i]);difference.put("ulpDistance",Math.abs(orderedB-orderedA));
            }else difference.put("classification","non-finite-no-numeric-difference");
            differences.add(difference);
        }
        record.put("rootDifferences",differences);record.put("rootRawBitsIgnored",false);record.put("noAdditionalGlQueries",true);
        try{
            Field helperField=ModernArmorGlintPipeline.class.getDeclaredField("geometryMvp");helperField.setAccessible(true);Object helper=helperField.get(null);Field rootField=ModernArmorMvp.class.getDeclaredField("root");rootField.setAccessible(true);float[] layerRoot=((float[])rootField.get(helper)).clone();record.put("helperLayerRoot",layerRoot);record.put("helperLayerRootBits",bits(layerRoot));
        }catch(ReflectiveOperationException error){record.put("snapshotFailureClass",error.getClass().getName());}
    }
    private static void captureMismatch(ModelPart part,float[] physicalRoot,float[] nativePart,float[] projection,float[] expected,ModernGlintMatrices table,int index,int word){
        if(firstMismatch!=null)return;
        var record=new java.util.LinkedHashMap<String,Object>();firstMismatch=record;
        record.put("firstDifferingWord",word);record.put("row",index);record.put("tableCount",table.count());
        record.put("actualPhysicalModelRootAtEntry",physicalRoot.clone());record.put("actualNativePartModelView",nativePart.clone());
        record.put("actualProjection",projection.clone());record.put("expectedNativeMvp",expected.clone());record.put("expectedNativeMvpBits",bits(expected));
        float[] generated=java.util.Arrays.copyOfRange(table.values(),index*16,index*16+16);record.put("generatedMvp",generated);record.put("generatedMvpBits",bits(generated));
        record.put("pivot",new float[]{part.x,part.y,part.z});record.put("translation",new float[]{part.translateX,part.translateY,part.translateZ});record.put("anglesRadians",new float[]{part.rotationX,part.rotationY,part.rotationZ});
        try{
            Field rootsField=ModernGlint072Qa.class.getDeclaredField("nativeModelRoots");rootsField.setAccessible(true);
            Object nativeEntry=((IdentityHashMap<?,?>)rootsField.get(null)).get(part);require(nativeEntry instanceof Map,"No same-frame native part entry root");
            var entry=(Map<?,?>)nativeEntry;record.put("actualNativePartEntry",entry);record.put("actualNativePartEntryRootBits",bits((float[])entry.get("modelView")));record.put("actualNativePartEntryProjectionBits",bits((float[])entry.get("projection")));
            Field helperField=ModernArmorGlintPipeline.class.getDeclaredField("geometryMvp");helperField.setAccessible(true);Object helper=helperField.get(null);
            float[] layerRoot=null;
            for(String name:new String[]{"root","projection","eye","mvp"}){Field field=ModernArmorMvp.class.getDeclaredField(name);field.setAccessible(true);float[] value=((float[])field.get(helper)).clone();record.put("helper_"+name,value);record.put("helper_"+name+"Bits",bits(value));if(name.equals("root"))layerRoot=value;}
            record.put("nativeEntryProjectionMatchesCurrentRaw16",same((float[])entry.get("projection"),projection));
            record.put("nativeEntryRootMatchesGpuPhysicalRootRaw16",same((float[])entry.get("modelView"),physicalRoot));
            Object model=((io.github.arcadiumclient.runtime.mixin.client.access.BetaModelPartAccess)part).arcadium$baseModel();
            require(model instanceof net.minecraft.client.render.model.entity.HumanoidModel,"Mismatch model is not native HumanoidModel");
            var humanoid=(net.minecraft.client.render.model.entity.HumanoidModel)model;
            record.put("actualModelClass",model.getClass().getName());record.put("modelSneaking",humanoid.sneaking);record.put("modelBaby",humanoid.isBaby);
            Field actorField=ModernGlint072Qa.class.getDeclaredField("actor");actorField.setAccessible(true);Object value=actorField.get(null);
            require(value instanceof net.minecraft.entity.living.LivingEntity,"Mismatch has no actual owned living actor");
            var actor=(net.minecraft.entity.living.LivingEntity)value;
            record.put("actualActorSneaking",actor.isSneaking());record.put("actualActorBaby",actor.isBaby());
            if(actor.isSneaking()&&!actor.isBaby()&&!humanoid.isBaby)record.put("nativeAdultSneakPreludeOracle",prelude(layerRoot,part,physicalRoot,nativePart,projection,expected));
        }catch(Throwable error){record.put("snapshotFailureClass",error.getClass().getName());}
    }
    private static Map<String,Object> prelude(float[] layerRoot,ModelPart part,float[] physicalRoot,float[] nativePart,float[] projection,float[] expected){
        require(layerRoot!=null,"Missing observed helper layer root");int mode=GL11.glGetInteger(GL11.GL_MATRIX_MODE),stack=GL11.glGetInteger(GL11.GL_MODELVIEW_STACK_DEPTH);
        require(stack<GL11.glGetInteger(GL11.GL_MAX_MODELVIEW_STACK_DEPTH),"No native oracle matrix stack capacity");
        float[] before=new float[16],beforeProjection=new float[16],root=new float[16],eye=new float[16],after=new float[16],afterProjection=new float[16],mvp=new float[16];
        GL11.glGetFloatv(GL11.GL_MODELVIEW_MATRIX,before);GL11.glGetFloatv(GL11.GL_PROJECTION_MATRIX,beforeProjection);
        var buffer=org.lwjgl.BufferUtils.createFloatBuffer(16);boolean pushed=false;
        try{
            GL11.glMatrixMode(GL11.GL_MODELVIEW);GL11.glPushMatrix();pushed=true;buffer.put(layerRoot).flip();GL11.glLoadMatrixf(buffer);
            GL11.glTranslatef(0,.2f,0);GL11.glGetFloatv(GL11.GL_MODELVIEW_MATRIX,root);
            GL11.glTranslatef(part.translateX,part.translateY,part.translateZ);GL11.glTranslatef(part.x*.0625f,part.y*.0625f,part.z*.0625f);
            if(part.rotationZ!=0)GL11.glRotatef(part.rotationZ*57.295776f,0,0,1);
            if(part.rotationY!=0)GL11.glRotatef(part.rotationY*57.295776f,0,1,0);
            if(part.rotationX!=0)GL11.glRotatef(part.rotationX*57.295776f,1,0,0);
            GL11.glGetFloatv(GL11.GL_MODELVIEW_MATRIX,eye);
        }finally{if(pushed)GL11.glPopMatrix();GL11.glMatrixMode(mode);}
        GL11.glGetFloatv(GL11.GL_MODELVIEW_MATRIX,after);GL11.glGetFloatv(GL11.GL_PROJECTION_MATRIX,afterProjection);
        boolean restored=GL11.glGetInteger(GL11.GL_MATRIX_MODE)==mode&&GL11.glGetInteger(GL11.GL_MODELVIEW_STACK_DEPTH)==stack&&same(before,after)&&same(beforeProjection,afterProjection);
        require(restored,"Bounded native sneak oracle leaked matrix state");ModernArmorMvpMath.multiply(projection,eye,mvp);
        var result=new java.util.LinkedHashMap<String,Object>();result.put("actualAdultSneakRequired",true);result.put("operation","native glTranslatef(0,0.2f,0) then actual current part operations");result.put("root",root);result.put("rootBits",bits(root));result.put("partEye",eye);result.put("partEyeBits",bits(eye));result.put("mvp",mvp);result.put("mvpBits",bits(mvp));result.put("rootMatchesActualPhysicalRootRaw16",same(root,physicalRoot));result.put("eyeMatchesActualNativePartRaw16",same(eye,nativePart));result.put("mvpMatchesExpectedNativeRaw16",same(mvp,expected));result.put("matrixModeBefore",mode);result.put("matrixDepthBefore",stack);result.put("exactStateRestored",restored);result.put("qaOnlyMetadataReadback",true);return result;
    }
    private static int[] bits(float[] values){int[] out=new int[values.length];for(int i=0;i<values.length;i++)out[i]=Float.floatToRawIntBits(values[i]);return out;}
    private static boolean same(float[]a,float[]b){for(int i=0;i<a.length;i++)if(Float.floatToRawIntBits(a[i])!=Float.floatToRawIntBits(b[i]))return false;return true;}
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException(message);}
}
