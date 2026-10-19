package io.github.arcadiumclient.forgecompat;

import com.bawnorton.mixinsquared.MixinSquaredBootstrap;
import com.bawnorton.mixinsquared.adjuster.MixinAnnotationAdjusterRegistrar;
import com.bawnorton.mixinsquared.adjuster.tools.AdjustableAnnotationNode;
import com.bawnorton.mixinsquared.api.MixinAnnotationAdjuster;
import io.github.arcadiumclient.forgecompat.mapping.ForgeMappings;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class HostMixinCompatibility implements MixinAnnotationAdjuster {
    private static final String FONT_MIXIN = "dev.rdh.argentum.mixin.features.font.TextRendererMixin";
    private static final String LIGHTMAP_MIXIN = "dev.rdh.sarcio.mixin.bugfix.render.GameRendererMixin";
    private static final String FONT = "net/minecraft/client/gui/FontRenderer";
    private static final String BUFFER = "net/minecraft/client/renderer/WorldRenderer";
    private static final String TESSELLATOR = "net/minecraft/client/renderer/Tessellator";
    private static final String GL = "net/minecraft/client/renderer/GlStateManager";
    private static final String FORMAT = "net/minecraft/client/renderer/vertex/VertexFormat";
    private static final String CONDITION = "Lcom/llamalad7/mixinextras/injector/v2/WrapWithCondition;";
    private static final String OPERATION = "Lcom/llamalad7/mixinextras/injector/wrapoperation/WrapOperation;";
    private record FontRule(String descriptor, String annotation, List<String> methods, List<String> targets, String replacement) {}
    private record Relocation(String mixin, List<String> targets, String handler, String descriptor, String annotation,
                              List<String> methods, List<String> replacement, List<String> points) {}
    private record CameraRule(String mixin, String target, String handler, String descriptor, List<String> methods) {}
    private static boolean installed;
    private final String font, renderer, lightmap, potionCheck, world, horizon, providerHorizon;
    private final Map<String, FontRule> fonts;
    private final List<Relocation> relocations;
    private final List<CameraRule> cameras;
    private final String vanillaCamera, forgeCamera;

    public static synchronized void install(ForgeMappings mappings) {
        if (installed) return;
        MixinSquaredBootstrap.init();
        MixinAnnotationAdjusterRegistrar.register(new HostMixinCompatibility(mappings));
        MixinAnnotationAdjusterRegistrar.register(new HostGuiBackgroundCompatibility(mappings));
        MixinAnnotationAdjusterRegistrar.register(new HostCreativeTabCompatibility(mappings));
        MixinAnnotationAdjusterRegistrar.register(new HostCameraCompatibility(mappings));
        MixinAnnotationAdjusterRegistrar.register(new HostLocalizationCompatibility(mappings));
        MixinSquaredBootstrap.reOrderExtensions();
        installed = true;
    }

    HostMixinCompatibility(ForgeMappings mappings) {
        font = mappings.classFromSrg(FONT);
        world = mappings.classFromSrg("net/minecraft/world/World");
        horizon = mappings.methodFromSrg("net/minecraft/world/World", "func_72919_O", "()D");
        providerHorizon = "L" + mappings.classFromSrg("net/minecraft/world/WorldProvider") + ";getHorizon()D";
        renderer = mappings.classFromSrg("net/minecraft/client/renderer/EntityRenderer");
        lightmap = mappings.methodFromSrg("net/minecraft/client/renderer/EntityRenderer", "func_78472_g", "(F)V");
        potionCheck = selector(mappings, "net/minecraft/client/entity/EntityPlayerSP", "func_70644_a", "(Lnet/minecraft/potion/Potion;)Z");
        String renderText = method(mappings, FONT, "func_180455_b", "(Ljava/lang/String;FFIZ)I");
        String renderCharacters = method(mappings, FONT, "func_78255_a", "(Ljava/lang/String;Z)V");
        String buffer = "L" + mappings.classFromSrg(BUFFER) + ";", tessellator = "L" + mappings.classFromSrg(TESSELLATOR) + ";";
        String format = "L" + mappings.classFromSrg(FORMAT) + ";";
        List<String> characters = List.of(renderCharacters);
        fonts = Map.of(
            "argentum$captureColor", new FontRule("(FFFF)Z", CONDITION, List.of(renderText, renderCharacters),
                List.of(selector(mappings, GL, "func_179131_c", "(FFFF)V")), "setColor(FFFF)V"),
            "argentum$decorationBuffer", new FontRule("(" + tessellator + "Lcom/llamalad7/mixinextras/injector/wrapoperation/Operation;)" + buffer,
                OPERATION, characters, List.of(selector(mappings, TESSELLATOR, "func_178180_c", "()L" + BUFFER + ";")), "doDraw(F)V"),
            "argentum$beginDecorations", new FontRule("(" + buffer + "I" + format + ")Z", CONDITION, characters,
                List.of(selector(mappings, BUFFER, "func_181668_a", "(IL" + FORMAT + ";)V")), "doDraw(F)V"),
            "argentum$colorDecoration", new FontRule("(" + buffer + ")Z", CONDITION, characters,
                List.of(selector(mappings, BUFFER, "func_181675_d", "()V")), "doDraw(F)V"),
            "argentum$deferDecorations", new FontRule("(" + tessellator + ")Z", CONDITION, characters,
                List.of(selector(mappings, TESSELLATOR, "func_78381_a", "()V")), "doDraw(F)V"),
            "argentum$deferTextureState", new FontRule("()Z", CONDITION, characters,
                List.of(selector(mappings, GL, "func_179090_x", "()V"), selector(mappings, GL, "func_179098_w", "()V")), "doDraw(F)V"));
        relocations = relocations(mappings);
        String transforms = "net/minecraft/client/renderer/block/model/ItemCameraTransforms";
        String transform = transforms + "$TransformType";
        vanillaCamera = selector(mappings, transforms, "func_181689_a", "(L" + transform + ";)V");
        forgeCamera = "Lnet/minecraftforge/client/ForgeHooksClient;handleCameraTransforms" + mappings.descriptorFromSrg(
            "(Lnet/minecraft/client/resources/model/IBakedModel;L" + transform + ";)Lnet/minecraft/client/resources/model/IBakedModel;");
        String renderItem = "net/minecraft/client/renderer/entity/RenderItem", renderEntityItem = "net/minecraft/client/renderer/entity/RenderEntityItem";
        String stack = "Lnet/minecraft/item/ItemStack;", model = "Lnet/minecraft/client/resources/model/IBakedModel;";
        String ci = "Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;";
        cameras = List.of(
            new CameraRule("org.polyfrost.glintcolorizer.mixin.ItemEntityRendererMixin_HandleCameraTransforms", mappings.classFromSrg(renderEntityItem),
                "glintcolorizer$setCameraTransform", mappings.descriptorFromSrg("(Lnet/minecraft/entity/item/EntityItem;DDDFF" + ci + ")V"),
                List.of(method(mappings, renderEntityItem, "func_76986_a", "(Lnet/minecraft/entity/item/EntityItem;DDDFF)V"))),
            new CameraRule("org.polyfrost.glintcolorizer.mixin.ItemRendererMixin_HandleCameraTransforms", mappings.classFromSrg(renderItem),
                "glintcolorizer$setCameraTransform$hand", mappings.descriptorFromSrg("(" + stack + model + "L" + transform + ";" + ci + ")V"),
                List.of(mappings.methodFromSrg(renderItem, "func_175040_a", "(" + stack + model + "L" + transform + ";)V"))),
            new CameraRule("org.polyfrost.glintcolorizer.mixin.ItemRendererMixin_HandleCameraTransforms", mappings.classFromSrg(renderItem),
                "glintcolorizer$setCameraTransform$gui", mappings.descriptorFromSrg("(" + stack + "II" + ci + ")V"),
                List.of(mappings.methodFromSrg(renderItem, "func_175042_a", "(" + stack + "II)V"))));
    }

    @Override
    public AdjustableAnnotationNode adjust(List<String> targets, String mixin, MethodNode handler, AdjustableAnnotationNode annotation) {
        if (FONT_MIXIN.equals(mixin) && singleTarget(targets, font)) {
            FontRule rule = fonts.get(handler.name);
            if (rule == null || !rule.annotation.equals(annotation.desc)) return annotation;
            if (!rule.descriptor.equals(handler.desc)) throw unsupported(mixin, handler.name, "handler descriptor changed");
            List<String> methods = strings(value(annotation, "method"));
            if (!methods.equals(rule.methods) && !methods.equals(List.of(rule.replacement))) {
                throw unsupported(mixin, handler.name, "method selectors changed");
            }
            if (!atTargets(annotation).equals(rule.targets)) throw unsupported(mixin, handler.name, "invocation targets changed");
            annotation.set("method", new ArrayList<>(List.of(rule.replacement)));
        } else if (LIGHTMAP_MIXIN.equals(mixin) && singleTarget(targets, renderer)
                && annotation.desc.equals("Lorg/spongepowered/asm/mixin/injection/ModifyVariable;")) {
            Integer ordinal = switch (handler.name) {
                case "sarcio$clampRedBeforeNightVision" -> 11;
                case "sarcio$clampGreenBeforeNightVision" -> 12;
                case "sarcio$clampBlueBeforeNightVision" -> 13;
                default -> null;
            };
            if (ordinal == null) return annotation;
            if (!handler.desc.equals("(F)F")) throw unsupported(mixin, handler.name, "handler descriptor changed");
            if (!strings(value(annotation, "method")).equals(List.of(lightmap)) || !atTargets(annotation).equals(List.of(potionCheck))) {
                throw unsupported(mixin, handler.name, "lightmap targets changed");
            }
            Object current = value(annotation, "ordinal");
            if (!ordinal.equals(current) && !Integer.valueOf(ordinal - 2).equals(current)) {
                throw unsupported(mixin, handler.name, "local ordinal changed");
            }
            annotation.set("ordinal", ordinal - 2);
        }
        if (mixin.equals("dev.rdh.sarcio.mixin.bugfix.render.WorldHorizonMixin") && singleTarget(targets, world)
                && handler.name.equals("sarcio$horizonAtVoid")
                && annotation.desc.equals("Lcom/llamalad7/mixinextras/injector/ModifyExpressionValue;")) {
            adjustHorizon(mixin, handler, annotation);
        }
        for (Relocation rule : relocations) {
            if (!rule.mixin.equals(mixin) || !rule.handler.equals(handler.name) || !rule.annotation.equals(annotation.desc)
                    || !sameTargets(targets, rule.targets)) continue;
            if (!rule.descriptor.equals(handler.desc)) throw unsupported(mixin, handler.name, "handler descriptor changed");
            List<String> methods = strings(value(annotation, "method"));
            if ((!methods.equals(rule.methods) && !methods.equals(rule.replacement)) || !pointKeys(annotation).equals(rule.points)) {
                throw unsupported(mixin, handler.name, "body selectors or injection points changed");
            }
            annotation.set("method", new ArrayList<>(rule.replacement));
        }
        for (CameraRule rule : cameras) {
            if (!rule.mixin.equals(mixin) || !rule.handler.equals(handler.name) || !singleTarget(targets, rule.target)
                    || !annotation.desc.equals("Lorg/spongepowered/asm/mixin/injection/Inject;")) continue;
            if (!rule.descriptor.equals(handler.desc) || !strings(value(annotation, "method")).equals(rule.methods)) {
                throw unsupported(mixin, handler.name, "camera handler or selectors changed");
            }
            List<String> points = atTargets(annotation);
            if (points.equals(List.of(vanillaCamera, forgeCamera))) return annotation;
            if (!points.equals(List.of(vanillaCamera))) throw unsupported(mixin, handler.name, "camera invocation target changed");
            AnnotationNode original = (AnnotationNode) ((List<?>) value(annotation, "at")).get(0);
            AnnotationNode alternate = new AnnotationNode(original.desc); original.accept(alternate);
            put(alternate, "target", forgeCamera);
            annotation.set("at", new ArrayList<>(List.of(original, alternate)));
        }
        return annotation;
    }

    private static List<Relocation> relocations(ForgeMappings mappings) {
        String world = "net/minecraft/world/World", chunk = "net/minecraft/world/chunk/Chunk", provider = "net/minecraft/world/gen/ChunkProviderServer";
        String operation = "Lcom/llamalad7/mixinextras/injector/wrapoperation/Operation;";
        String chunkDescriptor = "(II)L" + chunk + ";";
        String load = mappings.methodFromSrg(provider, "func_73158_c", chunkDescriptor);
        List<String> oldLoad = List.of(load), newLoad = List.of("originalLoadChunk" + mappings.descriptorFromSrg(chunkDescriptor));
        String share = "(IILorg/spongepowered/asm/mixin/injection/callback/CallbackInfoReturnable;Lcom/llamalad7/mixinextras/sugar/ref/LocalBooleanRef;)V";
        List<Relocation> rules = new ArrayList<>();
        String argentum = "dev.rdh.argentum.mixin.core.world.ChunkCacheMixin";
        rules.add(new Relocation(argentum, List.of(mappings.classFromSrg(provider)), "markLoaded", share,
            "Lorg/spongepowered/asm/mixin/injection/Inject;", oldLoad, newLoad,
            List.of("INVOKE:" + selector(mappings, provider, "func_73239_e", chunkDescriptor))));
        rules.add(new Relocation(argentum, List.of(mappings.classFromSrg(provider)), "sendLoadEventIfLoaded", share,
            "Lorg/spongepowered/asm/mixin/injection/Inject;", oldLoad, newLoad, List.of("RETURN:")));
        rules.add(new Relocation("dev.rdh.sarcio.mixin.opt.worldgen.ServerChunkCacheMixin", List.of(mappings.classFromSrg(provider)),
            "sarcio$takePrefetched", mappings.descriptorFromSrg("(Lnet/minecraft/world/chunk/IChunkProvider;II" + operation + ")L" + chunk + ";"),
            OPERATION, oldLoad, newLoad, List.of("INVOKE:" + selector(mappings, "net/minecraft/world/chunk/IChunkProvider", "func_73154_d", chunkDescriptor))));
        String anvil = "net/minecraft/world/chunk/storage/AnvilChunkLoader";
        String anvilArgs = "(L" + world + ";II)";
        rules.add(new Relocation("dev.rdh.sarcio.mixin.mem.leak.world.AnvilChunkStorageMixin", List.of(mappings.classFromSrg(anvil)), "closeChunkStream",
            mappings.descriptorFromSrg("(Ljava/io/DataInputStream;" + operation + ")Lnet/minecraft/nbt/NBTTagCompound;"), OPERATION,
            List.of(method(mappings, anvil, "func_75815_a", anvilArgs + "L" + chunk + ";")),
            List.of("loadChunk__Async" + mappings.descriptorFromSrg(anvilArgs + "[Ljava/lang/Object;")),
            List.of("INVOKE:" + selector(mappings, "net/minecraft/nbt/CompressedStreamTools", "func_74794_a", "(Ljava/io/DataInputStream;)Lnet/minecraft/nbt/NBTTagCompound;"))));
        List<String> weatherOld = List.of(mappings.methodFromSrg(world, "func_72833_a", "(Lnet/minecraft/entity/Entity;F)Lnet/minecraft/util/Vec3;"),
            mappings.methodFromSrg(world, "func_72971_b", "(F)F"), mappings.methodFromSrg(world, "func_72824_f", "(F)Lnet/minecraft/util/Vec3;"));
        List<String> weatherNew = List.of("getSkyColorBody", "getSunBrightnessBody", "drawCloudsBody", "getSunBrightnessFactor");
        for (String handler : List.of("modifyPrecipitationStrength", "modifyThunderStrength")) {
            rules.add(new Relocation("org.polyfrost.polyweather.mixin.client.Mixin_FixWorldColors", List.of(mappings.classFromSrg(world)), handler,
                mappings.descriptorFromSrg("(L" + world + ";F" + operation + ")F"), OPERATION, weatherOld, weatherNew,
                List.of("INVOKE:" + selector(mappings, world, handler.equals("modifyPrecipitationStrength") ? "func_72867_j" : "func_72819_i", "(F)F"))));
        }
        String caves = "net/minecraft/world/gen/MapGenCaves", ravine = "net/minecraft/world/gen/MapGenRavine";
        String carve = "(JIILnet/minecraft/world/chunk/ChunkPrimer;DDDFFFIID)V";
        rules.add(new Relocation("dev.rdh.sarcio.mixin.opt.worldgen.WorldCarverMixin", List.of(mappings.classFromSrg(caves), mappings.classFromSrg(ravine)),
            "sarcio$prefetchedBiome", mappings.descriptorFromSrg("(L" + world + ";Lnet/minecraft/util/BlockPos;" + operation + ")Lnet/minecraft/world/biome/BiomeGenBase;"), OPERATION,
            List.of(mappings.methodFromSrg(caves, "func_180702_a", carve), mappings.methodFromSrg(ravine, "func_180707_a", carve)),
            List.of("isTopBlock", "digBlock"), List.of("INVOKE:" + selector(mappings, world, "func_180494_b", "(Lnet/minecraft/util/BlockPos;)Lnet/minecraft/world/biome/BiomeGenBase;"))));
        String armor = "net/minecraft/client/renderer/entity/layers/LayerArmorBase";
        String itemArmor = "Lnet/minecraft/item/ItemArmor;", resource = "Lnet/minecraft/util/ResourceLocation;";
        String oldTexture = "(" + itemArmor + "Z)" + resource;
        rules.add(new Relocation("dev.rdh.sarcio.mixin.mem.alloc.render.AbstractArmorLayerMixin", List.of(mappings.classFromSrg(armor)),
            "sarcio$cacheArmorTexture", mappings.descriptorFromSrg("(L" + armor + ";" + itemArmor + "ZLjava/lang/String;" + operation + ")" + resource), OPERATION,
            List.of(mappings.methodFromSrg(armor, "func_177182_a", "(Lnet/minecraft/entity/EntityLivingBase;FFFFFFFI)V"),
                method(mappings, armor, "func_177181_a", oldTexture)),
            List.of("getArmorResource" + mappings.descriptorFromSrg("(Lnet/minecraft/entity/Entity;Lnet/minecraft/item/ItemStack;ILjava/lang/String;)" + resource),
                method(mappings, armor, "func_177181_a", oldTexture)),
            List.of("INVOKE:" + selector(mappings, armor, "func_177178_a", "(" + itemArmor + "ZLjava/lang/String;)" + resource))));
        return List.copyOf(rules);
    }

    private void adjustHorizon(String mixin, MethodNode handler, AdjustableAnnotationNode annotation) {
        if (!handler.desc.equals("(D)D") || (handler.access & Opcodes.ACC_STATIC) != 0
                || !strings(value(annotation, "method")).equals(List.of(horizon))) {
            throw unsupported(mixin, handler.name, "horizon handler or selectors changed");
        }
        List<AbstractInsnNode> body = new ArrayList<>();
        for (AbstractInsnNode instruction : handler.instructions) if (instruction.getOpcode() >= 0) body.add(instruction);
        String owner = "io/github/arcadiumclient/forgecompat/HostMixinCompatibility";
        boolean original = body.size() == 2 && body.get(0).getOpcode() == Opcodes.DCONST_0 && body.get(1).getOpcode() == Opcodes.DRETURN;
        boolean adapted = body.size() == 3 && body.get(0) instanceof VarInsnNode load && load.getOpcode() == Opcodes.DLOAD && load.var == 1
            && body.get(1) instanceof MethodInsnNode call && call.getOpcode() == Opcodes.INVOKESTATIC && call.owner.equals(owner)
            && call.name.equals("horizon") && call.desc.equals("(D)D") && body.get(2).getOpcode() == Opcodes.DRETURN;
        Object raw = value(annotation, "at");
        List<?> points = raw instanceof List<?> list ? list : raw instanceof AnnotationNode at ? List.of(at) : List.of();
        if (points.size() != 1 || !(points.get(0) instanceof AnnotationNode at) || (!original && !adapted)) {
            throw unsupported(mixin, handler.name, "horizon body or injection point changed");
        }
        boolean oldPoint = "CONSTANT".equals(value(at, "value")) && List.of("doubleValue=63.0").equals(value(at, "args"));
        boolean newPoint = "INVOKE".equals(value(at, "value")) && providerHorizon.equals(value(at, "target"));
        if (!oldPoint && !newPoint) throw unsupported(mixin, handler.name, "horizon provider target changed");
        AnnotationNode replacement = new AnnotationNode(at.desc); at.accept(replacement);
        put(replacement, "value", "INVOKE"); put(replacement, "target", providerHorizon); remove(replacement, "args");
        annotation.set("at", new ArrayList<>(List.of(replacement)));
        if (original) {
            VarInsnNode load = new VarInsnNode(Opcodes.DLOAD, 1);
            handler.instructions.set(body.get(0), load);
            handler.instructions.insert(load, new MethodInsnNode(Opcodes.INVOKESTATIC, owner, "horizon", "(D)D", false));
        }
    }

    public static double horizon(double providerHorizon) {
        return providerHorizon == 63.0D ? 0.0D : providerHorizon;
    }

    private static boolean singleTarget(List<String> targets, String expected) {
        return targets.size() == 1 && targets.get(0).replace('.', '/').equals(expected);
    }
    private static boolean sameTargets(List<String> targets, List<String> expected) {
        return targets.size() == expected.size() && targets.stream().map(target -> target.replace('.', '/')).sorted().toList().equals(expected.stream().sorted().toList());
    }
    private static List<String> pointKeys(AnnotationNode annotation) {
        Object locations = value(annotation, "at");
        List<?> list = locations instanceof List<?> all ? all : locations == null ? List.of() : List.of(locations);
        List<String> keys = new ArrayList<>();
        for (Object item : list) {
            if (!(item instanceof AnnotationNode at) || !(value(at, "value") instanceof String kind)) return List.of();
            keys.add(kind + ":" + java.util.Objects.toString(value(at, "target"), ""));
        }
        return keys;
    }
    private static void put(AnnotationNode annotation, String key, Object value) {
        for (int i = 0; annotation.values != null && i < annotation.values.size(); i += 2) {
            if (annotation.values.get(i).equals(key)) { annotation.values.set(i + 1, value); return; }
        }
        if (annotation.values == null) annotation.values = new ArrayList<>();
        annotation.values.add(key); annotation.values.add(value);
    }
    private static void remove(AnnotationNode annotation, String key) {
        for (int i = 0; annotation.values != null && i < annotation.values.size(); i += 2) {
            if (annotation.values.get(i).equals(key)) { annotation.values.remove(i + 1); annotation.values.remove(i); return; }
        }
    }
    private static List<String> atTargets(AnnotationNode annotation) {
        Object locations = value(annotation, "at");
        List<?> list = locations instanceof List<?> all ? all : locations == null ? List.of() : List.of(locations);
        List<String> targets = new ArrayList<>();
        for (Object item : list) {
            if (!(item instanceof AnnotationNode at) || !"INVOKE".equals(value(at, "value")) || !(value(at, "target") instanceof String target)) return List.of();
            targets.add(target);
        }
        return targets;
    }
    private static Object value(AnnotationNode annotation, String key) {
        for (int i = 0; annotation.values != null && i < annotation.values.size(); i += 2) {
            if (annotation.values.get(i).equals(key)) return annotation.values.get(i + 1);
        }
        return null;
    }
    private static List<String> strings(Object value) {
        if (!(value instanceof List<?> values) || values.stream().anyMatch(item -> !(item instanceof String))) return List.of();
        return values.stream().map(String.class::cast).toList();
    }
    private static String method(ForgeMappings mappings, String owner, String name, String descriptor) {
        return mappings.methodFromSrg(owner, name, descriptor) + mappings.descriptorFromSrg(descriptor);
    }
    private static String selector(ForgeMappings mappings, String owner, String name, String descriptor) {
        return "L" + mappings.classFromSrg(owner) + ";" + method(mappings, owner, name, descriptor);
    }
    private static IllegalStateException unsupported(String mixin, String handler, String reason) {
        return new IllegalStateException("Cannot adapt native mixin " + mixin + "." + handler + ": " + reason + ".");
    }
}
