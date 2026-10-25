// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.modern.client.profile;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import dev.isxander.yacl3.config.v2.api.ConfigClassHandler;
import dev.isxander.yacl3.config.v2.api.ConfigField;
import dev.isxander.yacl3.config.v2.api.ConfigSerializer;
import dev.isxander.yacl3.config.v2.api.FieldAccess;
import io.github.arcadiumclient.modern.client.ModernSettingsMigration;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import org.polyfrost.oneconfig.api.config.v1.ConfigManager;
import org.polyfrost.oneconfig.internal.compat.YACLCompat;

public final class NativeYaclProfiles {
    private record Selection(String profile, Path folder) {}
    private static final class Access {
        final LegacyYaclSchema.Spec spec;
        final ConfigClassHandler<?> handler;
        final Gson gson;
        final String nativeFile;
        volatile JsonObject lastLive;
        Access(LegacyYaclSchema.Spec spec, ConfigClassHandler<?> handler, Gson gson, Path nativeFile) {
            this.spec = spec; this.handler = handler; this.gson = gson; this.nativeFile = nativeFile.getFileName().toString();
        }
    }
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("Arcadium/SharedProfiles");
    private static final Map<String, Access> ACCESS = new ConcurrentHashMap<>();
    private static final Set<Path> BLOCKED = ConcurrentHashMap.newKeySet();
    private static final AtomicLong EPOCH = new AtomicLong();
    private static volatile boolean ready, loading;
    private static volatile String currentProfile;
    private NativeYaclProfiles() {}

    public static void initialize() {
        ConfigManager.addTreeRegistrationListener(LegacySmoothSkyOptions::attach);
        ConfigManager.addProfileChangeListener(new ConfigManager.ProfileChangeListener() {
            public void onProfileSaving(String profile) {
                EPOCH.incrementAndGet();
                if (!ready || loading || !profile.equals(currentProfile) || !profile.equals(ConfigManager.activeProfile())) return;
                for (Access access : ACCESS.values()) save(access);
            }
            public void onProfileChanged(String profile) { if (ready) applyProfile(profile); }
            public void onProfileRenamed(String previous, String next) {
                EPOCH.incrementAndGet();
                if (previous.equals(currentProfile)) currentProfile = next;
                Path source = ConfigManager.profileDir(previous).toAbsolutePath().normalize();
                Path target = ConfigManager.profileDir(next).toAbsolutePath().normalize();
                var renamed = BLOCKED.stream().filter(path -> path.startsWith(source)).toList();
                for (Path path : renamed) { BLOCKED.remove(path); BLOCKED.add(target.resolve(source.relativize(path))); }
            }
            public void onProfileDeleted(String profile) {
                EPOCH.incrementAndGet();
                Path folder = ConfigManager.profileDir(profile).toAbsolutePath().normalize();
                BLOCKED.removeIf(path -> path.startsWith(folder));
            }
        });
        ClientLifecycleEvents.CLIENT_STARTED.register(client -> {
            ready = true; applyProfile(ConfigManager.activeProfile());
            if (ConfigManager.active().exists("smoothskies")) LegacySmoothSkyOptions.attach(ConfigManager.active().get("smoothskies"));
        });
    }

    public static ConfigSerializer.LoadResult loadNative(ConfigClassHandler<?> handler, Gson gson, Path nativeFile, Map<ConfigField<?>, FieldAccess<?>> fields) {
        LegacyYaclSchema.Spec spec = LegacyYaclSchema.find(handler.configClass().getName());
        if (spec == null) return null;
        Access access = access(spec, handler, gson, nativeFile);
        Path file = null;
        try {
            Selection target = selection(); file = target.folder.resolve(spec.legacyFile());
            var plan = LegacyYaclStore.load(spec, target.folder, access.nativeFile, NativeYaclFields.capture(handler, gson, true));
            var values = NativeYaclFields.decode(fields, gson, plan.desired());
            var sky = spec.modId().equals("smoothskies") ? LegacySmoothSkyOptions.parse(plan.marker().getAsJsonObject("legacy")) : null;
            plan.commit();
            NativeYaclFields.apply(values);
            if (sky != null) LegacySmoothSkyOptions.apply(sky);
            access.lastLive = plan.desired().deepCopy();
            BLOCKED.remove(file);
            return ConfigSerializer.LoadResult.SUCCESS;
        } catch (Exception failure) {
            if (file != null) BLOCKED.add(file);
            LOGGER.warn("Kept unreadable shared settings for {} unchanged", spec.modId(), failure);
            return ConfigSerializer.LoadResult.NO_CHANGE;
        }
    }

