package io.github.arcadiumclient.forgecompat;

import com.google.gson.JsonParser;
import com.google.common.util.concurrent.*;
import io.github.arcadiumclient.forgecompat.mapping.ForgeMappings;
import io.github.arcadiumclient.forgecompat.transform.ForgeJarTransformer;
import java.io.StringReader;
import java.lang.reflect.InvocationTargetException;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.ZipFile;
import net.fabricmc.loader.api.MappingResolver;
import net.fabricmc.loader.impl.lib.mappingio.MappingReader;
import net.fabricmc.loader.impl.lib.mappingio.tree.MappingTree;
import net.fabricmc.loader.impl.lib.mappingio.tree.MemoryMappingTree;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.BasicVerifier;

import static org.junit.jupiter.api.Assertions.*;

class HostServerShutdownCompatibilityTest {
    private static final String SERVER = "net/minecraft/server/MinecraftServer";
    private static final String INTEGRATED = "net/minecraft/server/integrated/IntegratedServer";
    private static ForgeMappings mappings;
    private static ForgeJarTransformer transformer;
    private static Path runtime, originalForge;
    private static ClassNode base, integrated, logout;
    private static String stop, submitCallable, submitRunnable, sameThread, stopped, getThread, getPlayers;
    private static String queueField, threadField, runningField, stoppedField, playersField, lanField;
    private static String playerDescriptor;
    private static int sequence;

    @BeforeAll static void setup() throws Exception {
        Path root = Path.of("..").toAbsolutePath().normalize();
        Path game = root.resolve("build/forge-compat/runtime-qa/20261015-170443-2c95ac4632/all/game");
        runtime = game.resolve(".fabric/remappedJars/minecraft-1.8.9-0.19.5/client-intermediary.jar");
        Path tiny = Path.of(System.getProperty("user.home"), ".gradle/caches/fabric-loom/1.8.9/calamus-gen2-intermediary-v2.tiny");
        Assumptions.assumeTrue(Files.isRegularFile(runtime) && Files.isRegularFile(tiny));
        MemoryMappingTree tree = new MemoryMappingTree(); MappingReader.read(new StringReader(Files.readString(tiny)), tree);
        var constructor = Class.forName("net.fabricmc.loader.impl.MappingResolverImpl").getDeclaredConstructor(MappingTree.class, String.class); constructor.setAccessible(true);
        try (var reader = Files.newBufferedReader(root.resolve("build/forge-compat/dependencies/forge-1.8.9.srg"))) { mappings = new ForgeMappings((MappingResolver)constructor.newInstance(tree,"intermediary"),reader); }
        originalForge = root.resolve("build/forge-compat/dependencies/forge-1.8.9-11.15.1.2318-1.8.9-universal.jar");
        transformer = new ForgeJarTransformer();
        try (var files = Files.list(game.resolve(".arcadium/forge-compat/game"))) { transformer.index(files.filter(path -> path.toString().endsWith(".jar")).findFirst().orElseThrow(),mappings,true); }
        transformer.index(originalForge, mappings, true);
        base = read(runtime,mappings.classFromSrg(SERVER)); integrated = read(runtime,mappings.classFromSrg(INTEGRATED));
        stop = mappings.methodFromSrg(INTEGRATED,"func_71263_m","()V");
        submitCallable = mappings.methodFromSrg(SERVER,"func_175586_a","(Ljava/util/concurrent/Callable;)Lcom/google/common/util/concurrent/ListenableFuture;");
        submitRunnable = mappings.methodFromSrg(SERVER,"func_152344_a","(Ljava/lang/Runnable;)Lcom/google/common/util/concurrent/ListenableFuture;");
        sameThread = mappings.methodFromSrg(SERVER,"func_152345_ab","()Z"); stopped = mappings.methodFromSrg(SERVER,"func_71241_aa","()Z");
        getThread = HostServerShutdownCompatibility.THREAD_ACCESSOR;
        String manager = "net/minecraft/server/management/ServerConfigurationManager";
        getPlayers = mappings.methodFromSrg(SERVER,"func_71203_ab","()L"+manager+";");
        queueField = mappings.fieldFromSrg(SERVER,"field_175589_i","Ljava/util/Queue;"); threadField = mappings.fieldFromSrg(SERVER,"field_175590_aa","Ljava/lang/Thread;");
        runningField = mappings.fieldFromSrg(SERVER,"field_71317_u","Z"); stoppedField = mappings.fieldFromSrg(SERVER,"field_71316_v","Z");
        playersField = mappings.fieldFromSrg(SERVER,"field_71318_t","L"+manager+";");
        lanField = mappings.fieldFromSrg(INTEGRATED,"field_71345_q","Lnet/minecraft/client/multiplayer/ThreadLanServerPing;");
        MethodNode shutdown = method(integrated,stop);
        String task = Arrays.stream(shutdown.instructions.toArray()).filter(instruction -> instruction instanceof TypeInsnNode type && type.getOpcode()==Opcodes.NEW).map(TypeInsnNode.class::cast).findFirst().orElseThrow().desc;
        logout = read(runtime,task); playerDescriptor = mappings.descriptorFromSrg("(Lnet/minecraft/entity/player/EntityPlayerMP;)V");
    }

