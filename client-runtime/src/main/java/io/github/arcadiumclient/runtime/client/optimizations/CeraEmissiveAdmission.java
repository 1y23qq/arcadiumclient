// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.optimizations;

import java.lang.annotation.Annotation;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.render.texture.TextureManager;
import net.minecraft.resource.Identifier;

public final class CeraEmissiveAdmission {
    private static final String INSTANCER = "dev.rdh.argentum.impl.render.entity.instancing.ModelInstancer";
    private static final String MIXIN = "dev.rdh.cera.mixin.ModelInstancerMixin";
    private static final String TEXTURES_MIXIN = "dev.rdh.cera.mixin.TextureManagerMixin";
    private static final String SARCIO_MIXIN = "dev.rdh.sarcio.mixin.opt.textures.TextureManagerMixin";
    private static final String SARCIO_JAR = "bbccff506dcbe04b1b081b46cc5d98f93fcf1af316bd5fda333a499bc5bc378f";
    private static final String SARCIO_CLASS = "8b6e913faa78b52f1cf779e40e82e3c985264cd841a29a7f036239ef00333121";
    private static final String API = "dev.rdh.cera.modules.EmissiveTextures";
    private static final String EXTENSION = "dev.rdh.cera.ext.CeraTextureManagerExtension";
    private static final String API_SHA = "a64ff3b9c39490c36590c4803a131b2d73887e9e1e8d57ac052aca53a46cffed";
    private static final String EXTENSION_SHA = "f9f1444c42db358133f418a6d7a561dc6925ad1296c1b6e867155bfe777b2012";
    private static final String TEXTURES_SHA = "d14c7e8016e649ce8f4dfbb8b13018192d7e6787625b8a7a0e5583ae5019cc4e";
    private static boolean checked, trusted, present, lookupFailed, sarcioAudited;
    private static long generation = 1;
    private static MethodHandle module, lookup;
    private static volatile String state = "Waiting for a native texture";
    private static String failureStage = "", failureType = "", failureMessage = "", rejectedPass = "";
    private static Identifier rejectedTexture;
    public static long emissiveFallbacks, unavailableFallbacks, ordinaryTextures;

    public interface Capture {
        boolean arcadium$nativeMaterial(Object model, Identifier texture, String pass);
    }

    private CeraEmissiveAdmission() {}

    public static boolean nativeSubmitRequired(Identifier texture) {
        if (texture == null) return true;
        if (!checked) initialize();
        if (!trusted || lookupFailed) { unavailableFallbacks++; rejectedTexture = texture; rejectedPass = "texture"; return true; }
        if (!present) return false;
        try {
            Object manager = Minecraft.getInstance().getTextureManager();
            Object current = (Object)module.invokeExact(manager);
            if (current == null) { unavailableFallbacks++; rejectedTexture = texture; rejectedPass = "texture"; state = "Cera textures are not ready; retaining native submission"; return true; }
            Identifier emissive = (Identifier)lookup.invokeExact(current, texture);
            if (emissive != null) { emissiveFallbacks++; rejectedTexture = texture; rejectedPass = "texture"; state = "Emissive texture retains native submission"; return true; }
            ordinaryTextures++; state = "Ordinary Cera textures retain compact rendering"; return false;
        } catch (Throwable unavailable) {
            lookupFailed = true; unavailableFallbacks++; rejectedTexture = texture; rejectedPass = "texture"; failure("lookup", unavailable);
            state = "Cera lookup unavailable (" + failureType + "); retaining native submission"; return true;
        }
    }

    public static boolean nativeSubmitRequired(Object capture, Object model, Identifier texture, String pass) {
        return !(capture instanceof Capture material) || material.arcadium$nativeMaterial(model, texture, pass);
    }

    public static boolean nativeSubmitRequired(NativeMaterialState material, Object model, Identifier texture, String pass) {
        boolean base = "NORMAL".equals(pass) || "TRANSLUCENT".equals(pass), glint = "GLINT".equals(pass);
        boolean required = material.requires(model, texture, base, glint, base && nativeSubmitRequired(texture), generation);
        if (required) { rejectedTexture = texture; rejectedPass = pass; }
        return required;
    }

