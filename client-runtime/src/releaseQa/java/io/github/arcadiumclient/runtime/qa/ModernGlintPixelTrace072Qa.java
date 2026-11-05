// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.qa;

import io.github.arcadiumclient.runtime.mixin.client.access.BetaModelPartAccess;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.render.model.Box;
import net.minecraft.client.render.model.Model;
import net.minecraft.client.render.model.ModelPart;
import net.minecraft.client.render.model.Polygon;
import net.minecraft.client.render.model.entity.HumanoidModel;
import org.joml.Matrix4fc;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.*;

public final class ModernGlintPixelTrace072Qa {
    private static final int MAX_DRAWS = 512, MAX_VERTICES = 256, MAX_CAPTURED = 256;
    private static final int[][] REGIONS = {{635,438,15,3},{647,395,3,1}};
    private static final List<Map<String,Object>> draws = new ArrayList<>(), captures = new ArrayList<>();
    private static final List<Map<String,Object>> shadowRecords = new ArrayList<>();
    private static final IdentityHashMap<Model,Integer> modelIds = new IdentityHashMap<>();
    private static Field boxFaces, polygonFlip;
    private static Object immediateShadowOwner, deferredShadowOwner;
    private ModernGlintPixelTrace072Qa() {}

    public static void captured(ModelPart part, Matrix4fc root, Matrix4fc complete, String pass, int light) {
        if (!ModernGlint072Qa.tracingGpu()) return;
        try {
            require(captures.size()<MAX_CAPTURED,"Captured armor diagnostic exceeded its bounded budget");
            Map<String,Object> entry=part(part);
            entry.put("case",ModernGlint072Qa.traceCase());entry.put("capturePass",pass);entry.put("packedLight",light);
            entry.put("capturedRoot",matrix(root));entry.put("capturedPartMatrix",matrix(complete));
            entry.put("physicalRootModelView",floats(GL11.GL_MODELVIEW_MATRIX,16));
            captures.add(entry);
        } catch(Throwable error) { ModernGlint072Qa.referenceFailure(error); }
    }

    public static int beforeNative(ModelPart part,int list) {
        if (!ModernGlint072Qa.tracingNative()) return 0;
        try {
            Model base=((BetaModelPartAccess)part).arcadium$baseModel();
            if (base.getClass()!=HumanoidModel.class) return 0;
            Map<String,Object> detail=part(part);detail.put("displayList",list);detail.put("nativeBoxFaces",boxFaces(part));
            return before("native-fixed-function",detail);
        } catch(Throwable error) { ModernGlint072Qa.referenceFailure(error);return 0; }
    }

    public static int beforeGpu(Object owner,int mode,int first,int vertices,int instances) {
        if (!ModernGlint072Qa.tracingGpu() && !(deferredShadowOwner!=null&&ModernGlint072Qa.tracingNative())) return 0;
        try {
            int program=GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
            if(program==0)return 0;
            boolean shadow=deferredShadowOwner!=null&&GL20.glGetUniformLocation(program,"uGlintPass")<0&&GL20.glGetAttribLocation(program,"aCorner")>=0;
            if(!shadow&&GL20.glGetUniformLocation(program,"uGlintPass")<0)return 0;
            require(vertices>=0&&vertices<=MAX_VERTICES&&instances>=0&&instances<=MAX_VERTICES,"Instanced diagnostic geometry exceeds its bounded fixture");
            Map<String,Object> detail=new LinkedHashMap<>();
            detail.put("ownerClass",owner.getClass().getName());detail.put("ownerIdentity",System.identityHashCode(owner));
            detail.put("mode",mode);detail.put("first",first);detail.put("vertices",vertices);detail.put("instances",instances);
            Map<String,Object> attributes=new LinkedHashMap<>();
            for(String name:shadow?List.of("aCorner","aBounds0","aBounds1","aShadow"):List.of("aPosition","aNormal","aTexCoord","aVertexColor","aModel0","aModel1","aModel2","aModel3","aLightCoord","aColor","aEffectTime","aOverlay"))
                attributes.put(name,attribute(program,name,first,vertices,instances));
            detail.put("attributes",attributes);
            if(shadow)detail.put("shadowBatchIdentity",System.identityHashCode(deferredShadowOwner));
            else detail.put("resolvedInstances",instanceWords(program,instances));
            return before(shadow?"deferred-instanced-shadow-draw":"instanced-shader",detail);
        } catch(Throwable error) { ModernGlint072Qa.referenceFailure(error);return 0; }
    }