    @Test void preservesTheOriginalLogoutRunnableAndShutdownBookendsAndOnlyChangesTheWait() throws Exception {
        ClassNode node=copy(integrated); MethodNode target=method(node,stop);
        List<AbstractInsnNode> before=List.of(target.instructions.toArray());
        HostServerShutdownCompatibility.normalize(node,mappings);
        assertTrue(List.of(target.instructions.toArray()).containsAll(before));
        assertEquals(1,calls(target).stream().filter(call->call.owner.endsWith("/HostServerShutdownCompatibility")&&call.name.equals("await")).count());
        assertEquals(1,calls(target).stream().filter(call->call.getOpcode()==Opcodes.INVOKESPECIAL&&call.owner.equals(base.name)&&call.name.equals(stop)).count());
        assertEquals(1,calls(target).stream().filter(call->call.name.equals("interrupt")).count());
        assertEquals(1,calls(target).stream().filter(call->call.name.equals(submitRunnable)).count());
        new Analyzer<>(new BasicVerifier()).analyze(node.name,target);
        byte[] once=bytes(node); HostServerShutdownCompatibility.normalize(node,mappings); assertArrayEquals(once,bytes(node));
        ClassNode other=copy(base); other.name="fixture/UnrelatedServer"; byte[] untouched=bytes(other); HostServerShutdownCompatibility.normalize(other,mappings); assertArrayEquals(untouched,bytes(other));
    }

    @Test void addsOnlyTheActualPrivateThreadFieldAccessorAndLeavesEveryOriginalServerMemberIntact() throws Exception {
        ClassNode node=copy(base); List<MethodNode> methods=List.copyOf(node.methods); List<FieldNode> fields=List.copyOf(node.fields);
        Map<MethodNode,byte[]> before=new IdentityHashMap<>();
        for(MethodNode method:methods){ClassNode holder=new ClassNode();holder.version=node.version;holder.name=node.name;holder.superName=node.superName;holder.methods.add(method);before.put(method,bytes(holder));}
        HostServerShutdownCompatibility.normalize(node,mappings);
        assertEquals(methods.size()+1,node.methods.size());assertEquals(fields,node.fields);
        for(MethodNode method:methods){ClassNode holder=new ClassNode();holder.version=node.version;holder.name=node.name;holder.superName=node.superName;holder.methods.add(method);assertArrayEquals(before.get(method),bytes(holder));}
        MethodNode getter=node.methods.stream().filter(method->method.name.equals(getThread)).findFirst().orElseThrow();
        assertEquals("()Ljava/lang/Thread;",getter.desc);assertTrue((getter.access&Opcodes.ACC_PUBLIC)!=0);assertTrue((getter.access&Opcodes.ACC_SYNTHETIC)!=0);
        assertNull(getter.visibleAnnotations);assertNull(getter.invisibleAnnotations);
        FieldInsnNode read=Arrays.stream(getter.instructions.toArray()).filter(FieldInsnNode.class::isInstance).map(FieldInsnNode.class::cast).findFirst().orElseThrow();
        assertEquals(base.name,read.owner);assertEquals(threadField,read.name);assertEquals("Ljava/lang/Thread;",read.desc);
        assertTrue(base.fields.stream().anyMatch(field->field.name.equals(read.name)&&field.desc.equals(read.desc)&&(field.access&Opcodes.ACC_PRIVATE)!=0));
        new Analyzer<>(new BasicVerifier()).analyze(node.name,getter);
        byte[] once=bytes(node);HostServerShutdownCompatibility.normalize(node,mappings);assertArrayEquals(once,bytes(node));
        ClassNode missing=copy(base);missing.fields.removeIf(field->field.name.equals(threadField));byte[] unchanged=bytes(missing);
        assertThrows(IllegalStateException.class,()->HostServerShutdownCompatibility.normalize(missing,mappings));assertArrayEquals(unchanged,bytes(missing));
    }

