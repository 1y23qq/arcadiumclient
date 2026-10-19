package io.github.arcadiumclient.forgecompat;

import io.github.arcadiumclient.forgecompat.mapping.ForgeMappings;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.zip.*;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;
import org.objectweb.asm.tree.*;

final class CameraExtraFixtures {
    static final String NATIVE_CAMERA = "org/codeberg/chromatic/freelook/mixin/MixinCamera";
    static final String NATIVE_WORLD = "org/codeberg/chromatic/freelook/mixin/legacy/MixinWorldRenderer";
    static final String NATIVE_HURT = "net/uku3lig/betterhurtcam/mixin/GameRendererMixin";
    static final String GUEST_CAMERA = "keystrokesmod/mixin/impl/render/MixinActiveRenderInfo";
    static final String GUEST_WORLD = "keystrokesmod/mixin/impl/render/MixinRenderGlobal";
    static final String GUEST_RENDERER = "keystrokesmod/mixin/impl/render/MixinEntityRenderer";
    static final String CONTROLLER = "org/codeberg/chromatic/freelook/handler/FreelookHandler";
    static final String TRACKER = "org/codeberg/chromatic/freelook/util/CameraStateTracker";
    static final String HURT_API = "net/uku3lig/betterhurtcam/BetterHurtCam";
    static final String HURT_TYPE = "net/uku3lig/betterhurtcam/HurtCamType";
    static final String ACTIVE = "fixture/camera/ActiveInfo", WORLD = "fixture/camera/World", RENDERER = "fixture/camera/HurtRenderer";
    private static final String ENTITY = Type.getInternalName(CameraFixtureSupport.Entity.class), PLAYER = Type.getInternalName(CameraFixtureSupport.Player.class);
    private static final String SUPPORT = Type.getInternalName(CameraFixtureSupport.class), GAME = Type.getInternalName(CameraFixtureSupport.Game.class), GL = Type.getInternalName(CameraFixtureSupport.Gl.class);
    private CameraExtraFixtures() { }