    public static int beginImmediateShadow(Object owner,Object entity,double x,double y,double z,float first,float second) {
        if(!ModernGlint072Qa.tracingNative()&&!ModernGlint072Qa.tracingGpu()||!ModernGlint072Qa.ownsActor(entity))return 0;
        try{require(immediateShadowOwner==null,"Nested immediate shadow diagnostic scope");
            int token=before("immediate-shadow-entry",Map.of("renderer",owner.getClass().getName(),"ownedActor",true,"arguments",new double[]{x,y,z,first,second}));
            immediateShadowOwner=owner;return token;
        }catch(Throwable error){ModernGlint072Qa.referenceFailure(error);return 0;}
    }
    public static void endImmediateShadow(int token){if(token!=0){try{after(token);}finally{immediateShadowOwner=null;}}}
    public static int immediateShadowDraw(){
        if(immediateShadowOwner==null)return 0;
        try{return before("immediate-shadow-tesselator-draw",Map.of("renderer",immediateShadowOwner.getClass().getName(),"ownedActor",true));}
        catch(Throwable error){ModernGlint072Qa.referenceFailure(error);return 0;}
    }
    public static int beginShadowBatch(Object owner){
        if(!ModernGlint072Qa.tracingNative()&&!ModernGlint072Qa.tracingGpu())return 0;
        try{require(deferredShadowOwner==null,"Nested deferred shadow diagnostic scope");
            int token=before("deferred-shadow-flush-entry",Map.of("ownerClass",owner.getClass().getName(),"shadowBatchIdentity",System.identityHashCode(owner)));
            deferredShadowOwner=owner;return token;
        }catch(Throwable error){ModernGlint072Qa.referenceFailure(error);return 0;}
    }
    public static void endShadowBatch(int token){if(token!=0){try{after(token);}finally{deferredShadowOwner=null;}}}
    public static void shadowRecord(Object owner,Object world,Object entity,double x,double y,double z,float first,float second,float third,boolean accepted){
        if(!ModernGlint072Qa.tracingNative()&&!ModernGlint072Qa.tracingGpu()||!ModernGlint072Qa.ownsActor(entity))return;
        try{require(shadowRecords.size()<64,"Shadow diagnostic exceeded its bounded queue budget");
            shadowRecords.add(Map.of("case",ModernGlint072Qa.traceCase(),"variant",ModernGlint072Qa.tracingNative()?"native":"gpu","shadowBatchIdentity",System.identityHashCode(owner),
                    "ownedActor",true,"currentClientWorld",world==Minecraft.getInstance().world,"arguments",new double[]{x,y,z,first,second,third},"acceptedByNativeQueue",accepted));
        }catch(Throwable error){ModernGlint072Qa.referenceFailure(error);}
    }

    private static Map<String,Object> part(ModelPart part) {
        Model base=((BetaModelPartAccess)part).arcadium$baseModel();
        HumanoidModel model=(HumanoidModel)base;
        String name=part==model.head?"head":part==model.hat?"hat":part==model.body?"body":part==model.rightArm?"rightArm":
                part==model.leftArm?"leftArm":part==model.rightLeg?"rightLeg":part==model.leftLeg?"leftLeg":"other";
        Map<String,Object> result=new LinkedHashMap<>();
        result.put("part",name);result.put("model",modelIds.computeIfAbsent(base,key->modelIds.size()+1));
        result.put("pivot",new float[]{part.x,part.y,part.z});result.put("translation",new float[]{part.translateX,part.translateY,part.translateZ});
        result.put("anglesRadians",new float[]{part.rotationX,part.rotationY,part.rotationZ});
        result.put("nativeRotateDegrees",new float[]{part.rotationX*57.295776f,part.rotationY*57.295776f,part.rotationZ*57.295776f});
        return result;
    }