    @Test void resolvesTheEmittedCallOnTheFullActualServerAfterTheRealClientSideTransformer(@TempDir Path temporary) throws Throwable {
        ClassNode server=copy(base),client=copy(integrated);HostServerShutdownCompatibility.normalize(server,mappings);HostServerShutdownCompatibility.normalize(client,mappings);
        MethodInsnNode emitted=calls(method(client,stop)).stream().filter(call->call.name.equals(getThread)).findFirst().orElseThrow();
        assertEquals(base.name,emitted.owner);assertEquals("()Ljava/lang/Thread;",emitted.desc);
        String unavailable=mappings.methodFromSrg(SERVER,"func_175583_aK","()Ljava/lang/Thread;");
        ClassNode stripped;
        try(URLClassLoader forge=new URLClassLoader(new URL[]{originalForge.toUri().toURL()},getClass().getClassLoader())) {
            Class<?> side=forge.loadClass("net.minecraftforge.fml.relauncher.Side");
            Class<?> launch=forge.loadClass("net.minecraftforge.fml.relauncher.FMLLaunchHandler");
            var sideField=launch.getDeclaredField("side");sideField.setAccessible(true);
            sideField.set(null,Arrays.stream(side.getEnumConstants()).filter(value->value.toString().equals("CLIENT")).findFirst().orElseThrow());
            Object actual=forge.loadClass("net.minecraftforge.fml.common.asm.transformers.SideTransformer").getConstructor().newInstance();
            var transform=actual.getClass().getMethod("transform",String.class,String.class,byte[].class);
            ClassNode legacy=remap(server,mappings.runtimeToSrg());
            byte[] source=(byte[])transform.invoke(actual,SERVER.replace('/','.'),SERVER.replace('/','.'),bytes(legacy));
            ClassNode output=new ClassNode();new ClassReader(source).accept(output,0);stripped=remap(output,mappings.srgToRuntime());
        }
        assertTrue(base.methods.stream().anyMatch(method->method.name.equals(unavailable)&&method.desc.equals(emitted.desc)));
        assertFalse(stripped.methods.stream().anyMatch(method->method.name.equals(unavailable)&&method.desc.equals(emitted.desc)));
        assertTrue(stripped.methods.stream().anyMatch(method->method.name.equals(emitted.name)&&method.desc.equals(emitted.desc)));
        assertTrue(stripped.methods.size()>50);
        Path api=transformer.transform(originalForge,temporary.resolve("api"),mappings,true,new ForgeRuntimePatches());
        LinkedHashSet<Path> paths=new LinkedHashSet<>(List.of(api,runtime));
        Path metadata=Path.of("../build/forge-compat/fml-runtime-review/trusted-classpath.json");Assumptions.assumeTrue(Files.isRegularFile(metadata));
        for(var item:new JsonParser().parse(Files.readString(metadata)).getAsJsonArray()) {
            Path path=Path.of(item.getAsString());if(!Files.isRegularFile(path))continue;
            try(ZipFile zip=new ZipFile(path.toFile())){if(zip.getEntry("ave.class")==null&&zip.getEntry("net/minecraft/client/main/Main.class")==null&&zip.getEntry("fabric.mod.json")==null&&zip.getEntry("mcmod.info")==null)paths.add(path);}
        }
        byte[] actualServer=bytes(stripped);
        URL[] urls=paths.stream().map(path->{try{return path.toUri().toURL();}catch(Exception failure){throw new AssertionError(failure);}}).toArray(URL[]::new);
        try(URLClassLoader target=new URLClassLoader(urls,getClass().getClassLoader()){
            @Override protected Class<?> findClass(String name)throws ClassNotFoundException{if(name.equals(base.name.replace('/','.')))return defineClass(name,actualServer,0,actualServer.length);return super.findClass(name);}
        }) {
            Class<?> type=Class.forName(emitted.owner.replace('/','.'),false,target);assertSame(target,type.getClassLoader());
            assertThrows(NoSuchMethodException.class,()->MethodHandles.publicLookup().findVirtual(type,unavailable,MethodType.methodType(Thread.class)));
            var getter=MethodHandles.publicLookup().findVirtual(type,emitted.name,MethodType.fromMethodDescriptorString(emitted.desc,target));
            assertEquals(type,getter.type().parameterType(0));assertEquals(Thread.class,getter.type().returnType());
            assertThrows(NullPointerException.class,()->getter.invokeWithArguments((Object)null));
        }
    }