    static Path build(Path directory, ForgeMappings mappings, Path runtime, Path guest, Map<String,String> roles) throws Exception {
        Path destination = directory.resolve("camera-extra-fixture.jar");
        String entity = mappings.classFromSrg("net/minecraft/entity/Entity"), player = mappings.classFromSrg("net/minecraft/entity/player/EntityPlayer");
        String active = mappings.classFromSrg("net/minecraft/client/renderer/ActiveRenderInfo"), world = mappings.classFromSrg("net/minecraft/client/renderer/RenderGlobal");
        String renderer = mappings.classFromSrg("net/minecraft/client/renderer/EntityRenderer"), game = mappings.classFromSrg("net/minecraft/client/Minecraft"), gl = mappings.classFromSrg("net/minecraft/client/renderer/GlStateManager");
        String camera = mappings.classFromSrg("net/minecraft/client/renderer/culling/ICamera");
        String update = mappings.methodFromSrg("net/minecraft/client/renderer/ActiveRenderInfo", "func_74583_a", "(Lnet/minecraft/entity/player/EntityPlayer;Z)V");
        String terrain = mappings.methodFromSrg("net/minecraft/client/renderer/RenderGlobal", "func_174970_a", "(Lnet/minecraft/entity/Entity;DLnet/minecraft/client/renderer/culling/ICamera;IZ)V");
        String render = mappings.methodFromSrg("net/minecraft/client/renderer/RenderGlobal", "func_174962_a", "(Lnet/minecraft/entity/Entity;D)Lorg/lwjgl/util/vector/Vector3f;");
        String hurt = mappings.methodFromSrg("net/minecraft/client/renderer/EntityRenderer", "func_78482_e", "(F)V");
        String rotate = mappings.methodFromSrg("net/minecraft/client/renderer/GlStateManager", "func_179114_b", "(FFFF)V");
        String view = mappings.methodFromSrg("net/minecraft/client/Minecraft", "func_175606_aa", "()Lnet/minecraft/entity/Entity;");
        String mc = mappings.fieldFromSrg("net/minecraft/client/renderer/RenderGlobal", "field_72777_q", "Lnet/minecraft/client/Minecraft;");
        Map<String,String> names = Map.of(update,"update",terrain,"setup",render,"direction",hurt,"hurt");
        Map<String,String> types = Map.of(entity,ENTITY,player,PLAYER,active,ACTIVE,world,WORLD,renderer,RENDERER,game,GAME,gl,GL,camera,"java/lang/Object","org/lwjgl/util/vector/Vector3f",Type.getInternalName(CameraFixtureSupport.Vector.class));
        Remapper binding = new Remapper(Opcodes.ASM9) {
            @Override public String map(String name) { return types.getOrDefault(name, switch(name) {
                case "keystrokesmod/module/ModuleManager" -> Type.getInternalName(CameraFixtureSupport.GuestManager.class);
                case "keystrokesmod/module/impl/render/Freelook" -> Type.getInternalName(CameraFixtureSupport.GuestFreelook.class);
                case "keystrokesmod/module/impl/combat/AimAssist" -> Type.getInternalName(CameraFixtureSupport.GuestAim.class);
                case "keystrokesmod/module/impl/render/NoHurtCam" -> Type.getInternalName(CameraFixtureSupport.GuestHurt.class);
                case "keystrokesmod/module/setting/impl/SliderSetting" -> Type.getInternalName(CameraFixtureSupport.Slider.class);
                default -> name;
            }); }
            @Override public String mapFieldName(String owner,String name,String descriptor) { return owner.equals(entity) || owner.equals(player) ? roles.getOrDefault(name,name) : (owner.equals(GUEST_WORLD)||owner.equals(world)) && name.equals(mc) ? "mc" : name; }
            @Override public String mapMethodName(String owner,String name,String descriptor) { return owner.equals(game) && name.equals(view) ? "getRenderViewEntity" : owner.equals(gl) && name.equals(rotate) ? "rotate" : name; }
        };
        Map<String,byte[]> files = new LinkedHashMap<>();
        addMixin(files,read(freelook(),NATIVE_CAMERA),Set.of("modifyYaw","modifyPitch"),false,binding,names,types,roles,rotate,mc);
        addMixin(files,read(freelook(),NATIVE_WORLD),Set.of("modifyYaw","modifyPitch","modifyLastYaw","modifyLastPitch"),false,binding,names,types,roles,rotate,mc);
        addMixin(files,read(hurtcam(),NATIVE_HURT),Set.of("betterhurtcam$modifyHurtCam","betterhurtcam$disableHurtCam"),false,binding,names,types,roles,rotate,mc);
        addMixin(files,read(guest,GUEST_CAMERA),Set.of("redirectRotationYaw","redirectRotationPitch"),false,binding,names,types,roles,rotate,mc);
        addMixin(files,read(guest,GUEST_WORLD),Set.of("raven$setupTerrainRotationYaw","raven$setupTerrainRotationPitch","raven$isFreelookActive","raven$isDetachedAimCameraActive"),true,binding,names,types,roles,rotate,mc);
        addMixin(files,read(guest,GUEST_RENDERER),Set.of("injectNoHurtCam"),false,binding,names,types,roles,rotate,mc);
        ClassNode controller = read(freelook(),CONTROLLER); controller.version=Opcodes.V17; controller.innerClasses.clear();
        controller.fields.removeIf(field->!Set.of("INSTANCE","freelookToggled").contains(field.name)); controller.methods.removeIf(method->!Set.of("cameraYaw","cameraPitch").contains(method.name));
        controller.methods.add(constructor(CONTROLLER,"java/lang/Object"));
        MethodNode clinit=new MethodNode(Opcodes.ACC_STATIC,"<clinit>","()V",null,null);clinit.visitTypeInsn(Opcodes.NEW,CONTROLLER);clinit.visitInsn(Opcodes.DUP);clinit.visitMethodInsn(Opcodes.INVOKESPECIAL,CONTROLLER,"<init>","()V",false);clinit.visitFieldInsn(Opcodes.PUTSTATIC,CONTROLLER,"INSTANCE","L"+CONTROLLER+";");clinit.visitInsn(Opcodes.RETURN);controller.methods.add(clinit);
        ClassNode mapped=new ClassNode();controller.accept(new ClassRemapper(mapped,binding));files.put(CONTROLLER+".class",bytes(mapped));
        ClassNode tracker=read(freelook(),TRACKER);tracker.version=Opcodes.V17;files.put(TRACKER+".class",bytes(tracker));
        files.put("fixture/camera/TrackedEntity.class",tracked("fixture/camera/TrackedEntity",ENTITY,tracker));
        files.put("fixture/camera/TrackedPlayer.class",tracked("fixture/camera/TrackedPlayer",PLAYER,tracker));
        List<String> updateOrder=order(read(runtime,active),update,player,roles), setupOrder=order(read(runtime,world),terrain,entity,roles), renderOrder=order(read(runtime,world),render,entity,roles);
        if(updateOrder.size()!=2 || Collections.frequency(setupOrder,"yaw")!=2 || Collections.frequency(setupOrder,"pitch")!=2)throw new AssertionError("Actual camera anchors changed");
        long rotations=method(read(runtime,renderer),hurt).instructions.iterator().hasNext()?Arrays.stream(method(read(runtime,renderer),hurt).instructions.toArray()).filter(i->i instanceof MethodInsnNode call&&call.owner.equals(gl)&&call.name.equals(rotate)&&call.desc.equals("(FFFF)V")).count():0;
        if(rotations!=4)throw new AssertionError("Actual hurt rotations changed");
        files.put(ACTIVE+".class",active(updateOrder));files.put(WORLD+".class",world(setupOrder,renderOrder));files.put(RENDERER+".class",hurtRenderer());
        files.put(HURT_API+".class",hurtApi());ClassNode type=read(hurtcam(),HURT_TYPE);type.version=Opcodes.V17;files.put(HURT_TYPE+".class",bytes(type));
        files.put("extra-order.txt",(String.join(",",updateOrder)+"\n"+String.join(",",setupOrder)+"\n"+String.join(",",renderOrder)).getBytes(StandardCharsets.UTF_8));
        files.put("extra-guest.json",config("keystrokesmod.mixin.impl.render","MixinActiveRenderInfo","MixinRenderGlobal","MixinEntityRenderer"));
        files.put("extra-native.json",config("org.codeberg.chromatic.freelook.mixin","MixinCamera","legacy.MixinWorldRenderer"));
        files.put("extra-hurt.json",config("net.uku3lig.betterhurtcam.mixin","GameRendererMixin"));
        files.put("META-INF/services/org.spongepowered.asm.service.IGlobalPropertyService",(CameraMixinRuntimeProbe.Properties.class.getName()+"\n").getBytes(StandardCharsets.UTF_8));
        try(ZipOutputStream zip=new ZipOutputStream(Files.newOutputStream(destination))){for(var entry:files.entrySet()){zip.putNextEntry(new ZipEntry(entry.getKey()));zip.write(entry.getValue());zip.closeEntry();}}
        return destination;
    }

