// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.qa;

import com.google.gson.GsonBuilder;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.IntBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.particle.ArcadiumParticle065Fixture;
import net.minecraft.client.entity.particle.Particle;
import net.minecraft.client.render.vertex.BufferBuilder;
import net.minecraft.client.render.vertex.DefaultVertexFormat;
import org.lwjgl.opengl.*;

public final class ParticlePackets065Qa {
    private static boolean shadow, done;
    private static int ticks, verifiedPackets;
    private static Class<?> gpu;
    private static Method quads;

    private ParticlePackets065Qa() { }
    public static boolean enabled() { return System.getProperty("arcadium.particles065.output") != null; }
    public static boolean preserveNative() { return shadow; }

    public static void tick() {
        if (done || ++ticks < 80) return;
        done = true;
        Map<String, Object> result;
        try { result = verify(); result.put("passed", true); }
        catch (Throwable error) {
            error.printStackTrace();
            result = new LinkedHashMap<>();
            result.put("passed", false);
            result.put("error", error.toString());
        }
        try {
            Path output = Path.of(System.getProperty("arcadium.particles065.output"));
            Files.createDirectories(output.toAbsolutePath().getParent());
            Files.writeString(output, new GsonBuilder().setPrettyPrinting().create().toJson(result));
        } catch (Exception error) { error.printStackTrace(); }
        Minecraft.getInstance().stop();
    }

    public static boolean verifyOriginal(BufferBuilder builder) {
        if (!shadow) return false;
        try {
            Object group = groups().remove(builder);
            if (group == null) return false;
            IntBuffer packets = (IntBuffer) field(group, "packets");
            int[] offsets = (int[]) field(group, "offsets"), nativeWords = ParticleNative065Reference.words(builder);
            int count = (int) field(group, "count");
            for (int i = 0; i < count; i++) {
                int[] packet = new int[16];
                packets.duplicate().position(i * 16).limit(i * 16 + 16).get(packet);
                int from = offsets[i] * 7;
                require(from >= 0 && from + 28 <= nativeWords.length, "Captured native particle offset outside buffer");
                int[] expected = Arrays.copyOfRange(nativeWords, from, from + 28);
                int[] actual = (int[]) quads.invoke(null, packet, 0);
                ParticleNative065Reference.compare(expected, actual, "captured particle " + i);
                verifiedPackets++;
            }
            return true;
        } catch (ReflectiveOperationException error) { throw new IllegalStateException(error); }
    }