    private static List<Object> boxFaces(ModelPart part)throws ReflectiveOperationException {
        if(boxFaces==null)for(Field field:Box.class.getDeclaredFields())if(field.getType()==Polygon[].class){field.setAccessible(true);boxFaces=field;}
        if(polygonFlip==null)for(Field field:Polygon.class.getDeclaredFields())if(field.getType()==boolean.class){field.setAccessible(true);polygonFlip=field;}
        require(boxFaces!=null&&polygonFlip!=null,"Pinned native box face ABI unavailable");
        List<Object> result=new ArrayList<>();
        require(part.boxes.size()==1,"Native diagnostic armor no longer has exactly one box");
        Polygon[] polygons=(Polygon[])boxFaces.get(part.boxes.get(0));
        require(polygons.length==6,"Native diagnostic armor no longer has six faces");
        for(Polygon polygon:polygons){
            List<Object> vertices=new ArrayList<>();
            require(polygon.vertices.length==4,"Native diagnostic armor face no longer has four vertices");
            for(var vertex:polygon.vertices)vertices.add(new double[]{vertex.pos.x*.0625f,vertex.pos.y*.0625f,vertex.pos.z*.0625f,vertex.u,vertex.v});
            result.add(Map.of("verticesPositionUv",vertices,"flipNormal",polygonFlip.getBoolean(polygon)));
        }
        return result;
    }

    private static int before(String route,Map<String,Object> geometry) {
        require(draws.size()<MAX_DRAWS,"Armor draw diagnostic exceeded its bounded budget");
        Map<String,Object> entry=new LinkedHashMap<>();
        entry.put("case",ModernGlint072Qa.traceCase());entry.put("variant",ModernGlint072Qa.tracingNative()?"native":"gpu");entry.put("route",route);entry.put("glintScope",ModernGlint072Qa.tracingGlint());entry.put("geometry",geometry);
        entry.put("regions",REGIONS);entry.put("before",sample());
        entry.put("state",state());draws.add(entry);return draws.size();
    }