    private static void addMixin(Map<String,byte[]> files,ClassNode source,Set<String> handlers,boolean shadow,Remapper binding,Map<String,String> names,Map<String,String> types,Map<String,String> roles,String rotate,String mc){
        source.version=Opcodes.V17;source.innerClasses.clear();source.interfaces.clear();source.methods.removeIf(method->!handlers.contains(method.name));source.fields.removeIf(field->!shadow||!field.name.equals(mc));
        if(source.visibleAnnotations!=null)source.visibleAnnotations.removeIf(a->!a.desc.equals("Lorg/spongepowered/asm/mixin/Mixin;"));if(source.invisibleAnnotations!=null)source.invisibleAnnotations.removeIf(a->!a.desc.equals("Lorg/spongepowered/asm/mixin/Mixin;"));
        ClassNode mapped=new ClassNode();source.accept(new ClassRemapper(mapped,binding));
        for(MethodNode handler:mapped.methods)if(handler.visibleAnnotations!=null)for(AnnotationNode annotation:handler.visibleAnnotations){
            Object methods=value(annotation,"method");if(methods instanceof List<?> selectors)put(annotation,"method",new ArrayList<>(selectors.stream().map(v->selector(v.toString(),binding,names)).toList()));
            for(AnnotationNode point:points(annotation)){String target=Objects.toString(value(point,"target"),"");
                for(var type:types.entrySet())for(var role:roles.entrySet())if(target.equals("L"+type.getKey()+";"+role.getKey()+":F"))put(point,"target","L"+type.getValue()+";"+role.getValue()+":F");
                if(target.endsWith(";"+rotate+"(FFFF)V"))put(point,"target","L"+GL+";rotate(FFFF)V");
            }
        }
        files.put(mapped.name+".class",bytes(mapped));
    }

