package io.github.arcadiumclient.forgecompat;

import com.bawnorton.mixinsquared.MixinSquaredBootstrap;
import com.bawnorton.mixinsquared.adjuster.MixinAnnotationAdjusterRegistrar;
import com.llamalad7.mixinextras.MixinExtrasBootstrap;
import io.github.arcadiumclient.forgecompat.mapping.ForgeMappings;
import java.io.StringReader;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import net.fabricmc.loader.api.MappingResolver;
import org.spongepowered.asm.launch.MixinBootstrap;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.Mixins;
import org.spongepowered.asm.mixin.transformer.IMixinTransformer;

public final class CameraExtendedRuntimeProbe {
    private CameraExtendedRuntimeProbe() { }

    public static void main(String[] args) {
        try {
            System.setProperty("mixin.service", CameraMixinRuntimeProbe.Service.class.getName()); System.setProperty("camera.fixture", args[0]);
            MixinBootstrap.init(); MixinEnvironment.getCurrentEnvironment().setSide(MixinEnvironment.Side.CLIENT);
            var constructor=Class.forName("org.spongepowered.asm.mixin.transformer.MixinTransformer").getDeclaredConstructor();constructor.setAccessible(true);
            IMixinTransformer transformer=(IMixinTransformer)constructor.newInstance();MixinExtrasBootstrap.init();
            MixinSquaredBootstrap.init();MixinAnnotationAdjusterRegistrar.register(new HostCameraCompatibility(mappings()));MixinSquaredBootstrap.reOrderExtensions();
            boolean guest=args[1].equals("fixed");if(guest)Mixins.addConfiguration("extra-guest.json");Mixins.addConfiguration("extra-native.json");Mixins.addConfiguration("extra-hurt.json");
            Map<String,byte[]> definitions=new HashMap<>();
            for(String internal:List.of(CameraExtraFixtures.ACTIVE,CameraExtraFixtures.WORLD,CameraExtraFixtures.RENDERER)){
                byte[] original;try(var input=CameraExtendedRuntimeProbe.class.getClassLoader().getResourceAsStream(internal+".class")){original=input.readAllBytes();}
                byte[] result=transformer.transformClassBytes(internal.replace('/','.'),internal.replace('/','.'),original);definitions.put(internal.replace('/','.'),result);
                Path output=Path.of(args[2]).resolve(internal+".class");Files.createDirectories(output.getParent());Files.write(output,result);
            }
            ClassLoader loader=new ClassLoader(CameraExtendedRuntimeProbe.class.getClassLoader()){
                @Override protected Class<?> loadClass(String name,boolean resolve)throws ClassNotFoundException{if(!definitions.containsKey(name))return super.loadClass(name,resolve);synchronized(getClassLoadingLock(name)){Class<?> type=findLoadedClass(name);if(type==null){byte[] body=definitions.get(name);type=defineClass(name,body,0,body.length);}if(resolve)resolveClass(type);return type;}}
            };
            Class<?> active=loader.loadClass(CameraExtraFixtures.ACTIVE.replace('/','.')),world=loader.loadClass(CameraExtraFixtures.WORLD.replace('/','.')),renderer=loader.loadClass(CameraExtraFixtures.RENDERER.replace('/','.'));
            Class<?> controller=Class.forName(CameraExtraFixtures.CONTROLLER.replace('/','.'));Object state=controller.getField("INSTANCE").get(null);
            Class<?> trackedEntity=Class.forName("fixture.camera.TrackedEntity"),trackedPlayer=Class.forName("fixture.camera.TrackedPlayer");
            List<List<String>> orders;try(var input=CameraExtendedRuntimeProbe.class.getClassLoader().getResourceAsStream("extra-order.txt")){orders=new String(input.readAllBytes(),StandardCharsets.UTF_8).lines().map(line->Arrays.asList(line.split(","))).toList();}
            int cameraCases=0,worldCases=0,renderCases=0;
            for(boolean nativeActive:List.of(false,true))for(boolean tracked:List.of(false,true))for(int freelook:new int[]{0,1,2,3})for(int aim:new int[]{0,1,2}){
                CameraFixtureSupport.reset();setGuest(freelook,aim);controller.getField("freelookToggled").setBoolean(state,nativeActive);
                CameraFixtureSupport.Player player=tracked?(CameraFixtureSupport.Player)trackedPlayer.getConstructor().newInstance():new CameraFixtureSupport.Player();
                active.getMethod("update",CameraFixtureSupport.Player.class,boolean.class).invoke(null,player,false);
                bits(expected(orders.get(0),player,nativeActive&&tracked,guest,freelook,aim),active.getField("result").getFloat(null),"Static player camera");
                checks(guest,freelook,orders.get(0).size());require(CameraFixtureSupport.nativeYawReads+CameraFixtureSupport.nativePitchReads==(nativeActive&&tracked?orders.get(0).size():0),"Static native operation count");cameraCases++;
                for(boolean matches:List.of(false,true)){
                    CameraFixtureSupport.reset();setGuest(freelook,aim);
                    CameraFixtureSupport.Entity entity=tracked?(CameraFixtureSupport.Entity)trackedEntity.getConstructor().newInstance():new CameraFixtureSupport.Entity();
                    Object target=world.getConstructor(CameraFixtureSupport.Game.class).newInstance(new CameraFixtureSupport.Game(matches?entity:new CameraFixtureSupport.Entity()));
                    world.getMethod("setup",CameraFixtureSupport.Entity.class,double.class,Object.class,int.class,boolean.class).invoke(target,entity,.25,null,1,false);
                    bits(expected(orders.get(1),entity,nativeActive&&tracked,guest&&matches,freelook,aim),world.getField("result").getFloat(target),"World view camera");
                    checks(guest&&matches,freelook,orders.get(1).size());require(CameraFixtureSupport.nativeYawReads+CameraFixtureSupport.nativePitchReads==(nativeActive&&tracked?orders.get(1).size():0),"World operation count");worldCases++;
                    CameraFixtureSupport.reset();setGuest(freelook,aim);
                    CameraFixtureSupport.Vector vector=(CameraFixtureSupport.Vector)world.getMethod("direction",CameraFixtureSupport.Entity.class,double.class).invoke(target,entity,.5);
                    bits(expected(orders.get(2),entity,nativeActive&&tracked,false,freelook,aim),world.getField("result").getFloat(target),"World second selector");
                    bits(world.getField("result").getFloat(target),vector.value,"World direction result");
                    checks(false,freelook,orders.get(2).size());require(CameraFixtureSupport.nativeYawReads+CameraFixtureSupport.nativePitchReads==(nativeActive&&tracked?orders.get(2).size():0),"Second selector operation count");renderCases++;
                }
            }
            require(cameraCases==48&&worldCases==96&&renderCases==96,"Incomplete expanded camera matrix");
            Class<?> api=Class.forName(CameraExtraFixtures.HURT_API.replace('/','.')),type=Class.forName(CameraExtraFixtures.HURT_TYPE.replace('/','.'));
            Object old=type.getField("OLD").get(null),current=type.getField("YAW_BASED").get(null),target=renderer.getConstructor().newInstance();Method hurt=renderer.getMethod("hurt",float.class);int hurtCases=0;
            float[][] originals={{14,0,1,0},{28,1,0,0},{-7,0,0,1},{-0.0f,1,1,1}};
            for(boolean enabled:List.of(false,true))for(Object style:List.of(old,current))for(double multiplier:new double[]{0,1,2.25})for(int module:new int[]{0,1,2})for(double slider:new double[]{.5,14}){
                CameraFixtureSupport.reset();api.getField("enabled").setBoolean(null,enabled);api.getField("type").set(null,style);api.getField("multiplier").setDouble(null,multiplier);
                if(module!=0){CameraFixtureSupport.GuestManager.noHurtCam=new CameraFixtureSupport.GuestHurt();CameraFixtureSupport.GuestManager.noHurtCam.enabled=module==2;CameraFixtureSupport.GuestManager.noHurtCam.multiplier.value=slider;}
                hurt.invoke(target,.5f);require(CameraFixtureSupport.Gl.rotations.size()==(enabled?4:0),"Original GL rotation duplicated or omitted");
                for(int i=0;i<CameraFixtureSupport.Gl.rotations.size();i++){
                    float[] original=originals[i],actual=CameraFixtureSupport.Gl.rotations.get(i);float angle=style==old&&original[2]!=0?0:original[0];angle=(float)(angle*multiplier);if(guest&&module==2)angle=(float)((angle/14.0f)*slider);
                    bits(angle,actual[0],"Composed hurt angle");for(int axis=1;axis<4;axis++)bits(original[axis],actual[axis],"Hurt axis");
                }
                require(CameraFixtureSupport.nativeHurtEnabledReads==1,"Original cancellable HEAD changed");require(CameraFixtureSupport.nativeHurtMultiplierReads==(enabled?4:0),"Native multiplier evaluation count changed");
                if(module!=0){require(CameraFixtureSupport.GuestManager.noHurtCam.checks==(guest&&enabled?4:0),"Guest hurt operation count changed");require(CameraFixtureSupport.GuestManager.noHurtCam.multiplier.reads==(guest&&enabled&&module==2?4:0),"Guest slider count changed");}hurtCases++;
            }
            require(hurtCases==72,"Incomplete hurt matrix");
            api.getField("enabled").setBoolean(null,true);api.getField("type").set(null,current);api.getField("multiplier").setDouble(null,1);
            CameraFixtureSupport.reset();RuntimeException failure=new RuntimeException("native hurt operand");CameraFixtureSupport.nativeHurtFailure=failure;expectFailure(hurt,target,failure);require(CameraFixtureSupport.Gl.rotations.isEmpty(),"Failed native operation reached GL");
            CameraFixtureSupport.reset();failure=new RuntimeException("GL downstream operation");CameraFixtureSupport.Gl.failure=failure;expectFailure(hurt,target,failure);
            if(guest){CameraFixtureSupport.reset();CameraFixtureSupport.GuestManager.noHurtCam=new CameraFixtureSupport.GuestHurt();failure=new RuntimeException("guest hurt operation");CameraFixtureSupport.GuestManager.noHurtCam.failure=failure;expectFailure(hurt,target,failure);require(CameraFixtureSupport.Gl.rotations.isEmpty(),"Failed guest operation reached GL");}
            System.out.println("CAMERA_EXTRA_PROBE SUCCESS mode="+args[1]+" player="+cameraCases+" world="+worldCases+" direction="+renderCases+" hurt="+hurtCases);
        }catch(Throwable failure){while((failure instanceof InvocationTargetException||failure instanceof ExceptionInInitializerError)&&failure.getCause()!=null)failure=failure.getCause();failure.printStackTrace();System.exit(1);}
    }