    private static Map<String,Object> state() {
        Map<String,Object> result=new LinkedHashMap<>();int program=GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        result.put("program",program);result.put("projection",floats(GL11.GL_PROJECTION_MATRIX,16));result.put("modelView",floats(GL11.GL_MODELVIEW_MATRIX,16));
        float[] normal=new float[9];new org.joml.Matrix3f(new org.joml.Matrix4f().set(floats(GL11.GL_MODELVIEW_MATRIX,16))).invert().transpose().get(normal);
        result.put("derivedModelViewNormalMatrix",normal);result.put("viewport",ints(GL11.GL_VIEWPORT,4));
        double[] range=new double[2];GL11.glGetDoublev(GL11.GL_DEPTH_RANGE,range);result.put("depthRange",range);
        result.put("depthFunction",GL11.glGetInteger(GL11.GL_DEPTH_FUNC));result.put("depthWrite",GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK));
        result.put("cull",GL11.glIsEnabled(GL11.GL_CULL_FACE));result.put("frontFace",GL11.glGetInteger(GL11.GL_FRONT_FACE));result.put("cullFace",GL11.glGetInteger(GL11.GL_CULL_FACE_MODE));
        result.put("polygonOffsetEnabled",GL11.glIsEnabled(GL11.GL_POLYGON_OFFSET_FILL));result.put("polygonOffsetFactor",GL11.glGetFloat(GL11.GL_POLYGON_OFFSET_FACTOR));result.put("polygonOffsetUnits",GL11.glGetFloat(GL11.GL_POLYGON_OFFSET_UNITS));
        result.put("lighting",GL11.glIsEnabled(GL11.GL_LIGHTING));result.put("normalize",GL11.glIsEnabled(GL11.GL_NORMALIZE));result.put("rescaleNormal",GL11.glIsEnabled(GL12.GL_RESCALE_NORMAL));
        result.put("colorMaterial",GL11.glIsEnabled(GL11.GL_COLOR_MATERIAL));result.put("currentColor",floats(GL11.GL_CURRENT_COLOR,4));result.put("currentNormal",floats(GL11.GL_CURRENT_NORMAL,3));
        result.put("alphaTest",GL11.glIsEnabled(GL11.GL_ALPHA_TEST));result.put("alphaFunction",GL11.glGetInteger(GL11.GL_ALPHA_TEST_FUNC));result.put("alphaReference",GL11.glGetFloat(GL11.GL_ALPHA_TEST_REF));
        result.put("blend",GL11.glIsEnabled(GL11.GL_BLEND));result.put("blendSource",GL11.glGetInteger(GL11.GL_BLEND_SRC));result.put("blendDestination",GL11.glGetInteger(GL11.GL_BLEND_DST));
        Map<String,Object> lights=new LinkedHashMap<>();
        for(int i=0;i<2;i++){Map<String,Object> light=new LinkedHashMap<>();int id=GL11.GL_LIGHT0+i;light.put("enabled",GL11.glIsEnabled(id));
            for(int property:new int[]{GL11.GL_POSITION,GL11.GL_DIFFUSE,GL11.GL_AMBIENT}){float[] data=new float[4];GL11.glGetLightfv(id,property,data);light.put(Integer.toString(property),data);}lights.put(Integer.toString(i),light);}
        result.put("fixedLights",lights);
        Map<String,Object> uniforms=new LinkedHashMap<>();
        if(program!=0){
            for(String name:List.of("uGlintPass","uAlphaPass","uItemGlintPass","uBoxInstancing","uLit","uLightmapped","uEyeLight","uTextureArrayEnabled")){
                int location=GL20.glGetUniformLocation(program,name);if(location>=0)uniforms.put(name,GL20.glGetUniformi(program,location));}
            for(String name:List.of("uLightDirection0","uLightDirection1")){int location=GL20.glGetUniformLocation(program,name);if(location>=0){float[] value=new float[3];GL20.glGetUniformfv(program,location,value);uniforms.put(name,value);}}
        }
        result.put("uniforms",uniforms);result.put("textures",textures(program));return result;
    }

    private static Map<String,Object> textures(int program) {
        int active=GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);Map<String,Object> result=new LinkedHashMap<>();
        try{
            for(String name:List.of("fixed0","fixed1","uTexture","uLightmap","uTextureArray")){
                int unit=name.equals("fixed0")?0:name.equals("fixed1")?1:-1;
                if(unit<0&&program!=0){int location=GL20.glGetUniformLocation(program,name);if(location>=0)unit=GL20.glGetUniformi(program,location);}
                if(unit<0)continue;require(unit<32,"Diagnostic sampler unit outside the bounded fixture");GL13.glActiveTexture(GL13.GL_TEXTURE0+unit);
                Map<String,Object> state=new LinkedHashMap<>();state.put("unit",unit);state.put("enabled2d",GL11.glIsEnabled(GL11.GL_TEXTURE_2D));state.put("matrix",floats(GL11.GL_TEXTURE_MATRIX,16));
                state.put("sampler",GL11.glGetInteger(GL33.GL_SAMPLER_BINDING));
                for(int target:new int[]{GL11.GL_TEXTURE_2D,GL30.GL_TEXTURE_2D_ARRAY}){
                    int binding=GL11.glGetInteger(target==GL11.GL_TEXTURE_2D?GL11.GL_TEXTURE_BINDING_2D:GL30.GL_TEXTURE_BINDING_2D_ARRAY);
                    Map<String,Object> texture=new LinkedHashMap<>();texture.put("binding",binding);
                    if(binding!=0)for(int property:new int[]{GL11.GL_TEXTURE_MIN_FILTER,GL11.GL_TEXTURE_MAG_FILTER,GL11.GL_TEXTURE_WRAP_S,GL11.GL_TEXTURE_WRAP_T})texture.put(Integer.toString(property),GL11.glGetTexParameteri(target,property));
                    state.put(target==GL11.GL_TEXTURE_2D?"2d":"array",texture);
                }
                result.put(name,state);
            }
        }finally{GL13.glActiveTexture(active);}return result;
    }

    private static Map<String,Object> attribute(int program,String name,int first,int vertices,int instances) {
        int index=GL20.glGetAttribLocation(program,name);if(index<0)return Map.of("location",index);
        int size=GL20.glGetVertexAttribi(index,GL20.GL_VERTEX_ATTRIB_ARRAY_SIZE),type=GL20.glGetVertexAttribi(index,GL20.GL_VERTEX_ATTRIB_ARRAY_TYPE),stride=GL20.glGetVertexAttribi(index,GL20.GL_VERTEX_ATTRIB_ARRAY_STRIDE);
        int buffer=GL20.glGetVertexAttribi(index,GL20.GL_VERTEX_ATTRIB_ARRAY_BUFFER_BINDING),divisor=GL20.glGetVertexAttribi(index,GL33.GL_VERTEX_ATTRIB_ARRAY_DIVISOR);
        boolean normalized=GL20.glGetVertexAttribi(index,GL20.GL_VERTEX_ATTRIB_ARRAY_NORMALIZED)!=0;long pointer=GL20.glGetVertexAttribPointer(index,GL20.GL_VERTEX_ATTRIB_ARRAY_POINTER);
        Map<String,Object> result=new LinkedHashMap<>();result.put("location",index);result.put("buffer",buffer);result.put("components",size);result.put("type",type);result.put("stride",stride);result.put("pointer",pointer);result.put("divisor",divisor);result.put("normalized",normalized);result.put("enabled",GL20.glGetVertexAttribi(index,GL20.GL_VERTEX_ATTRIB_ARRAY_ENABLED)!=0);
        int width=width(type);if(buffer==0||width==0)return result;
        require(size>0&&size<=4,"Diagnostic attribute component layout changed");
        int count=divisor==0?vertices:instances,start=divisor==0?first:0;int step=stride==0?size*width:stride;
        if(count==0){result.put("values",List.of());return result;}
        long end=pointer+(long)(start+Math.max(0,count-1))*step+(long)size*width;
        require(pointer>=0&&end<=1024*1024,"Diagnostic attribute address exceeded its bounded store");
        ByteBuffer bytes=readBuffer(buffer,(int)end);List<Object> rows=new ArrayList<>();
        for(int row=0;row<count;row++){float[] values=new float[size];int at=(int)pointer+(start+row)*step;for(int component=0;component<size;component++)values[component]=component(bytes,at+component*width,type,normalized);rows.add(values);}
        result.put("values",rows);return result;
    }

    private static List<Object> instanceWords(int program,int instances) {
        int attribute=GL20.glGetAttribLocation(program,"aModel0");if(attribute<0)return List.of();
        int buffer=GL20.glGetVertexAttribi(attribute,GL20.GL_VERTEX_ATTRIB_ARRAY_BUFFER_BINDING),stride=GL20.glGetVertexAttribi(attribute,GL20.GL_VERTEX_ATTRIB_ARRAY_STRIDE);
        long pointer=GL20.glGetVertexAttribPointer(attribute,GL20.GL_VERTEX_ATTRIB_ARRAY_POINTER);
        require(buffer>0&&stride==76&&pointer==0,"Pinned nineteen-word model instance ABI changed");
        ByteBuffer bytes=readBuffer(buffer,instances*76);List<Object> result=new ArrayList<>();
        for(int instance=0;instance<instances;instance++){
            int[] words=new int[19];for(int i=0;i<words.length;i++)words[i]=bytes.getInt(instance*76+i*4);
            float[] matrix=new float[16];matrix[15]=1;for(int col=0;col<4;col++)for(int row=0;row<3;row++)matrix[col*4+row]=Float.intBitsToFloat(words[col*3+row]);
            int glint=GL20.glGetUniformi(program,GL20.glGetUniformLocation(program,"uGlintPass"));
            Map<String,Object> item=new LinkedHashMap<>();item.put("words",words);item.put("modelMatrix",matrix);item.put("nearestCapturedArmor",nearestCapture(matrix,glint>=0));result.add(item);
        }return result;
    }

    private static Map<String,Object> nearestCapture(float[] matrix,boolean glint) {
        Map<String,Object> nearest=null;double best=Double.POSITIVE_INFINITY;
        for(Map<String,Object> candidate:captures){if(!ModernGlint072Qa.traceCase().equals(candidate.get("case"))||glint!="GLINT".equals(candidate.get("capturePass")))continue;
            float[] expected=(float[])candidate.get("capturedPartMatrix");double error=0;for(int i=0;i<16;i++)error=Math.max(error,Math.abs(expected[i]-matrix[i]));
            if(error<best){best=error;nearest=candidate;}}
        if(nearest==null)return Map.of("available",false);
        return Map.of("maximumMatrixError",best,"part",nearest.get("part"),"model",nearest.get("model"),"capturePass",nearest.get("capturePass"),"capturedRoot",nearest.get("capturedRoot"),"capturedPartMatrix",nearest.get("capturedPartMatrix"),"physicalRootModelView",nearest.get("physicalRootModelView"));
    }

    private static ByteBuffer readBuffer(int buffer,int bytes) {
        int old=GL11.glGetInteger(GL31.GL_COPY_READ_BUFFER);ByteBuffer data=BufferUtils.createByteBuffer(bytes).order(ByteOrder.nativeOrder());
        try{GL15.glBindBuffer(GL31.GL_COPY_READ_BUFFER,buffer);require(bytes<=GL15.glGetBufferParameteri(GL31.GL_COPY_READ_BUFFER,GL15.GL_BUFFER_SIZE),"Diagnostic read exceeded the actual GPU buffer");GL15.glGetBufferSubData(GL31.GL_COPY_READ_BUFFER,0,data);}
        finally{GL15.glBindBuffer(GL31.GL_COPY_READ_BUFFER,old);}return data;
    }
    private static int width(int type){return switch(type){case GL11.GL_BYTE,GL11.GL_UNSIGNED_BYTE->1;case GL11.GL_SHORT,GL11.GL_UNSIGNED_SHORT->2;case GL11.GL_FLOAT,GL11.GL_INT,GL11.GL_UNSIGNED_INT->4;default->0;};}
    private static float component(ByteBuffer bytes,int at,int type,boolean normalized){return switch(type){
        case GL11.GL_FLOAT->bytes.getFloat(at);case GL11.GL_BYTE->normalized?Math.max(-1,bytes.get(at)/127f):bytes.get(at);
        case GL11.GL_UNSIGNED_BYTE->normalized?(bytes.get(at)&255)/255f:bytes.get(at)&255;
        case GL11.GL_SHORT->normalized?Math.max(-1,bytes.getShort(at)/32767f):bytes.getShort(at);
        case GL11.GL_UNSIGNED_SHORT->normalized?(bytes.getShort(at)&65535)/65535f:bytes.getShort(at)&65535;
        case GL11.GL_INT->normalized?Math.max(-1,bytes.getInt(at)/2147483647f):bytes.getInt(at);
        case GL11.GL_UNSIGNED_INT->normalized?(float)(Integer.toUnsignedLong(bytes.getInt(at))/4294967295d):Integer.toUnsignedLong(bytes.getInt(at));default->Float.NaN;};}

    public static void after(int token) {if(token!=0)try{draws.get(token-1).put("after",sample());}catch(Throwable error){ModernGlint072Qa.referenceFailure(error);}}
    public static Map<String,Object> report(){return Map.of("draws",draws,"capturedArmorParts",captures,"ownedActorShadowQueues",shadowRecords,"purpose","Exact two-region draw attribution; no quality threshold adjustment");}
    private static Map<String,Object> sample() {
        Minecraft mc=Minecraft.getInstance();require(mc.width==1280&&mc.height==720,"Fixed diagnostic regions require the exact1280x720 fixture");
        int oldRead=GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);Map<String,Object> result=new LinkedHashMap<>();
        try{
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING));
            for(int i=0;i<REGIONS.length;i++){int[] region=REGIONS[i];int count=region[2]*region[3];var colors=BufferUtils.createByteBuffer(count*4);var depths=BufferUtils.createFloatBuffer(count);
                GL11.glReadPixels(region[0],mc.height-region[1]-region[3],region[2],region[3],GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,colors);
                GL11.glReadPixels(region[0],mc.height-region[1]-region[3],region[2],region[3],GL11.GL_DEPTH_COMPONENT,GL11.GL_FLOAT,depths);
                int[] rgba=new int[count*4];float[] depth=new float[count];for(int n=0;n<rgba.length;n++)rgba[n]=colors.get(n)&255;for(int n=0;n<depth.length;n++)depth[n]=depths.get(n);
                result.put(i==0?"baseFootEdge":"glintArmEdge",Map.of("regionTopLeft",region,"rowOrder","OpenGL bottom-to-top","rgba",rgba,"depth",depth));
            }
        }finally{GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,oldRead);}return result;
    }
    private static float[] matrix(Matrix4fc matrix){float[] result=new float[16];for(int col=0;col<4;col++)for(int row=0;row<4;row++)result[col*4+row]=matrix.get(col,row);return result;}
    private static float[] floats(int property,int count){float[] result=new float[count];GL11.glGetFloatv(property,result);return result;}
    private static int[] ints(int property,int count){int[] result=new int[count];GL11.glGetIntegerv(property,result);return result;}
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException(message);}
}