    @Test void performsTheActualLogoutOnTheActiveServerThreadBeforeReturningAndReapsItsWatcher() throws Exception {
        Queue<FutureTask<?>> queue=new ArrayDeque<>(); OwnPlayers players=new OwnPlayers(); players.values.add(new OwnPlayer());
        AtomicReference<Fixture> holder=new AtomicReference<>(); CountDownLatch started=new CountDownLatch(1);
        Thread worker=new Thread(()->{
            started.countDown();
            while(holder.get().running()) {
                FutureTask<?> task; synchronized(queue){task=queue.poll();}
                if(task!=null)task.run(); else Thread.onSpinWait();
            }
        },"Owned active integrated server");
        Fixture fixture=fixture(queue,worker,players); holder.set(fixture); worker.start(); assertTrue(started.await(5,TimeUnit.SECONDS));
        try { fixture.shutdown(); }
        finally { fixture.running(false); worker.join(5000); }
        assertFalse(worker.isAlive()); assertEquals(1,players.logouts); assertEquals(List.of(worker),players.threads);
        assertFalse(fixture.running()); assertEquals(1,fixture.lan.interrupts); assertTrue(queue.isEmpty()); assertNoWatcher();
    }

    @Test void runsTheActualPendingLogoutAfterWorkerTerminationWithoutDiscardingOtherTasks() throws Exception {
        Thread worker=new Thread(()->{},"Owned terminated integrated server"); worker.start(); worker.join();
        Queue<FutureTask<?>> queue=new ArrayDeque<>(); FutureTask<Void> unrelated=new FutureTask<>(()->null); queue.add(unrelated);
        OwnPlayers players=new OwnPlayers(); players.values.add(new OwnPlayer()); Fixture fixture=fixture(queue,worker,players);
        fixture.shutdown();
        assertEquals(1,players.logouts); assertEquals(List.of(Thread.currentThread()),players.threads);
        assertEquals(List.of(unrelated),new ArrayList<>(queue)); assertFalse(unrelated.isDone());
        assertFalse(fixture.running()); assertEquals(1,fixture.lan.interrupts); assertNoWatcher();
    }

    @Test void reproducesEnqueueAfterTheOriginalStoppedCheckAndCompletesOnlyAfterRealCleanup() throws Exception {
        RaceQueue queue=new RaceQueue(); OwnPlayers players=new OwnPlayers(); players.values.add(new OwnPlayer());
        CountDownLatch end=new CountDownLatch(1); AtomicInteger saves=new AtomicInteger(),unloads=new AtomicInteger();
        AtomicReference<Fixture> holder=new AtomicReference<>();
        Thread worker=new Thread(()->{
            await(end); OwnPlayer player=players.values.get(0); players.logout(player); saves.incrementAndGet(); unloads.incrementAndGet(); holder.get().stopped(true);
        },"Owned auto-stopping integrated server");
        Fixture fixture=fixture(queue,worker,players); holder.set(fixture); worker.start();
        queue.beforeAdd=()->{end.countDown();try{worker.join();}catch(InterruptedException failure){throw new AssertionError(failure);}};
        ListenableFuture<?> originalFuture=fixture.submit(()->{});
        assertEquals(Thread.State.TERMINATED,worker.getState()); assertFalse(originalFuture.isDone()); assertTrue(queue.contains(originalFuture));
        ((RunnableFuture<?>)originalFuture).run(); queue.remove(originalFuture);
        fixture.stopped(false); fixture.shutdown();
        assertEquals(1,saves.get()); assertEquals(1,unloads.get()); assertEquals(1,players.logouts);
        assertFalse(fixture.running()); assertTrue(queue.isEmpty()); assertEquals(1,fixture.lan.interrupts);
    }