    private static byte[] active(List<String> order){ClassNode node=base(ACTIVE);node.fields.add(new FieldNode(Opcodes.ACC_PUBLIC|Opcodes.ACC_STATIC,"result","F",null,null));node.methods.add(sum(ACTIVE,"update","(L"+PLAYER+";Z)V",order,PLAYER,true));return bytes(node);}
    private static byte[] world(List<String> setup,List<String> render){ClassNode node=base(WORLD);node.fields.add(new FieldNode(Opcodes.ACC_PRIVATE,"mc","L"+GAME+";",null,null));node.fields.add(new FieldNode(Opcodes.ACC_PUBLIC,"result","F",null,null));
        MethodNode init=new MethodNode(Opcodes.ACC_PUBLIC,"<init>","(L"+GAME+";)V",null,null);init.visitVarInsn(Opcodes.ALOAD,0);init.visitMethodInsn(Opcodes.INVOKESPECIAL,"java/lang/Object","<init>","()V",false);init.visitVarInsn(Opcodes.ALOAD,0);init.visitVarInsn(Opcodes.ALOAD,1);init.visitFieldInsn(Opcodes.PUTFIELD,WORLD,"mc","L"+GAME+";");init.visitInsn(Opcodes.RETURN);node.methods.add(init);
        node.methods.add(sum(WORLD,"setup","(L"+ENTITY+";DLjava/lang/Object;IZ)V",setup,ENTITY,false));
        MethodNode direction=sum(WORLD,"direction","(L"+ENTITY+";D)L"+Type.getInternalName(CameraFixtureSupport.Vector.class)+";",render,ENTITY,false);direction.instructions.remove(direction.instructions.getLast());
        direction.visitTypeInsn(Opcodes.NEW,Type.getInternalName(CameraFixtureSupport.Vector.class));direction.visitInsn(Opcodes.DUP);direction.visitVarInsn(Opcodes.ALOAD,0);direction.visitFieldInsn(Opcodes.GETFIELD,WORLD,"result","F");direction.visitMethodInsn(Opcodes.INVOKESPECIAL,Type.getInternalName(CameraFixtureSupport.Vector.class),"<init>","(F)V",false);direction.visitInsn(Opcodes.ARETURN);node.methods.add(direction);return bytes(node);}
    private static MethodNode sum(String owner,String name,String desc,List<String> order,String receiver,boolean isStatic){MethodNode method=new MethodNode(Opcodes.ACC_PUBLIC|(isStatic?Opcodes.ACC_STATIC:0),name,desc,null,null);if(!isStatic)method.visitVarInsn(Opcodes.ALOAD,0);method.visitInsn(Opcodes.FCONST_0);for(String role:order){method.visitVarInsn(Opcodes.ALOAD,isStatic?0:1);method.visitFieldInsn(Opcodes.GETFIELD,receiver,role,"F");method.visitInsn(Opcodes.FADD);}method.visitFieldInsn(isStatic?Opcodes.PUTSTATIC:Opcodes.PUTFIELD,owner,"result","F");method.visitInsn(Opcodes.RETURN);return method;}
    private static byte[] hurtRenderer(){ClassNode node=base(RENDERER);node.methods.add(constructor(RENDERER,"java/lang/Object"));MethodNode method=new MethodNode(Opcodes.ACC_PUBLIC,"hurt","(F)V",null,null);float[][] input={{14,0,1,0},{28,1,0,0},{-7,0,0,1},{-0.0f,1,1,1}};for(float[] values:input){for(float value:values)method.visitLdcInsn(value);method.visitMethodInsn(Opcodes.INVOKESTATIC,GL,"rotate","(FFFF)V",false);}method.visitInsn(Opcodes.RETURN);node.methods.add(method);return bytes(node);}
    private static byte[] hurtApi(){ClassNode node=base(HURT_API);node.fields.add(new FieldNode(Opcodes.ACC_PUBLIC|Opcodes.ACC_STATIC,"enabled","Z",null,null));node.fields.add(new FieldNode(Opcodes.ACC_PUBLIC|Opcodes.ACC_STATIC,"type","L"+HURT_TYPE+";",null,null));node.fields.add(new FieldNode(Opcodes.ACC_PUBLIC|Opcodes.ACC_STATIC,"multiplier","D",null,null));
        for(String[] value:new String[][]{{"isEnabled","enabled","Z","countHurtEnabled"},{"getType","type","L"+HURT_TYPE+";","countHurtType"},{"getMultiplier","multiplier","D","countHurtMultiplier"}}){MethodNode method=new MethodNode(Opcodes.ACC_PUBLIC|Opcodes.ACC_STATIC,value[0],"()"+value[2],null,null);method.visitMethodInsn(Opcodes.INVOKESTATIC,SUPPORT,value[3],"()V",false);method.visitFieldInsn(Opcodes.GETSTATIC,HURT_API,value[1],value[2]);method.visitInsn(value[2].equals("Z")?Opcodes.IRETURN:value[2].equals("D")?Opcodes.DRETURN:Opcodes.ARETURN);node.methods.add(method);}return bytes(node);}
    private static byte[] tracked(String owner,String parent,ClassNode tracker){ClassNode node=base(owner);node.superName=parent;node.interfaces.add(TRACKER);node.methods.add(constructor(owner,parent));for(MethodNode signature:tracker.methods){MethodNode method=new MethodNode(Opcodes.ACC_PUBLIC,signature.name,signature.desc,null,null);boolean yaw=signature.name.toLowerCase(Locale.ROOT).contains("yaw");method.visitVarInsn(Opcodes.ALOAD,0);if(signature.desc.equals("()F")){method.visitMethodInsn(Opcodes.INVOKESTATIC,SUPPORT,yaw?"nativeYaw":"nativePitch","(L"+ENTITY+";)F",false);method.visitInsn(Opcodes.FRETURN);}else{method.visitVarInsn(Opcodes.FLOAD,1);method.visitFieldInsn(Opcodes.PUTFIELD,ENTITY,yaw?"nativeYaw":"nativePitch","F");method.visitInsn(Opcodes.RETURN);}node.methods.add(method);}return bytes(node);}
    private static List<String> order(ClassNode node,String method,String owner,Map<String,String> roles){List<String> result=new ArrayList<>();for(AbstractInsnNode instruction:method(node,method).instructions)if(instruction instanceof FieldInsnNode field&&field.getOpcode()==Opcodes.GETFIELD&&field.owner.equals(owner)&&roles.containsKey(field.name))result.add(roles.get(field.name));return result;}
    private static String selector(String value,Remapper binding,Map<String,String> names){String owner="",member=value;if(value.startsWith("L")&&value.indexOf(';')>=0){int separator=value.indexOf(';');owner="L"+binding.map(value.substring(1,separator))+";";member=value.substring(separator+1);}int descriptor=member.indexOf('(');String name=descriptor<0?member:member.substring(0,descriptor);return owner+names.getOrDefault(name,name)+(descriptor<0?"":binding.mapMethodDesc(member.substring(descriptor)));}
    private static ClassNode base(String owner){ClassNode node=new ClassNode();node.visit(Opcodes.V17,Opcodes.ACC_PUBLIC,owner,null,"java/lang/Object",null);return node;}
    private static MethodNode constructor(String owner,String parent){MethodNode method=new MethodNode(Opcodes.ACC_PUBLIC,"<init>","()V",null,null);method.visitVarInsn(Opcodes.ALOAD,0);method.visitMethodInsn(Opcodes.INVOKESPECIAL,parent,"<init>","()V",false);method.visitInsn(Opcodes.RETURN);return method;}
    private static byte[] config(String namespace,String...mixins){return ("{\"required\":true,\"minVersion\":\"0.8\",\"package\":\""+namespace+"\",\"compatibilityLevel\":\"JAVA_17\",\"mixins\":[\""+String.join("\",\"",mixins)+"\"],\"injectors\":{\"defaultRequire\":1}}").getBytes(StandardCharsets.UTF_8);}
    static Path freelook(){return ForgeTestFixtures.nativeJar("freelook-2.3.0+mc1.8.9.jar").toAbsolutePath().normalize();}
    static Path hurtcam(){return ForgeTestFixtures.nativeJar("BetterHurtCam-1.15.1+mc1.8.9-ornithe.jar").toAbsolutePath().normalize();}
    static ClassNode read(Path jar,String name)throws Exception{try(ZipFile zip=new ZipFile(jar.toFile());var input=zip.getInputStream(Objects.requireNonNull(zip.getEntry(name+".class"),name))){ClassNode node=new ClassNode();new ClassReader(input.readAllBytes()).accept(node,0);return node;}}
    static MethodNode method(ClassNode node,String name){return node.methods.stream().filter(method->method.name.equals(name)).findFirst().orElseThrow();}
    static byte[] bytes(ClassNode node){ClassWriter writer=new ClassWriter(ClassWriter.COMPUTE_MAXS);node.accept(writer);return writer.toByteArray();}
    static Object value(AnnotationNode node,String key){if(node.values!=null)for(int i=0;i<node.values.size();i+=2)if(node.values.get(i).equals(key))return node.values.get(i+1);return null;}
    static List<AnnotationNode> points(AnnotationNode node){Object at=value(node,"at");return at instanceof List<?> list?list.stream().map(AnnotationNode.class::cast).toList():at instanceof AnnotationNode point?List.of(point):List.of();}
    static void put(AnnotationNode node,String key,Object value){if(node.values!=null)for(int i=0;i<node.values.size();i+=2)if(node.values.get(i).equals(key)){node.values.set(i+1,value);return;}if(node.values==null)node.values=new ArrayList<>();node.values.add(key);node.values.add(value);}
}