    private static void initialize() {
        checked = true;
        try {
            for (var mod : FabricLoader.getInstance().getAllMods()) {
                var top = mod;
                while (top.getContainingMod().isPresent()) top = top.getContainingMod().get();
                if (!ResolvedUploadPolicy.knownMod(top.getMetadata().getId())) { state = "Unrecognized submission mod: " + top.getMetadata().getId(); return; }
            }
            ClassLoader loader = CeraEmissiveAdmission.class.getClassLoader();
            Class<?> instancer = Class.forName(INSTANCER, false, loader);
            if (!ResolvedUploadPolicy.pinnedSource(INSTANCER, fingerprint(loader, INSTANCER))) { state = "Changed native submission source"; return; }
            var sarcio = FabricLoader.getInstance().getModContainer("sarcio");
            if (sarcio.isPresent()) {
                var container = sarcio.get();
                sarcioAudited = knownSarcio(container.getMetadata().getVersion().getFriendlyString(), archiveFingerprint(container), fingerprint(loader, SARCIO_MIXIN));
                if (!sarcioAudited) { state = "Unknown Sarcio texture manager integration"; return; }
            }
            var cera = FabricLoader.getInstance().getModContainer("cera");
            boolean audited = false;
            if (cera.isPresent()) {
                var container = cera.get(); String jarHash = archiveFingerprint(container);
                if (jarHash.isEmpty()) { state = "Unrecognized Cera archive"; return; }
                audited = ResolvedUploadPolicy.auditedCera(MIXIN, fingerprint(loader, MIXIN), container.getMetadata().getVersion().getFriendlyString(), jarHash);
                if (!audited || !knownApi(fingerprint(loader, API), fingerprint(loader, EXTENSION), fingerprint(loader, TEXTURES_MIXIN))) { state = "Unknown Cera API or version"; return; }
                Class<?> api = Class.forName(API, false, loader);
                if (!knownHooks(instancer, audited) || !knownHooks(api, audited)) return;
                Object manager = Minecraft.getInstance().getTextureManager();
                if (manager.getClass() != TextureManager.class) { state = "Custom Cera texture manager"; return; }
                if (!knownHooks(manager.getClass(), audited)) return;
                Method getter = manager.getClass().getMethod("cera$getEmissiveTextures"), query = api.getMethod("emissiveTexture", Identifier.class);
                if (getter.getReturnType() != api || query.getReturnType() != Identifier.class) { state = "Changed Cera lookup contract"; return; }
                module = MethodHandles.publicLookup().unreflect(getter).asType(MethodType.methodType(Object.class, Object.class));
                lookup = MethodHandles.publicLookup().unreflect(query).asType(MethodType.methodType(Identifier.class, Object.class, Identifier.class));
                present = true;
            } else if (!knownHooks(instancer, false)) return;
            trusted = true; state = present ? "Pinned Cera lookup available" : "Cera absent; native submission hooks verified";
        } catch (Throwable unavailable) { failure("initialization", unavailable); state = "Submission provenance unavailable (" + failureType + "); retaining native rendering"; }
    }

    private static boolean knownHooks(Class<?> type, boolean auditedCera) throws ReflectiveOperationException {
        for (Method method : type.getDeclaredMethods()) for (Annotation annotation : method.getDeclaredAnnotations()) {
            if (!annotation.annotationType().getName().equals("org.spongepowered.asm.mixin.transformer.meta.MixinMerged")) continue;
            String mixin = (String)annotation.annotationType().getMethod("mixin").invoke(annotation);
            if (!knownHook(mixin, auditedCera, sarcioAudited)) { state = "Custom native submission hook: " + mixin; return false; }
        }
        return true;
    }

    private static String fingerprint(ClassLoader loader, String name) throws Exception {
        try (var input = loader.getResourceAsStream(name.replace('.', '/') + ".class")) {
            if (input == null) return "";
            byte[] bytes = input.readNBytes((512 << 10) + 1);
            return bytes.length > (512 << 10) ? "" : HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        }
    }

    private static String archiveFingerprint(ModContainer container) throws Exception {
        var paths = container.getOrigin().getPaths();
        if (paths.size() != 1 || !Files.isRegularFile(paths.getFirst()) || Files.size(paths.getFirst()) > (64L << 20)) return "";
        try (var input = Files.newInputStream(paths.getFirst())) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256"); byte[] bytes = new byte[8192]; int count;
            while ((count = input.read(bytes)) != -1) digest.update(bytes, 0, count);
            return HexFormat.of().formatHex(digest.digest());
        }
    }

    static boolean knownApi(String api, String extension, String manager) { return API_SHA.equals(api) && EXTENSION_SHA.equals(extension) && TEXTURES_SHA.equals(manager); }
    static boolean knownSarcio(String version, String jar, String mixin) { return "1.1.0".equals(version) && SARCIO_JAR.equals(jar) && SARCIO_CLASS.equals(mixin); }
    static boolean knownHook(String name, boolean auditedCera) { return knownHook(name, auditedCera, false); }
    static boolean knownHook(String name, boolean auditedCera, boolean auditedSarcio) {
        return ResolvedUploadPolicy.knownMixin(name) || auditedCera && (MIXIN.equals(name) || TEXTURES_MIXIN.equals(name)) || auditedSarcio && SARCIO_MIXIN.equals(name);
    }
    private static void failure(String stage, Throwable error) {
        failureStage = stage; failureType = error.getClass().getName(); String message = String.valueOf(error.getMessage());
        failureMessage = message.length() > 256 ? message.substring(0, 256) : message;
    }
    public static Map<String, Object> diagnostics() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", state); result.put("checked", checked); result.put("trusted", trusted); result.put("ceraLookupAttached", present);
        result.put("lookupFailed", lookupFailed); result.put("sarcioAudited", sarcioAudited);
        result.put("failureStage", failureStage); result.put("failureType", failureType);
        String texture = String.valueOf(rejectedTexture);
        result.put("lastRejectedTextureCategory", rejectedTexture == null ? "none" : texture.equals("minecraft:textures/entity/steve.png") || texture.equals("minecraft:textures/entity/alex.png") ? "default skin" : texture.contains(":skins/") ? "downloaded skin" : "other");
        result.put("lastRejectedPass", rejectedPass);
        result.put("ordinaryTextures", ordinaryTextures); result.put("emissiveFallbacks", emissiveFallbacks); result.put("unavailableFallbacks", unavailableFallbacks);
        return result;
    }
    private static Map<String, Object> privateDiagnostics() {
        Map<String, Object> result = diagnostics(); result.put("failureMessage", failureMessage); result.put("lastRejectedTexture", String.valueOf(rejectedTexture)); return result;
    }
    public static void reset() { generation++; lookupFailed = false; rejectedTexture = null; rejectedPass = ""; }
    public static String status() { return "Cera material admission: " + state + " | Emissive/native: " + emissiveFallbacks + " | Unknown: " + unavailableFallbacks; }
}