    @Test void handlesConcurrentTaskCompletionAndActualWorkerExitExactlyOnceAndPreservesInterrupts() throws Exception {
        for(int iteration=0;iteration<16;iteration++) {
            CountDownLatch release=new CountDownLatch(1); AtomicInteger count=new AtomicInteger();
            Thread worker=new Thread(()->await(release),"Owned racing integrated server"); worker.start();
            ObservedFuture<Integer> future=new ObservedFuture<>(()->count.incrementAndGet()); Queue<FutureTask<?>> unused=new ArrayDeque<>();
            AtomicReference<Object> result=new AtomicReference<>(); AtomicReference<Throwable> error=new AtomicReference<>(); AtomicReference<Boolean> interrupted=new AtomicReference<>();
            Thread caller=new Thread(()->{try{result.set(HostServerShutdownCompatibility.await(future,worker,unused));interrupted.set(Thread.currentThread().isInterrupted());}catch(Throwable failure){error.set(failure);}},"Owned shutdown caller");
            caller.start(); assertTrue(future.listener.await(5,TimeUnit.SECONDS)); caller.interrupt();
            Thread completion=new Thread(()->{await(release);future.run();},"Owned external future completion"); completion.start(); release.countDown();
            caller.join(5000);worker.join(5000);completion.join(5000);
            assertFalse(caller.isAlive());assertNull(error.get());assertEquals(1,result.get());assertEquals(1,count.get());assertTrue(interrupted.get());assertNoWatcher();
        }
    }

    @Test void propagatesTheOriginalTaskFailureAndCancellationWithoutPretendingShutdownSucceeded() throws Exception {
        Thread worker=new Thread(()->{});worker.start();worker.join();
        for(Throwable error:new Throwable[]{new IllegalArgumentException("Owned task failure"),new AssertionError("Owned task error")}) {
            OwnPlayers players=new OwnPlayers();players.values.add(new OwnPlayer());players.failure=error;
            Fixture fixture=fixture(new ArrayDeque<>(),worker,players);
            InvocationTargetException result=assertThrows(InvocationTargetException.class,fixture::shutdown);
            assertTrue(result.getCause() instanceof UncheckedExecutionException||result.getCause() instanceof ExecutionError);
            assertSame(error,result.getCause().getCause());assertTrue(fixture.running());assertEquals(0,fixture.lan.interrupts);assertEquals(0,players.logouts);
        }
        ListenableFutureTask<Void> canceled=ListenableFutureTask.create(()->null);canceled.cancel(false);
        assertThrows(CancellationException.class,()->HostServerShutdownCompatibility.await(canceled,worker,new ArrayDeque<>()));
        assertNoWatcher();
    }