    private static void setGuest(int freelook,int aim){if(freelook!=0){CameraFixtureSupport.GuestManager.freelook=new CameraFixtureSupport.GuestFreelook();CameraFixtureSupport.GuestManager.freelook.enabled=freelook!=1;CameraFixtureSupport.GuestFreelook.perspectiveToggled=freelook==3;}if(aim!=0){CameraFixtureSupport.GuestManager.aimAssist=new CameraFixtureSupport.GuestAim();CameraFixtureSupport.GuestManager.aimAssist.active=aim==2;}}
    private static void checks(boolean guest,int freelook,int count){if(freelook!=0)require(CameraFixtureSupport.GuestManager.freelook.checks==(guest?count:0),"Guest camera operation skipped or duplicated");}
    private static float expected(List<String> order,CameraFixtureSupport.Entity entity,boolean nativeActive,boolean guest,int freelook,int aim){float result=0;for(String role:order){boolean yaw=role.toLowerCase(Locale.ROOT).contains("yaw");result+=nativeActive?(yaw?entity.nativeYaw:entity.nativePitch):guest&&freelook==3?(yaw?303:404):guest&&aim==2?switch(role){case "yaw"->505;case "previousYaw"->506;case "pitch"->607;default->608;}:switch(role){case "yaw"->entity.yaw;case "previousYaw"->entity.previousYaw;case "pitch"->entity.pitch;default->entity.previousPitch;};}return result;}
    private static void expectFailure(Method method,Object target,RuntimeException expected)throws Exception{try{method.invoke(target,.5f);throw new AssertionError("Operation failure swallowed");}catch(InvocationTargetException failure){require(failure.getCause()==expected,"Operation exception identity changed");}}
    private static void bits(float expected,float actual,String message){require(Float.floatToRawIntBits(expected)==Float.floatToRawIntBits(actual),message+" expected="+expected+" actual="+actual);}
    private static void require(boolean valid,String message){if(!valid)throw new AssertionError(message);}