    public static boolean saveNative(ConfigClassHandler<?> handler, Gson gson, Path nativeFile) {
        LegacyYaclSchema.Spec spec = LegacyYaclSchema.find(handler.configClass().getName());
        if (spec == null) return false;
        Access access = access(spec, handler, gson, nativeFile);
        if (loading || ready && !ConfigManager.activeProfile().equals(currentProfile)) return true;
        save(access);
        return true;
    }

    private static Access access(LegacyYaclSchema.Spec spec, ConfigClassHandler<?> handler, Gson gson, Path nativeFile) {
        return ACCESS.compute(spec.modId(), (id, access) -> access == null || access.handler != handler ? new Access(spec, handler, gson, nativeFile) : access);
    }
    private static void save(Access access) {
        try {
            long epoch = EPOCH.get();
            Selection target = selection();
            if (BLOCKED.contains(target.folder.resolve(access.spec.legacyFile()))) return;
            JsonObject value = NativeYaclFields.capture(access.handler, access.gson, false);
            if (epoch != EPOCH.get()) return;
            LegacyYaclStore.save(access.spec, target.folder, value);
            access.lastLive = value;
        } catch (Exception failure) { LOGGER.warn("Could not save shared settings for {}", access.spec.modId(), failure); }
    }

    public static void saveSkyOptions(LegacySmoothSkyOptions.Selection value) {
        Access access = ACCESS.get("smoothskies");
        if (access == null || loading || ready && !ConfigManager.activeProfile().equals(currentProfile)) return;
        try {
            Selection target = selection();
            if (BLOCKED.contains(target.folder.resolve(access.spec.legacyFile()))) return;
            JsonObject nativeValue = NativeYaclFields.capture(access.handler, access.gson, false);
            LegacyYaclStore.save(access.spec, target.folder, nativeValue, LegacySmoothSkyOptions.encoded(value));
            LegacySmoothSkyOptions.apply(value);
            access.lastLive = nativeValue;
        } catch (Exception failure) { LOGGER.warn("Could not save shared Smooth Skies settings", failure); }
    }

    private static void applyProfile(String profile) {
        EPOCH.incrementAndGet();
        loading = true;
        try {
            Selection target = selection();
            for (Access access : ACCESS.values()) {
                Path file = target.folder.resolve(access.spec.legacyFile());
                try {
                    var plan = LegacyYaclStore.load(access.spec, target.folder, access.nativeFile, NativeYaclFields.capture(access.handler, access.gson, true));
                    var decoded = NativeYaclFields.decode(NativeYaclFields.live(access.handler), access.gson, plan.desired());
                    var sky = access.spec.modId().equals("smoothskies") ? LegacySmoothSkyOptions.parse(plan.marker().getAsJsonObject("legacy")) : null;
                    plan.commit();
                    NativeYaclFields.apply(decoded);
                    if (sky != null) LegacySmoothSkyOptions.apply(sky);
                    access.lastLive = plan.desired().deepCopy();
                    BLOCKED.remove(file);
                } catch (Exception failure) {
                    BLOCKED.add(file);
                    if (access.lastLive != null) NativeYaclFields.apply(NativeYaclFields.decode(NativeYaclFields.live(access.handler), access.gson, access.lastLive));
                    LOGGER.warn("Kept unreadable profile settings for {} unchanged", access.spec.modId(), failure);
                }
                YACLCompat.refreshFromBindings(access.spec.modId());
            }
        } catch (Exception failure) { LOGGER.warn("Could not resolve the active shared profile", failure); }
        finally { currentProfile = profile; loading = false; EPOCH.incrementAndGet(); }
    }

    private static Selection selection() throws java.io.IOException {
        if (ready) return new Selection(ConfigManager.activeProfile(), ConfigManager.active().getFolder().toAbsolutePath().normalize());
        var loader = FabricLoader.getInstance();
        Path game = loader.getGameDir().toAbsolutePath().normalize();
        Path preferences = game.resolve("arcadium/profiles.json");
        String profile = "";
        if (Files.isRegularFile(preferences)) {
            var json = ModernSettingsMigration.read(preferences);
            if (json.has("activeProfile")) profile = json.get("activeProfile").getAsString();
        }
        if (profile.isBlank()) return new Selection("", loader.getConfigDir().toAbsolutePath().normalize());
        Path name = Path.of(profile), base = game.resolve("profiles"), folder = base.resolve(name).normalize();
        if (name.isAbsolute() || name.getNameCount() != 1 || !folder.startsWith(base) || folder.equals(base)) throw new java.io.IOException("Invalid active profile folder");
        return new Selection(profile, folder);
    }
}
