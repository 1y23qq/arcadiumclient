// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.qa;

import java.lang.reflect.Field;
import java.nio.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import net.minecraft.client.Minecraft;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.*;

public final class PlayerFeatures073VoxelStageEvidence {
    private static final Map<Integer,String> SOURCE_PINS=Map.of(GL20.GL_VERTEX_SHADER,"97deaa4e64994086417b4aab617f0b1a0b21e3605729e5af13130bd6ffbf40b6",GL20.GL_FRAGMENT_SHADER,"0ba35d3485f521d4767603c1200021f0270b17e96aa37bcb3f0d77cd5d65df30");
    private static final Map<String,Map<String,Object>> sources=new LinkedHashMap<>();
    private static final List<Map<String,Object>> events=new ArrayList<>(),queues=new ArrayList<>();
    private static final int[][] POINTS={{774,350},{783,463}};
    private static String mode="off",failure;
    private static int programSnapshots;
    private static Map<String,Object> rejectedAttribute;
    private static Object deferred,immediate;
    private static Field actorField;
    private PlayerFeatures073VoxelStageEvidence(){}
    public static void begin(String value){if(!mode.equals("off")||deferred!=null||immediate!=null)throw new IllegalStateException("Nested voxel stage collector");mode=value;failure=null;programSnapshots=0;rejectedAttribute=null;events.clear();queues.clear();}
    public static Map<String,Object> end(){
        Map<String,Object> result=new LinkedHashMap<>();result.put("mode",mode);result.put("shadowEvents",new ArrayList<>(events));result.put("acceptedShadowQueues",new ArrayList<>(queues));result.put("sources",new ArrayList<>(sources.values()));
        if(deferred!=null||immediate!=null)fail("scope-leak",new IllegalStateException());
        try{validateShadows(events,queues);}catch(Throwable error){fail("shadow-positivity",error);}
        if((mode.equals("optimized")||mode.equals("voxel-affine"))&&(programSnapshots==0||sources.size()!=2))fail("missing-exact-graphics-inputs",new IllegalStateException());
        try{result.put("finalSamples",samples());}catch(Throwable error){fail("final-samples",error);}
        result.put("programSnapshotsThisRender",programSnapshots);if(rejectedAttribute!=null)result.put("rejectedAttribute",rejectedAttribute);result.put("completedActualShadowEvents",events.stream().filter(event->event.containsKey("before")&&event.containsKey("after")&&Boolean.TRUE.equals(event.get("originalReturnedNormally"))).count());result.put("failure",failure);result.put("programRelinkOrRenderMutation",false);mode="off";return result;
    }
    public static void source(int type,String text)throws Exception{
        if(mode.equals("off"))return;
        if(text.length()>16384||!SOURCE_PINS.containsKey(type))throw new IllegalStateException("Unexpected bounded graphics source");
        byte[] bytes=text.getBytes(java.nio.charset.StandardCharsets.UTF_8);String pin=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        if(!SOURCE_PINS.get(type).equals(pin)&&!PlayerFeatures073VoxelProjectionControl.sourceAllowed(type,text)&&!P3PhysicalOrdinary073Evidence.sourceAllowed(type,text))throw new IllegalStateException("Actual graphics source differs from the frozen oracle");
        if(sources.containsKey(pin))return;
        Path output=Path.of(System.getProperty("arcadium.playerFeatures073.output")).toAbsolutePath().resolveSibling("voxel-graphics-source-"+pin+".glsl");
        if(Files.exists(output)){if(!Arrays.equals(Files.readAllBytes(output),bytes))throw new IllegalStateException("Existing graphics source evidence changed");}else Files.write(output,bytes,StandardOpenOption.CREATE_NEW);
        sources.put(pin,Map.of("type",type,"sha256",pin,"characters",text.length(),"path",output.toString(),"actualAttachedShaderText",true));
    }
    public static Map<String,Object> program(int program)throws Exception{
        if(mode.equals("off"))return Map.of();
        int uniformCount=GL20.glGetProgrami(program,GL20.GL_ACTIVE_UNIFORMS),attributeCount=GL20.glGetProgrami(program,GL20.GL_ACTIVE_ATTRIBUTES);
        if(uniformCount<0||uniformCount>128||attributeCount<0||attributeCount>32||GL20.glGetProgrami(program,GL31.GL_ACTIVE_UNIFORM_BLOCKS)!=0||(GL43.glGetProgramInterfacei(program,GL43.GL_SHADER_STORAGE_BLOCK,GL43.GL_ACTIVE_RESOURCES)!=0&&!P3PhysicalOrdinary073Evidence.ownedProgram(program)))throw new IllegalStateException("Unknown graphics interface requires a separate collector");
        List<Object> uniforms=new ArrayList<>(),attributes=new ArrayList<>(),drawParameters=new ArrayList<>();
        for(int i=0;i<uniformCount;i++){
            IntBuffer size=BufferUtils.createIntBuffer(1),type=BufferUtils.createIntBuffer(1);String name=GL20.glGetActiveUniform(program,i,size,type);int count=size.get(0),kind=type.get(0),components=components(kind);boolean integer=integer(kind);
            if(name.length()>128||count<1||count>32)throw new IllegalStateException("Uniform evidence exceeds bounded layout");
            List<Object> elements=new ArrayList<>();
            for(int element=0;element<count;element++){
                String indexed=count==1?name:name.endsWith("[0]")?name.substring(0,name.length()-3)+"["+element+"]":name+"["+element+"]";int location=GL20.glGetUniformLocation(program,indexed);
                if(location<0){if(!name.startsWith("gl_"))throw new IllegalStateException("Active non-builtin uniform is not addressable");elements.add(Map.of("location",location,"builtinFixedFunctionState",true));continue;}
                if(integer){IntBuffer data=BufferUtils.createIntBuffer(components);if(unsigned(kind))GL30.glGetUniformuiv(program,location,data);else GL20.glGetUniformiv(program,location,data);int[] words=new int[components];data.get(words);elements.add(Map.of("location",location,"ints",words,"unsigned",unsigned(kind)));}
                else{FloatBuffer data=BufferUtils.createFloatBuffer(components);GL20.glGetUniformfv(program,location,data);float[] values=new float[components];data.get(values);elements.add(Map.of("location",location,"floats",values,"rawBits",bits(values)));}
            }
            uniforms.add(Map.of("name",name,"type",kind,"arraySize",count,"elements",elements));
        }
        Set<Integer> consumed=new HashSet<>();Map<String,Integer> buffers=new LinkedHashMap<>();int maximum=GL11.glGetInteger(GL20.GL_MAX_VERTEX_ATTRIBS);
        for(int i=0;i<attributeCount;i++){
            IntBuffer size=BufferUtils.createIntBuffer(1),type=BufferUtils.createIntBuffer(1);String name=GL20.glGetActiveAttrib(program,i,size,type);int base=GL20.glGetAttribLocation(program,name),kind=type.get(0);
            Map<String,Object> system=P3PhysicalOrdinary073Evidence.rowAttribute(program,name,kind,size.get(0),base);if(system==null)system=PlayerFeatures073VoxelProjectionControl.drawParameterInput(program,name,kind,size.get(0),base);if(system!=null){drawParameters.add(system);continue;}
            int[] shape,slots;try{shape=attributeShape(kind);slots=attributeSlots(name,kind,size.get(0),base,maximum,consumed);}catch(IllegalStateException error){if(rejectedAttribute==null)rejectedAttribute=Map.of("name",name.length()<=128?name:name.substring(0,128),"shaderType",kind,"arraySize",size.get(0),"location",base,"program",program,"currentProgram",GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM));throw new IllegalStateException("Attribute layout rejected: "+rejectedAttribute,error);}List<Object> columns=new ArrayList<>();
            for(int slot:slots){
                org.lwjgl.PointerBuffer pointer=BufferUtils.createPointerBuffer(1);GL20.glGetVertexAttribPointerv(slot,GL20.GL_VERTEX_ATTRIB_ARRAY_POINTER,pointer);Map<String,Object> column=new LinkedHashMap<>();column.put("location",slot);column.put("column",slot-base);
                for(var entry:Map.of("enabled",GL20.GL_VERTEX_ATTRIB_ARRAY_ENABLED,"buffer",GL15.GL_VERTEX_ATTRIB_ARRAY_BUFFER_BINDING,"stride",GL20.GL_VERTEX_ATTRIB_ARRAY_STRIDE,"components",GL20.GL_VERTEX_ATTRIB_ARRAY_SIZE,"type",GL20.GL_VERTEX_ATTRIB_ARRAY_TYPE,"normalized",GL20.GL_VERTEX_ATTRIB_ARRAY_NORMALIZED,"integer",GL30.GL_VERTEX_ATTRIB_ARRAY_INTEGER,"divisor",GL33.GL_VERTEX_ATTRIB_ARRAY_DIVISOR).entrySet())column.put(entry.getKey(),GL20.glGetVertexAttribi(slot,entry.getValue()));
                column.put("pointerOffset",pointer.get(0));validateSlot(shape,slot,column);
                if(((Number)column.get("enabled")).intValue()==0){FloatBuffer generic=BufferUtils.createFloatBuffer(4);GL20.glGetVertexAttribfv(slot,GL20.GL_CURRENT_VERTEX_ATTRIB,generic);float[] values=new float[4];generic.get(values);column.put("generic",values);column.put("genericRawBits",bits(values));}
                else{String source=((Number)column.get("stride")).intValue()==36?"vertex-atlas":"instance-payload";int buffer=((Number)column.get("buffer")).intValue();Integer previous=buffers.putIfAbsent(source,buffer);if(previous!=null&&previous!=buffer)throw new IllegalStateException("Multiple unknown buffers feed one copied input layout");column.put("copiedSource",source);}
                columns.add(column);
            }
            attributes.add(Map.of("name",name,"location",base,"shaderType",kind,"columns",shape[0],"rows",shape[1],"integerShaderInput",shape[2]!=0,"columnSlots",columns));
        }
        if(P3PhysicalOrdinary073Evidence.enabled()&&P3PhysicalOrdinary073Evidence.candidate()){if(attributes.size()+drawParameters.size()!=attributeCount||drawParameters.size()<1||drawParameters.size()>2)throw new IllegalStateException("Actual Main physical attribute accounting differs");}else requireAttributeAccounting(attributeCount,attributes.size(),drawParameters.size());Map<String,Object> result=new LinkedHashMap<>();result.put("program",program);result.put("activeUniforms",uniforms);result.put("activeAttributes",attributes);result.put("activeAttributeEntryCount",attributeCount);result.put("activeGenericAttributeEntryCount",attributes.size());result.put("drawParameterInputs",drawParameters);result.put("attributeEntryAccountingComplete",true);result.put("maxVertexAttribs",maximum);result.put("attributeColumnSlotsComplete",true);result.put("copiedSourceBuffers",buffers);result.put("depthFacts",depthFacts());
        result.put("fog",Map.of("enabled",GL11.glIsEnabled(GL11.GL_FOG),"mode",GL11.glGetInteger(GL11.GL_FOG_MODE),"density",GL11.glGetFloat(GL11.GL_FOG_DENSITY),"start",GL11.glGetFloat(GL11.GL_FOG_START),"end",GL11.glGetFloat(GL11.GL_FOG_END),"color",floats(GL11.GL_FOG_COLOR,4)));programSnapshots++;return result;
    }
    private static int[] attributeShape(int type){return switch(type){case GL11.GL_FLOAT->new int[]{1,1,0};case GL20.GL_FLOAT_VEC2->new int[]{1,2,0};case GL20.GL_FLOAT_VEC3->new int[]{1,3,0};case GL20.GL_FLOAT_VEC4->new int[]{1,4,0};case GL20.GL_FLOAT_MAT2->new int[]{2,2,0};case GL20.GL_FLOAT_MAT3->new int[]{3,3,0};case GL20.GL_FLOAT_MAT4->new int[]{4,4,0};case 0x8b65->new int[]{2,3,0};case 0x8b66->new int[]{2,4,0};case 0x8b67->new int[]{3,2,0};case 0x8b68->new int[]{3,4,0};case 0x8b69->new int[]{4,2,0};case 0x8b6a->new int[]{4,3,0};case GL11.GL_INT->new int[]{1,1,1};case GL20.GL_INT_VEC2->new int[]{1,2,1};case GL20.GL_INT_VEC3->new int[]{1,3,1};case GL20.GL_INT_VEC4->new int[]{1,4,1};case GL11.GL_UNSIGNED_INT->new int[]{1,1,2};case GL30.GL_UNSIGNED_INT_VEC2->new int[]{1,2,2};case GL30.GL_UNSIGNED_INT_VEC3->new int[]{1,3,2};case GL30.GL_UNSIGNED_INT_VEC4->new int[]{1,4,2};default->throw new IllegalStateException("Unsupported active attribute shader type");};}
    private static void requireAttributeAccounting(int total,int generic,int system){if(total<0||generic<0||system<0||system>1||generic+system!=total||system==1&&(total!=15||generic!=14))throw new IllegalStateException("Active input accounting is incomplete");}
    private static int[] attributeSlots(String name,int type,int count,int base,int maximum,Set<Integer> consumed){int[] shape=attributeShape(type);if(name.length()>128||name.contains("[")||count!=1||maximum<1||base<0||base>Math.min(maximum,32)-shape[0])throw new IllegalStateException("Attribute location or array layout changed");int[] slots=new int[shape[0]];for(int i=0;i<slots.length;i++){slots[i]=base+i;if(consumed.contains(slots[i]))throw new IllegalStateException("Active attributes overlap consumed column slots");}for(int slot:slots)consumed.add(slot);return slots;}
    private static void validateSlot(int[] shape,int slot,Map<String,Object> data){
        int enabled=((Number)data.get("enabled")).intValue(),integer=((Number)data.get("integer")).intValue();if(enabled!=0&&enabled!=1||integer!=0&&integer!=1)throw new IllegalStateException("Unknown attribute flags");
        if(enabled==0){if(shape[2]!=0||integer!=0||slot==0)throw new IllegalStateException("Disabled integer or position-zero generic attribute requires a separate collector");return;}
        int stride=((Number)data.get("stride")).intValue(),divisor=((Number)data.get("divisor")).intValue(),size=((Number)data.get("components")).intValue(),type=((Number)data.get("type")).intValue(),normalized=((Number)data.get("normalized")).intValue();long offset=((Number)data.get("pointerOffset")).longValue();int bytes=switch(type){case GL11.GL_BYTE,GL11.GL_UNSIGNED_BYTE->1;case GL11.GL_SHORT,GL11.GL_UNSIGNED_SHORT->2;case GL11.GL_INT,GL11.GL_UNSIGNED_INT,GL11.GL_FLOAT->4;default->throw new IllegalStateException("Unknown copied attribute storage type");};
        if(((Number)data.get("buffer")).intValue()<=0||size<1||size>4||normalized<0||normalized>1||offset<0||offset>=stride||size*bytes>stride-offset||offset%bytes!=0||!(stride==36&&divisor==0||stride==76&&divisor==1)||(shape[2]!=0)!=(integer!=0)||integer!=0&&(normalized!=0||type==GL11.GL_FLOAT))throw new IllegalStateException("Attribute slot does not match copied vertex or instance layout");
    }
    private static void validateShadows(List<Map<String,Object>> draws,List<Map<String,Object>> accepted){
        if(draws.isEmpty())throw new IllegalStateException("No actual shadow draw completed");
        for(Map<String,Object> draw:draws){if(!Boolean.TRUE.equals(draw.get("originalReturnedNormally"))||!(draw.get("before") instanceof Map<?,?> before)||!(draw.get("after") instanceof Map<?,?> after)||!(before.get("points") instanceof List<?> a)||!(after.get("points") instanceof List<?> b)||a.size()!=2||b.size()!=2)throw new IllegalStateException("Actual shadow samples are incomplete");
            if(draw.get("kind").equals("actual-deferred-shadow-draw")){Map<?,?> detail=(Map<?,?>)draw.get("detail");boolean matched=accepted.stream().anyMatch(value->Boolean.TRUE.equals(value.get("accepted"))&&value.get("subject") instanceof Number subject&&subject.intValue()>=0&&subject.intValue()<=2&&Objects.equals(value.get("batchIdentity"),detail.get("batchIdentity")));if(!matched||((Number)detail.get("vertices")).intValue()<=0||((Number)detail.get("instances")).intValue()<=0)throw new IllegalStateException("Actual deferred shadow lacks its accepted owned queue");}
            else if(!draw.get("kind").equals("actual-immediate-shadow-tesselator-draw")||!(((Map<?,?>)draw.get("detail")).get("subject") instanceof Number subject)||subject.intValue()<0||subject.intValue()>2)throw new IllegalStateException("Unknown actual shadow draw class or subject");
        }
    }
    private static boolean unsigned(int type){return type==GL11.GL_UNSIGNED_INT||type==GL30.GL_UNSIGNED_INT_VEC2||type==GL30.GL_UNSIGNED_INT_VEC3||type==GL30.GL_UNSIGNED_INT_VEC4;}
    private static boolean integer(int type){return unsigned(type)||switch(type){case GL11.GL_INT,GL20.GL_INT_VEC2,GL20.GL_INT_VEC3,GL20.GL_INT_VEC4,GL20.GL_BOOL,GL20.GL_BOOL_VEC2,GL20.GL_BOOL_VEC3,GL20.GL_BOOL_VEC4,GL20.GL_SAMPLER_2D,GL30.GL_SAMPLER_2D_ARRAY->true;default->false;};}
    private static int components(int type){return switch(type){case GL11.GL_FLOAT,GL11.GL_INT,GL11.GL_UNSIGNED_INT,GL20.GL_BOOL,GL20.GL_SAMPLER_2D,GL30.GL_SAMPLER_2D_ARRAY->1;case GL20.GL_FLOAT_VEC2,GL20.GL_INT_VEC2,GL30.GL_UNSIGNED_INT_VEC2,GL20.GL_BOOL_VEC2->2;case GL20.GL_FLOAT_VEC3,GL20.GL_INT_VEC3,GL30.GL_UNSIGNED_INT_VEC3,GL20.GL_BOOL_VEC3->3;case GL20.GL_FLOAT_VEC4,GL20.GL_INT_VEC4,GL30.GL_UNSIGNED_INT_VEC4,GL20.GL_BOOL_VEC4,GL20.GL_FLOAT_MAT2->4;case GL20.GL_FLOAT_MAT3->9;case GL20.GL_FLOAT_MAT4->16;default->throw new IllegalStateException("Unsupported exact uniform type");};}
    public static Map<String,Object> depthFacts(){
        int draw=GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);Map<String,Object> value=new LinkedHashMap<>();value.put("drawFramebuffer",draw);value.put("readFramebuffer",GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING));value.put("depthBits",GL11.glGetInteger(GL11.GL_DEPTH_BITS));
        if(draw!=0){for(var entry:Map.of("attachmentType",GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE,"attachmentName",GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME,"attachmentComponentType",GL30.GL_FRAMEBUFFER_ATTACHMENT_COMPONENT_TYPE,"attachmentDepthBits",GL30.GL_FRAMEBUFFER_ATTACHMENT_DEPTH_SIZE).entrySet())value.put(entry.getKey(),GL30.glGetFramebufferAttachmentParameteri(GL30.GL_DRAW_FRAMEBUFFER,GL30.GL_DEPTH_ATTACHMENT,entry.getValue()));}return value;
    }
    public static int deferredEnter(Object owner){if(mode.equals("off"))return 0;if(deferred!=null)throw new IllegalStateException("Nested native shadow batch");deferred=owner;return 1;}
    public static void deferredExit(int token){if(token!=0)deferred=null;}
    public static int immediateEnter(Object entity){if(mode.equals("off")||subject(entity)<0)return 0;if(immediate!=null)throw new IllegalStateException("Nested immediate shadow");immediate=entity;return 1;}
    public static void immediateExit(int token){if(token!=0)immediate=null;}
    public static void record(Object owner,Object entity,boolean accepted){if(mode.equals("off")||!accepted||subject(entity)<0)return;if(queues.size()==16)throw new IllegalStateException("Shadow queue evidence budget exceeded");queues.add(Map.of("batchIdentity",System.identityHashCode(owner),"subject",subject(entity),"accepted",true));}
    public static int beforeDeferred(int primitive,int first,int vertices,int instances){
        if(mode.equals("off")||deferred==null)return 0;int program=GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);if(program==0||GL20.glGetAttribLocation(program,"aCorner")<0)return 0;
        return before("actual-deferred-shadow-draw",Map.of("batchIdentity",System.identityHashCode(deferred),"primitive",primitive,"first",first,"vertices",vertices,"instances",instances));
    }
    public static int beforeImmediate(){return immediate==null?0:before("actual-immediate-shadow-tesselator-draw",Map.of("subject",subject(immediate)));}
    private static int before(String kind,Map<String,Object> detail){
        if(mode.equals("off"))return 0;try{if(events.size()==32)throw new IllegalStateException("Shadow draw evidence budget exceeded");Map<String,Object> value=new LinkedHashMap<>();value.put("kind",kind);value.put("detail",detail);value.put("before",samples());events.add(value);return events.size();}catch(Throwable error){fail("shadow-before",error);return 0;}
    }
    public static void after(int token,boolean returned){if(token==0)return;try{if(token>events.size())throw new IllegalStateException("Shadow sample token changed");events.get(token-1).put("originalReturnedNormally",returned);if(!returned)fail("shadow-original-failed",new IllegalStateException());events.get(token-1).put("after",samples());}catch(Throwable error){fail("shadow-after",error);}}
    private static Map<String,Object> samples(){
        Minecraft mc=Minecraft.getInstance();if(mc.width!=1280||mc.height!=720)throw new IllegalStateException("Diagnostic coordinates require the frozen full-resolution fixture");
        if(GL11.glGetInteger(GL21.GL_PIXEL_PACK_BUFFER_BINDING)!=0||GL11.glGetInteger(GL11.GL_PACK_ROW_LENGTH)!=0||GL11.glGetInteger(GL11.GL_PACK_SKIP_ROWS)!=0||GL11.glGetInteger(GL11.GL_PACK_SKIP_PIXELS)!=0||GL11.glGetBoolean(GL11.GL_PACK_SWAP_BYTES))throw new IllegalStateException("Unknown pixel-pack state");
        int previous=GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING),draw=GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);List<Object> points=new ArrayList<>();
        try{GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,draw);for(int[] point:POINTS){ByteBuffer color=BufferUtils.createByteBuffer(4);FloatBuffer depth=BufferUtils.createFloatBuffer(1);GL11.glReadPixels(point[0],mc.height-1-point[1],1,1,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,color);GL11.glReadPixels(point[0],mc.height-1-point[1],1,1,GL11.GL_DEPTH_COMPONENT,GL11.GL_FLOAT,depth);float z=depth.get(0);points.add(Map.of("x",point[0],"y",point[1],"rgba",List.of(color.get(0)&255,color.get(1)&255,color.get(2)&255,color.get(3)&255),"depth",z,"depthRawBits",Float.floatToRawIntBits(z)));}}
        finally{GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,previous);}
        Map<String,Object> result=new LinkedHashMap<>();result.put("points",points);result.put("depthFacts",depthFacts());result.put("program",GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM));result.put("blend",GL11.glIsEnabled(GL11.GL_BLEND));result.put("blendSrcRgb",GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB));result.put("blendDstRgb",GL11.glGetInteger(GL14.GL_BLEND_DST_RGB));result.put("blendEquationRgb",GL11.glGetInteger(GL20.GL_BLEND_EQUATION_RGB));result.put("currentColor",floats(GL11.GL_CURRENT_COLOR,4));result.put("depthTest",GL11.glIsEnabled(GL11.GL_DEPTH_TEST));result.put("depthFunc",GL11.glGetInteger(GL11.GL_DEPTH_FUNC));result.put("depthWrite",GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK));result.put("depthRange",floats(GL11.GL_DEPTH_RANGE,2));if(P3PhysicalOrdinary073Evidence.enabled())result.put("physicalOrdinaryNativeState",P3PhysicalOrdinary073Evidence.nativeState());return result;
    }
    private static int subject(Object entity){try{if(actorField==null){actorField=PlayerFeatures073Qa.class.getDeclaredField("actors");actorField.setAccessible(true);}Object[] actors=(Object[])actorField.get(null);for(int i=0;i<actors.length;i++)if(actors[i]==entity)return i;return entity==Minecraft.getInstance().player?2:-1;}catch(ReflectiveOperationException error){fail("owned-subject",error);return -1;}}
    private static int[] bits(float[] values){int[] bits=new int[values.length];for(int i=0;i<values.length;i++){if(!Float.isFinite(values[i]))throw new IllegalStateException("Nonfinite graphics evidence");bits[i]=Float.floatToRawIntBits(values[i]);}return bits;}
    private static float[] floats(int name,int count){FloatBuffer data=BufferUtils.createFloatBuffer(Math.max(16,count));GL11.glGetFloatv(name,data);float[] result=new float[count];data.get(result);return result;}
    private static void fail(String stage,Throwable error){if(failure==null)failure=stage+":"+error.getClass().getName();}
}