    static ForgeMappings mappings() throws Exception {
        Map<String,String> names=Map.of("e",CameraFixtureSupport.Entity.class.getName(),"p",CameraFixtureSupport.Player.class.getName(),"r",CameraExtraFixtures.RENDERER.replace('/','.'),"a",CameraExtraFixtures.ACTIVE.replace('/','.'),"w",CameraExtraFixtures.WORLD.replace('/','.'),"c","java.lang.Object","g",CameraFixtureSupport.Gl.class.getName(),"v",CameraFixtureSupport.Vector.class.getName());
        return new ForgeMappings(new MappingResolver(){
            @Override public Collection<String> getNamespaces(){return List.of("official","intermediary");}
            @Override public String getCurrentRuntimeNamespace(){return "intermediary";}
            @Override public String mapClassName(String namespace,String name){return names.getOrDefault(name,name);}
            @Override public String unmapClassName(String namespace,String name){return names.entrySet().stream().filter(entry->entry.getValue().equals(name)).map(Map.Entry::getKey).findFirst().orElse(name);}
            @Override public String mapMethodName(String namespace,String owner,String name,String descriptor){return name;}
            @Override public String mapFieldName(String namespace,String owner,String name,String descriptor){return name;}
        },new StringReader("CL: e net/minecraft/entity/Entity\nCL: p net/minecraft/entity/player/EntityPlayer\nCL: r net/minecraft/client/renderer/EntityRenderer\nCL: a net/minecraft/client/renderer/ActiveRenderInfo\nCL: w net/minecraft/client/renderer/RenderGlobal\nCL: c net/minecraft/client/renderer/culling/ICamera\nCL: g net/minecraft/client/renderer/GlStateManager\nCL: v org/lwjgl/util/vector/Vector3f\n"
            +"FD: e/yaw net/minecraft/entity/Entity/field_70177_z\nFD: e/previousYaw net/minecraft/entity/Entity/field_70126_B\nFD: e/pitch net/minecraft/entity/Entity/field_70125_A\nFD: e/previousPitch net/minecraft/entity/Entity/field_70127_C\n"
            +"MD: r/hurt (F)V net/minecraft/client/renderer/EntityRenderer/func_78482_e (F)V\nMD: a/update (Lp;Z)V net/minecraft/client/renderer/ActiveRenderInfo/func_74583_a (Lnet/minecraft/entity/player/EntityPlayer;Z)V\n"
            +"MD: w/setup (Le;DLc;IZ)V net/minecraft/client/renderer/RenderGlobal/func_174970_a (Lnet/minecraft/entity/Entity;DLnet/minecraft/client/renderer/culling/ICamera;IZ)V\nMD: w/direction (Le;D)Lv; net/minecraft/client/renderer/RenderGlobal/func_174962_a (Lnet/minecraft/entity/Entity;D)Lorg/lwjgl/util/vector/Vector3f;\nMD: g/rotate (FFFF)V net/minecraft/client/renderer/GlStateManager/func_179114_b (FFFF)V\n"));
    }
}
