// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.forge1710;

import com.google.gson.JsonParser;
import com.gtnewhorizon.gtnhmixins.IEarlyMixinLoader;
import cpw.mods.fml.relauncher.IFMLLoadingPlugin;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.spongepowered.asm.mixin.Mixins;

@IFMLLoadingPlugin.MCVersion("1.7.10")
@IFMLLoadingPlugin.Name("Arcadium")
@IFMLLoadingPlugin.TransformerExclusions({"io.github.arcadiumclient.forge1710.bootstrap.",
    "io.github.arcadiumclient.forge1710.platform.", "io.github.arcadiumclient.forge1710.ArcadiumLoadingPlugin",
    "io.github.arcadiumclient.forge1710.ArcadiumAccessTransformer"})
public final class ArcadiumLoadingPlugin implements IFMLLoadingPlugin, IEarlyMixinLoader {
    private static boolean configured;

    public ArcadiumLoadingPlugin() {
        if (System.getProperty("mixinconstraints.abstraction") == null)
            System.setProperty("mixinconstraints.abstraction", "io.github.arcadiumclient.forge1710.platform.ForgeConstraints");
        if (Boolean.getBoolean("arcadium.forge.debugBootstrap")) {
            try {
                var loader = net.minecraft.launchwrapper.Launch.classLoader;
                String name = "org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin";
                byte[] bytes = loader.getClassBytes(name);
                System.out.println("Arcadium mixin source: " + loader.getResource(name.replace('.', '/') + ".class"));
                String raw = new String(bytes, java.nio.charset.StandardCharsets.ISO_8859_1);
                System.out.println("Arcadium mixin raw ASM: legacy=" + raw.contains("org/spongepowered/asm/lib") + ", modern=" + raw.contains("org/objectweb/asm"));
            } catch (Exception error) { throw new IllegalStateException(error); }
        }
    }

    @Override public String[] getASMTransformerClass() { return new String[]{"io.github.arcadiumclient.forge1710.ArcadiumAccessTransformer"}; }
    @Override public String getModContainerClass() { return null; }
    @Override public String getSetupClass() { return null; }
    @Override public void injectData(Map<String, Object> data) {}
    @Override public String getAccessTransformerClass() { return null; }

    @Override public String getMixinConfig() {
        if (!configured) {
            configured = true;
            try (var input = ArcadiumLoadingPlugin.class.getResourceAsStream("/arcadium-forge-mixins.json")) {
                if (input == null) throw new IllegalStateException("Arcadium Forge mixin index is missing.");
                for (var value : new JsonParser().parse(new InputStreamReader(input, StandardCharsets.UTF_8)).getAsJsonArray())
                    Mixins.addConfiguration(value.getAsString());
            } catch (java.io.IOException error) { throw new IllegalStateException(error); }
        }
        return "mixins.arcadium-forge.json";
    }

    @Override public List<String> getMixins(Set<String> loadedCoreMods) { return List.of(); }
}