    public static Map<String, Object> verify() throws Exception {
        verifiedPackets = 0;
        gpu = Class.forName("io.github.arcadiumclient.runtime.client.optimizations.GpuParticles");
        quads = Class.forName("io.github.arcadiumclient.runtime.client.optimizations.ParticlePacketMath").getMethod("quads", int[].class, int.class);
        Class<?> config = Class.forName("io.github.arcadiumclient.runtime.client.features.BetaOptimizationsConfig");
        boolean oldEnabled = (boolean) config.getMethod("getGpuParticles").invoke(null);
        boolean oldRecipes = (boolean) config.getMethod("getRetainedParticleRecipes").invoke(null);
        int oldMinimum = (int) config.getMethod("getMinimumGpuParticles").invoke(null);
        int oldProgram = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        IntBuffer oldViewport = org.lwjgl.BufferUtils.createIntBuffer(4);
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, oldViewport);
        double cameraX = Particle.cameraX, cameraY = Particle.cameraY, cameraZ = Particle.cameraZ;
        Map<String, Object> report = new LinkedHashMap<>();
        try {
            GL20.glUseProgram(0);
            config.getMethod("setGpuParticles", boolean.class).invoke(null, true);
            config.getMethod("setMinimumGpuParticles", int.class).invoke(null, 1);
            call("close");
            warm();
            int[] original = reference(640);
            require(verifiedPackets == 640, "Capture-preserving mixin did not verify all original particles");
            report.put("capturedNativePackets", verifiedPackets);
            report.put("capturedNativeWords", verifiedPackets * 28);
            require((int) field(gpu, "program") != 0, "Production particle shader did not initialize");
            BufferBuilder accelerated = render(640, null);
            require(accelerated.getVertexCount() == 0, "GPU capture left native vertices in an eligible batch");
            Object group = groups().get(accelerated);
            require(group != null && (int) field(group, "count") == 640, "GPU particle batch lost packets");
            require(((int[]) field(group, "offsets")).length >= 640, "Particle packet buffer did not grow");
            long drawsBefore = (long) field(gpu, "draws");
            gpuDraw(accelerated);
            require((long) field(gpu, "draws") == drawsBefore + 1, "Production GPU draw counter did not advance");
            require((int) field(group, "count") == 0, "GPU draw retained packets");
            report.put("productionGpuDraw", true);
            report.put("packetBufferGrowth", 640);
            report.put("mixedBatchOrdering", mixed(original));
            report.put("customShaderFallback", customShader(original));
            report.put("unsafeBuilderRejections", rejectedBuilders());
            report.put("retainedRecipes", recipes(config));
            BufferBuilder failed = render(640, null);
            gpu.getMethod("disable", Throwable.class).invoke(null, new IllegalStateException("QA: injected particle failure"));
            require(!(boolean) call("draw", new Class<?>[]{BufferBuilder.class}, failed), "Failed GPU path consumed the native draw");
            ParticleNative065Reference.compare(original, ParticleNative065Reference.words(failed), "failure recovery order");
            require((int) field(groups().get(failed), "count") == 0, "Failure recovery retained packets");
            report.put("failureRecoveryPackets", 640);
            call("end");
            int program = (int) field(gpu, "program"), vao = (int) field(gpu, "vao"), instances = (int) field(gpu, "instances");
            GL20.glUseProgram(0);
            call("close");
            require(groups().isEmpty(), "Particle close retained buffer groups");
            require(!GL20.glIsProgram(program) && !GL30.glIsVertexArray(vao) && !GL15.glIsBuffer(instances), "Particle close retained GPU resources");
            warm();
            GL11.glViewport(0, 0, 512, 288);
            gpuDraw(render(640, null));
            call("end");
            require(GL11.glGetError() == GL11.GL_NO_ERROR, "OpenGL error after particle lifecycle verification");
            report.put("closeDeletesGpuResources", true);
            report.put("drawAfterReinitializeAndViewportResize", true);
            report.put("renderer", GL11.glGetString(GL11.GL_RENDERER));
            report.put("glVersion", GL11.glGetString(GL11.GL_VERSION));
            return report;
        } finally {
            shadow = false;
            GL20.glUseProgram(0);
            call("close");
            config.getMethod("setGpuParticles", boolean.class).invoke(null, oldEnabled);
            config.getMethod("setRetainedParticleRecipes", boolean.class).invoke(null, oldRecipes);
            config.getMethod("setMinimumGpuParticles", int.class).invoke(null, oldMinimum);
            GL20.glUseProgram(oldProgram);
            GL11.glViewport(oldViewport.get(0), oldViewport.get(1), oldViewport.get(2), oldViewport.get(3));
            Particle.cameraX = cameraX;
            Particle.cameraY = cameraY;
            Particle.cameraZ = cameraZ;
        }
    }

    private static void warm() throws Exception {
        set("observed", 1024);
        call("begin", new Class<?>[]{boolean.class}, true);
        require((boolean) field(gpu, "scopeEnabled"), "Production particle scope remained disabled");
    }

    private static int[] reference(int count) throws Exception {
        shadow = true;
        try {
            BufferBuilder original = render(count, null);
            int[] words = ParticleNative065Reference.words(original);
            require(words.length == count * 28, "Original renderer lost reference particles");
            require(!(boolean) call("draw", new Class<?>[]{BufferBuilder.class}, original), "Reference pass was consumed by GPU");
            return words;
        } finally { shadow = false; }
    }

    private static BufferBuilder render(int count, int[] nativeWords) {
        BufferBuilder buffer = new BufferBuilder(128);
        buffer.begin(GL11.GL_QUADS, DefaultVertexFormat.PARTICLE);
        Random random = new Random(0x65C0FFEE);
        for (int i = 0; i < count; i++) {
            Particle.cameraX = 29_999_900 + i * .013;
            Particle.cameraY = 70 + i * .007;
            Particle.cameraZ = -29_999_900 + i * .021;
            ArcadiumParticle065Fixture particle = new ArcadiumParticle065Fixture();
            particle.configure(i, random);
            if (nativeWords != null && (i == 0 || i == 3 || i == 5)) buffer.vertices(Arrays.copyOfRange(nativeWords, i * 28, i * 28 + 28));
            else particle.render(buffer, null, i % 7 / 6f, .83f, -.37f, .24f, -.19f, .13f);
        }
        buffer.end();
        return buffer;
    }

    private static int mixed(int[] original) throws Exception {
        BufferBuilder mixed = render(8, original);
        require(mixed.getVertexCount() == 12, "Mixed reference did not retain the three native quads");
        require(!(boolean) call("draw", new Class<?>[]{BufferBuilder.class}, mixed), "Mixed batch did not use native replay");
        ParticleNative065Reference.compare(Arrays.copyOf(original, 8 * 28), ParticleNative065Reference.words(mixed), "mixed native/captured order");
        require(!(boolean) call("draw", new Class<?>[]{BufferBuilder.class}, mixed), "Consumed mixed batch replayed twice");
        return 8;
    }

    private static int customShader(int[] original) throws Exception {
        BufferBuilder builder = render(8, null);
        int program = (int) field(gpu, "program");
        GL20.glUseProgram(program);
        try {
            require(!(boolean) call("draw", new Class<?>[]{BufferBuilder.class}, builder), "Custom shader did not force native replay");
            require(GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM) == program, "Native fallback changed the active shader");
            ParticleNative065Reference.compare(Arrays.copyOf(original, 8 * 28), ParticleNative065Reference.words(builder), "custom shader fallback");
        } finally { GL20.glUseProgram(0); }
        return 8;
    }

    private static int rejectedBuilders() throws Exception {
        Class<?>[] types = new Class<?>[20];
        types[0] = Particle.class;
        types[1] = BufferBuilder.class;
        Arrays.fill(types, 2, 19, float.class);
        types[19] = int.class;
        Method queue = gpu.getMethod("queue", types);
        for (int i = 0; i < 3; i++) {
            BufferBuilder builder = new BufferBuilder(128);
            builder.begin(i == 2 ? GL11.GL_TRIANGLES : GL11.GL_QUADS, DefaultVertexFormat.PARTICLE);
            if (i == 0) builder.offset(.5, 0, 0);
            if (i == 1) builder.uncolored();
            boolean queued = (boolean) queue.invoke(null, new ArcadiumParticle065Fixture(), builder,
                    1f, 2f, 3f, .2f, 0f, 1f, 0f, 1f, 1f, .5f, .25f, .7f, 1f, 0f, 0f, 0f, 1f, 0x00E00020);
            require(!queued, "Unsafe native builder state was captured: " + i);
            builder.end();
        }
        return 3;
    }

    private static Map<String, Object> recipes(Class<?> config) throws Exception {
        Method option = config.getMethod("setRetainedParticleRecipes", boolean.class);
        option.invoke(null, true);
        var mc = Minecraft.getInstance();
        var screen = mc.screen;
        int checked = 0;
        try {
            mc.screen = null;
            var constructor = Particle.class.getDeclaredConstructor(net.minecraft.world.World.class, double.class, double.class, double.class);
            constructor.setAccessible(true);
            var queue = Arrays.stream(gpu.getMethods()).filter(method -> method.getName().equals("queue")).findFirst().orElseThrow();
            var recipe = gpu.getMethod("tryRecipe", Particle.class, BufferBuilder.class, float.class, float.class, float.class, float.class, float.class, float.class);
            Random random = new Random(0x650CAE);
            for (int i = 0; i < 96; i++) {
                ParticleNative065Reference.NativeParticle reference = new ParticleNative065Reference.NativeParticle();
                reference.configure(i, random);
                Particle actual = constructor.newInstance(null, 0d, 0d, 0d);
                actual.lastX=reference.lastX; actual.lastY=reference.lastY; actual.lastZ=reference.lastZ;
                actual.x=reference.x; actual.y=reference.y; actual.z=reference.z;
                ArcadiumParticle065Fixture.copyAppearance(reference,actual);
                // The existing native oracle covers custom render-time scaling; recipes only cover the stable base appearance.
                reference.stableAppearance();
                BufferBuilder seed=new BufferBuilder(128); seed.begin(GL11.GL_QUADS,DefaultVertexFormat.PARTICLE);
                int[] packet=reference.packet(0,.83f,-.37f,.24f,-.19f,.13f,0);
                float[] colors=ArcadiumParticle065Fixture.colors(actual);
                require((boolean)queue.invoke(null,actual,seed,Float.intBitsToFloat(packet[0]),Float.intBitsToFloat(packet[1]),Float.intBitsToFloat(packet[2]),Float.intBitsToFloat(packet[3]),
                        Float.intBitsToFloat(packet[4]),Float.intBitsToFloat(packet[5]),Float.intBitsToFloat(packet[6]),Float.intBitsToFloat(packet[7]),
                        colors[0],colors[1],colors[2],colors[3],.83f,-.37f,.24f,-.19f,.13f,packet[9]),"Recipe seed rejected");
                seed.end(); groups().remove(seed);
                if(i==0){
                    option.invoke(null,false);reportRecipeFallback(recipe,actual,"disabled");option.invoke(null,true);
                    mc.screen=screen;reportRecipeFallback(recipe,actual,"GUI open");mc.screen=null;
                    reportRecipeFallback(recipe,reference,"custom subclass");
                }
                for(float delta : new float[]{0,.001f,.25f,.5f,.875f,.999f,1}) {
                    Particle.cameraX+=.017; Particle.cameraY-=.013; Particle.cameraZ+=.019;
                    BufferBuilder expected=new BufferBuilder(128); expected.begin(GL11.GL_QUADS,DefaultVertexFormat.PARTICLE);
                    int oldDepth=(int)field(gpu,"depth"); set("depth",0);
                    try{reference.render(expected,null,delta,.83f,-.37f,.24f,-.19f,.13f);}finally{set("depth",oldDepth);}
                    expected.end();
                    BufferBuilder output=new BufferBuilder(128);output.begin(GL11.GL_QUADS,DefaultVertexFormat.PARTICLE);
                    require((boolean)recipe.invoke(null,actual,output,delta,.83f,-.37f,.24f,-.19f,.13f),"Stable recipe was not reused");output.end();
                    Object group=groups().remove(output);IntBuffer data=(IntBuffer)field(group,"packets");int[] words=new int[16];data.duplicate().position(0).get(words);
                    ParticleNative065Reference.compare(ParticleNative065Reference.words(expected),(int[])quads.invoke(null,words,0),"retained recipe "+i+"/"+delta);checked++;
                }
                call("tick");
                BufferBuilder expired=new BufferBuilder(128);expired.begin(GL11.GL_QUADS,DefaultVertexFormat.PARTICLE);
                require(!(boolean)recipe.invoke(null,actual,expired,.5f,.83f,-.37f,.24f,-.19f,.13f),"Recipe survived a game tick");expired.end();
            }
            Map<String,Object> liveStyles=liveOverflowStyles(constructor,queue,recipe);
            option.invoke(null,false);
            reportRecipeFallback(recipe, constructor.newInstance(null,0d,0d,0d), "disabled");
            return Map.of("sameTickNativeVertexComparisons",checked,"cameraAndPartialTickInterpolation",true,"invalidatedAfterTicks",96,"customParticlesKeepNativePreparation",true,"liveOverflowStyles",liveStyles);
        } finally { mc.screen=screen;option.invoke(null,false); }
    }

    private static Map<String,Object> liveOverflowStyles(java.lang.reflect.Constructor<Particle> constructor,Method queue,Method recipe)throws Exception{
        Particle particle=constructor.newInstance(null,0d,0d,0d);
        Class<?> configType=Class.forName("org.polyfrost.overflowparticles.client.config.ParticleConfig");
        var configConstructor=configType.getConstructors()[0];
        Object[] types=configConstructor.getParameterTypes()[1].getEnumConstants();
        require(types!=null&&types.length>0,"Overflow particle type registry unavailable");
        Object type=Arrays.stream(types).filter(value->((Enum<?>)value).name().contains("SPELL")).findFirst().orElse(types[0]);
        Object style=configConstructor.newInstance("Arcadium Recipe QA",type);
        configType.getMethod("setFade",boolean.class).invoke(style,false);
        configType.getMethod("setFadeStart",float.class).invoke(style,.2f);
        Object color=configType.getMethod("getColor").invoke(style);
        Method chroma=color.getClass().getMethod("setChroma",boolean.class);
        chroma.invoke(color,false);
        Field nativeConfig=Particle.class.getDeclaredField("overflowparticles$config");
        Field nativeResolved=Particle.class.getDeclaredField("overflowparticles$configResolved");
        nativeConfig.setAccessible(true);nativeResolved.setAccessible(true);
        Object previous=nativeConfig.get(particle);boolean resolved=nativeResolved.getBoolean(particle);
        try{
            nativeConfig.set(particle,style);nativeResolved.setBoolean(particle,true);
            Class<?> data=Class.forName("org.polyfrost.overflowparticles.mixin.client.Mixin_AccessParticleData");
            Method age=data.getMethod("getAge"),lifetime=data.getMethod("getLifetime");
            nativeIntField(particle,age).setInt(particle,7);nativeIntField(particle,lifetime).setInt(particle,10);
            Class<?> appearance=Class.forName("org.polyfrost.overflowparticles.mixin.client.Mixin_AccessBillboardParticleData");
            appearance.getMethod("overflowparticles$setAlpha",float.class).invoke(particle,.8f);
            require(seedStyle(queue,particle),"Stable Overflow style did not seed a recipe");
            long epoch=(long)field(gpu,"recipeEpoch");
            configType.getMethod("setFade",boolean.class).invoke(style,true);
            reportRecipeFallback(recipe,particle,"Overflow fade enabled during the same tick");
            require((long)field(gpu,"recipeEpoch")==epoch,"Fade test advanced the game tick");
            Method fade=Arrays.stream(net.minecraft.client.ParticleManager.class.getDeclaredMethods())
                    .filter(method->method.getName().contains("applyFade")&&java.lang.reflect.Modifier.isStatic(method.getModifiers())
                            &&Arrays.equals(method.getParameterTypes(),new Class<?>[]{Particle.class})).findFirst().orElseThrow();
            fade.setAccessible(true);
            fade.invoke(null,particle);float alpha1=particle.getAlpha();
            fade.invoke(null,particle);float alpha2=particle.getAlpha();
            require(Math.abs(alpha1-.3f)<.00001&&Math.abs(alpha2-.1125f)<.00001,"Bundled native Overflow fade did not evolve each render: "+alpha1+", "+alpha2);
            require(seedStyle(queue,particle),"Fading particle lost normal GPU capture");
            require((long)particle.getClass().getMethod("arcadium$recipeEpoch").invoke(particle)!=epoch,"Fading style seeded a retained recipe");
            configType.getMethod("setFade",boolean.class).invoke(style,false);
            reportRecipeFallback(recipe,particle,"Stale pre-fade recipe after disabling fade");
            require(seedStyle(queue,particle),"Post-fade appearance did not seed afresh");
            BufferBuilder output=new BufferBuilder(128);output.begin(GL11.GL_QUADS,DefaultVertexFormat.PARTICLE);
            require((boolean)recipe.invoke(null,particle,output,.5f,1f,1f,0f,0f,0f),"Fresh post-fade recipe was not reused");output.end();
            Object group=groups().remove(output);int packed=((IntBuffer)field(group,"packets")).get(8);
            require((packed>>>24)==(int)(alpha2*255),"Post-fade recipe restored stale alpha");
            chroma.invoke(color,true);
            reportRecipeFallback(recipe,particle,"Overflow chroma enabled during the same tick");
            require(!seedStyle(queue,particle),"Chroma particle bypassed native per-vertex color");
            chroma.invoke(color,false);
            reportRecipeFallback(recipe,particle,"Stale pre-chroma recipe after disabling chroma");
            require(seedStyle(queue,particle),"Post-chroma appearance did not seed afresh");
            require((long)field(gpu,"recipeEpoch")==epoch,"Live-style transition tests advanced the game tick");
            return Map.of("sameTickFadeAndChromaTransitions",true,"nativeAlphaAfterFirstRender",alpha1,"nativeAlphaAfterSecondRender",alpha2,
                    "fadeCaptureRetainsNativePreparation",true,"chromaKeepsNativeColor",true,"staleRecipeInvalidated",true,"freshPostFadeAlpha",packed>>>24);
        }finally{chroma.invoke(color,false);nativeConfig.set(particle,previous);nativeResolved.setBoolean(particle,resolved);}
    }

    private static Field nativeIntField(Particle particle,Method getter)throws Exception{
        for(Field field:Particle.class.getDeclaredFields())if(field.getType()==int.class&&!java.lang.reflect.Modifier.isStatic(field.getModifiers())){
            field.setAccessible(true);int previous=field.getInt(particle);boolean match;
            try{field.setInt(particle,-650065);match=(int)getter.invoke(particle)==-650065;}finally{field.setInt(particle,previous);}
            if(match)return field;
        }
        throw new IllegalStateException("Native particle field for "+getter.getName()+" not found");
    }

    private static boolean seedStyle(Method queue,Particle particle)throws Exception{
        BufferBuilder builder=new BufferBuilder(128);builder.begin(GL11.GL_QUADS,DefaultVertexFormat.PARTICLE);
        try{return (boolean)queue.invoke(null,particle,builder,1f,2f,3f,.2f,0f,1f,0f,1f,1f,.5f,.25f,particle.getAlpha(),1f,1f,0f,0f,0f,0x00E00020);}
        finally{builder.end();groups().remove(builder);}
    }

    private static void reportRecipeFallback(Method method,Particle particle,String reason) throws Exception {
        BufferBuilder buffer=new BufferBuilder(128);buffer.begin(GL11.GL_QUADS,DefaultVertexFormat.PARTICLE);
        require(!(boolean)method.invoke(null,particle,buffer,.5f,1f,1f,0f,0f,0f),"Recipe did not fall back: "+reason);buffer.end();
    }

    private static void gpuDraw(BufferBuilder builder) throws Exception {
        int vao = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING), buffer = GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);
        int program = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM), unit = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        boolean discard = GL11.glIsEnabled(GL30.GL_RASTERIZER_DISCARD);
        GL11.glEnable(GL30.GL_RASTERIZER_DISCARD);
        try {
            require((boolean) call("draw", new Class<?>[]{BufferBuilder.class}, builder), "Eligible particle batch did not draw on GPU");
            require(GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING) == vao && GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING) == buffer
                    && GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM) == program && GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE) == unit,
                    "Production particle draw changed caller GL state");
        } finally { if (!discard) GL11.glDisable(GL30.GL_RASTERIZER_DISCARD); }
    }

    @SuppressWarnings("unchecked")
    private static Map<BufferBuilder, Object> groups() throws ReflectiveOperationException { return (Map<BufferBuilder, Object>) field(gpu, "groups"); }
    private static Object field(Object owner, String name) throws ReflectiveOperationException {
        Field field = (owner instanceof Class<?> type ? type : owner.getClass()).getDeclaredField(name);
        field.setAccessible(true);
        return field.get(owner instanceof Class<?> ? null : owner);
    }
    private static void set(String name, Object value) throws ReflectiveOperationException {
        Field field = gpu.getDeclaredField(name);
        field.setAccessible(true);
        field.set(null, value);
    }
    private static Object call(String name) throws Exception { return call(name, new Class<?>[0]); }
    private static Object call(String name, Class<?>[] types, Object... arguments) throws Exception { return gpu.getMethod(name, types).invoke(null, arguments); }
    private static void require(boolean condition, String message) { ParticleNative065Reference.require(condition, message); }
}