    public static final class OwnPlayer { }
    public static final class OwnPlayers {
        final List<OwnPlayer> values=new ArrayList<>();final List<Thread> threads=new ArrayList<>();int logouts;Throwable failure;
        public List<OwnPlayer> list(){return values;}
        public void logout(OwnPlayer player){if(failure instanceof Error error)throw error;if(failure instanceof RuntimeException error)throw error;values.remove(player);logouts++;threads.add(Thread.currentThread());}
    }
    public static final class OwnLan extends Thread { int interrupts;@Override public void interrupt(){interrupts++;super.interrupt();} }
    private static final class RaceQueue extends ArrayDeque<FutureTask<?>> { Runnable beforeAdd=()->{};@Override public boolean add(FutureTask<?> task){beforeAdd.run();return super.add(task);} }
    private static final class ObservedFuture<V> implements RunnableFuture<V>,ListenableFuture<V> {
        final ListenableFutureTask<V> delegate;final CountDownLatch listener=new CountDownLatch(1);
        ObservedFuture(Callable<V> task){delegate=ListenableFutureTask.create(task);}
        @Override public void addListener(Runnable action,Executor executor){delegate.addListener(action,executor);listener.countDown();}
        @Override public void run(){delegate.run();}@Override public boolean cancel(boolean interrupt){return delegate.cancel(interrupt);}
        @Override public boolean isCancelled(){return delegate.isCancelled();}@Override public boolean isDone(){return delegate.isDone();}
        @Override public V get()throws InterruptedException,ExecutionException{return delegate.get();}
        @Override public V get(long time,TimeUnit unit)throws InterruptedException,ExecutionException,TimeoutException{return delegate.get(time,unit);}
    }
    private record Fixture(Object server,Class<?> type,OwnLan lan) {
        void shutdown()throws Exception{type.getMethod("shutdown").invoke(server);}
        ListenableFuture<?> submit(Runnable task)throws Exception{return (ListenableFuture<?>)type.getMethod("submitRunnable",Runnable.class).invoke(server,task);}
        boolean running(){return (Boolean)field("running",null,false);}
        void running(boolean value){field("running",value,true);}
        void stopped(boolean value){field("stopped",value,true);}
        Object field(String name,Object value,boolean set){try{var field=type.getSuperclass().getDeclaredField(name);field.setAccessible(true);if(set)field.set(server,value);return field.get(server);}catch(Exception failure){throw new AssertionError(failure);}}
    }

    private static Fixture fixture(Queue<FutureTask<?>> queue,Thread worker,OwnPlayers players)throws Exception {
        ClassNode parent=copy(base),child=copy(integrated),task=copy(logout);HostServerShutdownCompatibility.normalize(parent,mappings);HostServerShutdownCompatibility.normalize(child,mappings);
        Set<String> methods=Set.of(stop,submitCallable,submitRunnable,sameThread,stopped,getThread,getPlayers);
        parent.methods.removeIf(method->!methods.contains(method.name));
        parent.fields.removeIf(field->!Set.of(queueField,threadField,runningField,stoppedField,playersField).contains(field.name));
        parent.superName="java/lang/Object";parent.interfaces.clear();parent.access=Opcodes.ACC_PUBLIC|Opcodes.ACC_SUPER;parent.signature=null;parent.innerClasses.clear();
        child.methods.removeIf(method->!method.name.equals(stop));child.fields.removeIf(field->!field.name.equals(lanField));child.interfaces.clear();child.signature=null;child.innerClasses.clear();task.innerClasses.clear();
        String packageName="fixture/Stop"+sequence++,ownBase=packageName+"Base",ownChild=packageName+"Integrated",ownTask=packageName+"Logout";
        Map<String,String> types=Map.of(base.name,ownBase,integrated.name,ownChild,logout.name,ownTask,
            mappings.classFromSrg("net/minecraft/server/management/ServerConfigurationManager"),Type.getInternalName(OwnPlayers.class),
            mappings.classFromSrg("net/minecraft/entity/player/EntityPlayerMP"),Type.getInternalName(OwnPlayer.class),
            mappings.classFromSrg("net/minecraft/client/multiplayer/ThreadLanServerPing"),Type.getInternalName(OwnLan.class));
        Map<String,String> names=new HashMap<>();names.put(stop,"shutdown");names.put(submitCallable,"submitCallable");names.put(submitRunnable,"submitRunnable");names.put(sameThread,"sameThread");names.put(stopped,"stopped");names.put(getThread,"getThread");names.put(getPlayers,"players");
        names.put(mappings.methodFromSrg("net/minecraft/server/management/ServerConfigurationManager","func_181057_v","()Ljava/util/List;"),"list");
        names.put(mappings.methodFromSrg("net/minecraft/server/management/ServerConfigurationManager","func_72367_e","(Lnet/minecraft/entity/player/EntityPlayerMP;)V"),"logout");
        Map<String,String> fields=Map.of(queueField,"queue",threadField,"worker",runningField,"running",stoppedField,"stopped",playersField,"players",lanField,"lan");
        Remapper remap=new Remapper(){@Override public String map(String name){return types.getOrDefault(name,name);}@Override public String mapMethodName(String owner,String name,String descriptor){return names.getOrDefault(name,name);}@Override public String mapFieldName(String owner,String name,String descriptor){return fields.getOrDefault(name,name);}};
        parent=remap(parent,remap);child=remap(child,remap);task=remap(task,remap);
        String manager=Type.getDescriptor(OwnPlayers.class),lan=Type.getDescriptor(OwnLan.class),args="(Ljava/util/Queue;Ljava/lang/Thread;"+manager+lan+")V";
        MethodNode constructor=new MethodNode(Opcodes.ACC_PUBLIC,"<init>",args,null,null);
        constructor.visitVarInsn(Opcodes.ALOAD,0);constructor.visitMethodInsn(Opcodes.INVOKESPECIAL,"java/lang/Object","<init>","()V",false);
        for(int i=0;i<3;i++){constructor.visitVarInsn(Opcodes.ALOAD,0);constructor.visitVarInsn(Opcodes.ALOAD,i+1);constructor.visitFieldInsn(Opcodes.PUTFIELD,ownBase,new String[]{"queue","worker","players"}[i],new String[]{"Ljava/util/Queue;","Ljava/lang/Thread;",manager}[i]);}
        constructor.visitVarInsn(Opcodes.ALOAD,0);constructor.visitInsn(Opcodes.ICONST_1);constructor.visitFieldInsn(Opcodes.PUTFIELD,ownBase,"running","Z");constructor.visitInsn(Opcodes.RETURN);constructor.visitMaxs(2,5);parent.methods.add(constructor);
        MethodNode init=new MethodNode(Opcodes.ACC_PUBLIC,"<init>",args,null,null);for(int i=0;i<5;i++)init.visitVarInsn(Opcodes.ALOAD,i);init.visitMethodInsn(Opcodes.INVOKESPECIAL,ownBase,"<init>",args,false);init.visitVarInsn(Opcodes.ALOAD,0);init.visitVarInsn(Opcodes.ALOAD,4);init.visitFieldInsn(Opcodes.PUTFIELD,ownChild,"lan",lan);init.visitInsn(Opcodes.RETURN);init.visitMaxs(5,5);child.methods.add(init);
        Map<String,byte[]> classes=Map.of(ownBase.replace('/','.'),bytes(parent),ownChild.replace('/','.'),bytes(child),ownTask.replace('/','.'),bytes(task));
        ClassLoader loader=new ClassLoader(HostServerShutdownCompatibilityTest.class.getClassLoader()){@Override protected Class<?> findClass(String name)throws ClassNotFoundException{byte[] value=classes.get(name);if(value==null)return super.findClass(name);return defineClass(name,value,0,value.length);}};
        Class<?> type=loader.loadClass(ownChild.replace('/','.'));OwnLan lanThread=new OwnLan();
        return new Fixture(type.getConstructor(Queue.class,Thread.class,OwnPlayers.class,OwnLan.class).newInstance(queue,worker,players,lanThread),type,lanThread);
    }

