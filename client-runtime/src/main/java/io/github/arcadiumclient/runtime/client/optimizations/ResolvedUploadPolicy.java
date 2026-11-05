// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.optimizations;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;
import net.fabricmc.loader.api.FabricLoader;

final class ResolvedUploadPolicy {
    static final String DRIVER = "org.embeddedt.embeddium.impl.gl.device.GLRenderDevice$ImmediateCommandList";
    static final String SERVICE = "org.taumc.celeritas.lwjgl.lwjgl3.LWJGL3Service";
    private static final Set<String> TOP_LEVEL = Set.of(("argentum,argentum-extras,asynclogger,behindyouv3,betterhurtcam,betternightvision,betterscreens,blur,cera,chatting,colorsaturation," +
            "compose-multiplatform,confirmdisconnect,confirmresetkeys,countdown,crosshairtweaks,custom-block-highlight,damagetint,droppeditemtweaks,evergreenhud,exploint," +
            "fabric-language-kotlin,fabricloader,fovchanger,freelook,fullbright,glintcolorizer,hybedwars,hybridge,hychatter,hylobby,itemphysiclite,java,keyyyyyyyy,legacy-skin-layers," +
            "minecraft,mountopacity,numericalenchantments,oneconfig,oneconfigv1,osl,overflowanimations,overflowparticles,overlaytweaks,polyblur,polyhitbox,polynametag,polytime," +
            "polyweather,polyzoom,pylon,redaction,rendertweaks,sarcio,sciophobia,screenshotmessageenhancer,scrolltweaks,shaketweaks,smartsearch,smoothscrollingrefurbished," +
            "smoothskies,soundtweaks,spark,tipper,tooltipscroll,vanillahud").split(","));
    private static final Map<String, String> PINS = Map.ofEntries(
            Map.entry("dev.rdh.argentum.api.render.instancing.InstancedGeometryBuffer", "ee7e4765aa5f006ed09ec7a280217efa71d63e7b87b203aa9ffec70e93930afc"),
            Map.entry("dev.rdh.argentum.api.render.instancing.InstanceDataBuffer", "200a7b59863902fd0307081a321d52ff9d12e5d41e99d048b139f607cae6286f"),
            Map.entry("dev.rdh.argentum.impl.render.entity.instancing.ModelInstancer", "53e51505e909620bbd439a199018a83e9bff4de75d0af0fd8af4d9752aa93638"),
            Map.entry("dev.rdh.argentum.impl.render.entity.instancing.InstanceBatcher", "09250fb7f4292c59c223d1ab5a44b29b0465904fc165b923c33187f8af9ad0e1"),
            Map.entry("dev.rdh.argentum.impl.render.entity.instancing.InstanceBatcher$TextureBatch", "11671ca42b9f03f6c1ae0bbd253971db594dd112efc41370942270127033b6ae"),
            Map.entry("dev.rdh.argentum.impl.render.entity.instancing.InstanceGeometry", "33eeaaa5dce6a1b0378f92762b9e3b790e6bcf24f726f3e288dc796fe0877f69"),
            Map.entry("dev.rdh.argentum.impl.render.entity.instancing.BoxGeometry", "c82edb509609f328f191c6925eeda4c29edc4d33d3adb7884e17075873158126"),
            Map.entry("dev.rdh.argentum.impl.render.entity.instancing.PartGeometry", "9fccdc80e9497a389669816cfa72b204be3d14cf28643e26e29b72d7afcdc62f"),
            Map.entry(DRIVER, "db2fe909b2d004868687083622fc1b5b8c3a91e089e5da29730a761c76b0676c"),
            Map.entry("org.embeddedt.embeddium.impl.gl.state.GlStateTracker", "6b076b99f4df7d0ea1e626bdecea07c1c715bcede3e1d2b1fd9633c46c966f69"),
            Map.entry("org.embeddedt.embeddium.impl.gl.buffer.GlMutableBuffer", "4f85d9a9e192fb8b584da57ea4f5fb2ec8d83917aaba2d8704d0ae9ddbbb2325"),
            Map.entry("org.embeddedt.embeddium.impl.gl.buffer.GlBuffer", "522eda230fd7d970a31a4e4fc59dea26a1f62d597bf305ca22ce3eee981d1af5"),
            Map.entry("org.embeddedt.embeddium.impl.gl.GlObject", "2c00d3413213a7c78612c35cc5e77334b6e6f0098451092207c2d798d440802b"),
            Map.entry(SERVICE, "4140ba694e2dca3e031f04d6b99d54bf97c9589518bf155444ce4729273401e5"));
    private static boolean checked, trusted;
    private static volatile String reason = "Not checked";
    private ResolvedUploadPolicy() {}

    static boolean knownMod(String id) { return id.startsWith("arcadium_") || TOP_LEVEL.contains(id); }
    static boolean knownMixin(String name) { return name.startsWith("io.github.arcadiumclient.runtime.") || name.startsWith("dev.rdh.argentum.mixin."); }

    static boolean trusted(Object commands) {
        if (commands == null || !commands.getClass().getName().equals(DRIVER)) { reason = "Custom command list"; return false; }
        if (GpuPlayerPipeline.hasForgeHooks()) { reason = "Forge upload hooks retain normal streaming"; return false; }
        if (!checked) {
            checked = true;
            try {
                for (var mod : FabricLoader.getInstance().getAllMods()) {
                    var top = mod;
                    while (top.getContainingMod().isPresent()) top = top.getContainingMod().get();
                    if (!knownMod(top.getMetadata().getId())) { reason = "Unrecognized mod: " + top.getMetadata().getId(); return false; }
                }
                ClassLoader loader = commands.getClass().getClassLoader();
                for (var pin : PINS.entrySet()) {
                    Class<?> type = Class.forName(pin.getKey(), false, loader);
                    try (var input = type.getResourceAsStream("/" + pin.getKey().replace('.', '/') + ".class")) {
                        if (input == null) { reason = "Missing upload source"; return false; }
                        byte[] bytes = input.readNBytes((512 << 10) + 1);
                        if (bytes.length > (512 << 10) || !pin.getValue().equals(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)))) {
                            reason = "Changed upload source: " + type.getSimpleName(); return false;
                        }
                    }
                    for (Method method : type.getDeclaredMethods()) for (Annotation annotation : method.getDeclaredAnnotations()) {
                        if (!annotation.annotationType().getName().equals("org.spongepowered.asm.mixin.transformer.meta.MixinMerged")) continue;
                        String mixin = (String)annotation.annotationType().getMethod("mixin").invoke(annotation);
                        if (!knownMixin(mixin)) { reason = "Custom upload hook: " + mixin; return false; }
                    }
                }
                trusted = true; reason = "Pinned native upload path";
            } catch (Throwable unavailable) { reason = "Upload provenance unavailable"; }
        }
        return trusted;
    }

    static boolean service(Object value) { return value != null && value.getClass().getName().equals(SERVICE); }
    static String reason() { return reason; }
}