    private static void await(CountDownLatch latch){try{if(!latch.await(5,TimeUnit.SECONDS))throw new AssertionError("Owned lifecycle gate timed out");}catch(InterruptedException failure){throw new AssertionError(failure);}}
    private static void assertNoWatcher(){assertTrue(Thread.getAllStackTraces().keySet().stream().noneMatch(thread->thread.isAlive()&&thread.getName().equals("Arcadium server shutdown join")));}
    private static ClassNode read(Path jar,String name)throws Exception{ClassNode node=new ClassNode();try(ZipFile zip=new ZipFile(jar.toFile());var input=zip.getInputStream(zip.getEntry(name+".class"))){new ClassReader(input.readAllBytes()).accept(node,0);}return node;}
    private static ClassNode copy(ClassNode node){ClassNode result=new ClassNode();node.accept(result);return result;}
    private static ClassNode remap(ClassNode node,Remapper remapper){ClassNode result=new ClassNode();node.accept(new ClassRemapper(result,remapper));return result;}
    private static MethodNode method(ClassNode node,String name){return node.methods.stream().filter(method->method.name.equals(name)&&method.desc.equals("()V")).findFirst().orElseThrow();}
    private static List<MethodInsnNode> calls(MethodNode method){return Arrays.stream(method.instructions.toArray()).filter(MethodInsnNode.class::isInstance).map(MethodInsnNode.class::cast).toList();}
    private static byte[] bytes(ClassNode node){ClassWriter writer=new ClassWriter(0);node.accept(writer);return writer.toByteArray();}
}
