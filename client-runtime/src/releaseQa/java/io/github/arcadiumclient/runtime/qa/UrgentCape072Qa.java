// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.qa;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.authlib.GameProfile;
import io.github.arcadiumclient.runtime.client.capes.CapeCatalog;
import io.github.arcadiumclient.runtime.client.capes.LocalCape;
import io.github.arcadiumclient.runtime.client.features.CapeConfig;
import io.github.arcadiumclient.runtime.client.gui.SkinChangerScreen;
import io.github.arcadiumclient.runtime.client.skins.SkinChanger;
import java.lang.reflect.Method;
import io.github.arcadiumclient.runtime.qa.mixin.UrgentCape072InfoAccess;
import io.github.arcadiumclient.runtime.qa.mixin.UrgentCape072PlayerAccess;
import java.security.MessageDigest;
import java.util.*;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.living.player.ClientPlayerEntity;
import net.minecraft.client.entity.living.player.RemoteClientPlayerEntity;
import net.minecraft.client.network.PlayerInfo;
import net.minecraft.client.render.entity.PlayerRenderer;
import net.minecraft.client.render.entity.layer.CapeLayer;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.resource.Identifier;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.polyfrost.oneconfig.internal.ui.api.ModuleSettingsPanels;

public final class UrgentCape072Qa {
    private static boolean resetScope, layerScope;
    private static int resets, capeModelCalls;
    private UrgentCape072Qa() {}
    public static void resetObserved() { if (resetScope) resets++; }
    public static void capeModelObserved() { if (layerScope) capeModelCalls++; }

    public static Map<String,Object> verify(Minecraft mc) throws Exception {
        require("ArcadiumTest".equals(mc.getSession().getUsername()) && "0".equals(mc.getSession().getAccessToken())
            && mc.getCurrentServerEntry() == null && mc.world != null && mc.player != null, "Use only the owned offline cape fixture");
        boolean oldEnabled = CapeConfig.getEnabled(); String oldPreset = CapeConfig.getPreset(); var oldScreen = mc.screen;
        UUID self = mc.player.getUuid(), remote = UUID.fromString("00000000-0000-0000-0000-000000007202");
        require(self.equals(mc.getSession().getProfile().getId()), "The fixture player must own the fake session");
        require(!SkinChanger.hasCapeOverride(self), "Do not replace an existing account cape preview in QA");
        int active = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE), mode = GL11.glGetInteger(GL11.GL_MATRIX_MODE);
        GlStateManager.activeTexture(GL13.GL_TEXTURE0);
        int oldBinding = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        var color = BufferUtils.createFloatBuffer(4); GL11.glGetFloatv(GL11.GL_CURRENT_COLOR, color);
        try {
            require(GL11.glGetError() == GL11.GL_NO_ERROR, "Existing OpenGL error before cape QA");
            require(CapeConfig.INSTANCE.getTree().getProp("enabled") != null, "Cape module toggle missing");
            require(ModuleSettingsPanels.INSTANCE.forModule(CapeConfig.INSTANCE.id).stream().anyMatch(p -> "Capes".equals(p.getTitle())), "Cape browser is not reachable from module settings");
            var loaded = new ArrayList<Map<String,Object>>();
            for (var entry : CapeCatalog.entries()) {
                byte[] bytes;
                try (var stream = mc.getResourceManager().getResource(new Identifier("arcadium", entry.texturePath())).asStream()) { bytes = stream.readAllBytes(); }
                require(entry.sha256().equals(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes))), "Resource changed: " + entry.id());
                var image = ImageIO.read(new java.io.ByteArrayInputStream(bytes));
                require(image != null && image.getWidth() == 64 && image.getHeight() == 32, "Invalid PNG: " + entry.id());
                Identifier texture = new Identifier("arcadium", entry.texturePath()); mc.getTextureManager().bind(texture);
                int width = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_WIDTH);
                int height = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_HEIGHT);
                require(width == 64 && height == 32, "Texture was not uploaded: " + entry.id());
                require(GL11.glGetError() == GL11.GL_NO_ERROR, "Cape upload GL error: " + entry.id());
                loaded.add(Map.of("id", entry.id(), "width", width, "height", height, "sha256", entry.sha256()));
            }
            require(loaded.size() == 55, "Incomplete cape catalog");
            byte[] accountPng;
            try (var stream = CapeCatalog.class.getClassLoader().getResourceAsStream(CapeCatalog.find("minecon-2011").resource())) { accountPng = stream.readAllBytes(); }
            SkinChanger.INSTANCE.refreshCape(self, accountPng);
            Identifier account = SkinChanger.capeTextureFor(self);
            var selfInfo = new PlayerInfo(new GameProfile(self, "ArcadiumTest"));
            var remoteInfo = new PlayerInfo(new GameProfile(remote, "CapeRemote072"));
            Identifier remoteCape = new Identifier("arcadium", CapeCatalog.find("minecon-2012").texturePath());
            ((UrgentCape072InfoAccess)(Object)remoteInfo).cape072$waitingForTextures(true);
            ((UrgentCape072InfoAccess)(Object)remoteInfo).cape072$capeTexture(remoteCape);
            var remotePlayer = new RemoteClientPlayerEntity(mc.world, remoteInfo.getProfile());
            ((UrgentCape072PlayerAccess)(Object)remotePlayer).cape072$playerInfo(remoteInfo);
            CapeConfig.setEnabled(false); LocalCape.changed();
            require(account.equals(mc.player.getCapeTextureLocation()) && account.equals(selfInfo.getCapeTexture()), "Disabled module did not preserve account cape");
            CapeConfig.setPreset("migrator"); CapeConfig.setEnabled(true); LocalCape.changed();
            Identifier migrator = new Identifier("arcadium", CapeCatalog.find("migrator").texturePath());
            require(migrator.equals(mc.player.getCapeTextureLocation()) && migrator.equals(selfInfo.getCapeTexture()), "Self cape getters did not select the preset");
            require(remoteCape.equals(remoteInfo.getCapeTexture()) && remoteCape.equals(remotePlayer.getCapeTextureLocation()) && LocalCape.textureFor(remote) == null, "Another player's cape was changed");
            require(SkinChanger.hasCapeOverride(self) && account.equals(SkinChanger.capeTextureFor(self)), "Local selection changed account preview data");
            resetScope = true; resets = 0;
            try { CapeConfig.INSTANCE.select("cherry-blossom"); LocalCape.changed(); LocalCape.changed(); }
            finally { resetScope = false; }
            require(resets == 1, "Preset change must invalidate GPU cape profiles exactly once: " + resets);
            var nativeLayer = new CapeLayer(new PlayerRenderer(mc.getEntityRenderDispatcher()));
            Method predicate = Arrays.stream(CapeLayer.class.getDeclaredMethods()).filter(m -> m.getName().contains("localCapeWithoutPlayerInfo")).findFirst().orElseThrow();
            predicate.setAccessible(true);
            Operation<Boolean> missingInfo = args -> false;
            require((boolean)predicate.invoke(nativeLayer, mc.player, missingInfo), "Local preset was blocked by missing PlayerInfo");
            require(!(boolean)predicate.invoke(nativeLayer, remotePlayer, missingInfo), "Missing PlayerInfo was overridden for another player");
            GL11.glMatrixMode(GL11.GL_PROJECTION); GL11.glPushMatrix(); GL11.glLoadIdentity(); GL11.glOrtho(-2, 2, -2, 2, .1, 20);
            GL11.glMatrixMode(GL11.GL_MODELVIEW); GL11.glPushMatrix(); GL11.glLoadIdentity(); GL11.glTranslatef(0, 0, -3);
            layerScope = true; capeModelCalls = 0;
            try { nativeLayer.render(mc.player, 0, 0, 0, 0, 0, 0, .0625f); }
            finally { layerScope = false; GL11.glPopMatrix(); GL11.glMatrixMode(GL11.GL_PROJECTION); GL11.glPopMatrix(); GL11.glMatrixMode(mode); }
            require(capeModelCalls == 1, "Native CapeLayer did not reach the cape model: " + capeModelCalls);
            mc.screen = new SkinChangerScreen(oldScreen); LocalCape.changed();
            require(LocalCape.textureFor(self) == null && account.equals(mc.player.getCapeTextureLocation()) && account.equals(selfInfo.getCapeTexture()), "Account management preview was hidden by the local preset");
            mc.screen = oldScreen; LocalCape.changed();
            CapeConfig.setEnabled(false); LocalCape.changed();
            require(account.equals(mc.player.getCapeTextureLocation()), "Disabling the module did not restore the account cape");
            CapeConfig.setEnabled(true); LocalCape.changed();
            Identifier beforeReload = mc.player.getCapeTextureLocation();
            mc.reloadResources();
            require(beforeReload.equals(mc.player.getCapeTextureLocation()), "Resource reload changed the selected cape");
            mc.getTextureManager().bind(beforeReload);
            require(GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_WIDTH) == 64
                && GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_HEIGHT) == 32, "Cape upload missing after resource reload");
            require(GL11.glGetError() == GL11.GL_NO_ERROR, "OpenGL error during cape rendering or resource reload");
            return Map.of("passed", true, "catalog", loaded, "localOnlyGetters", true, "accountPreviewAndDisabledRestored", true,
                "nativeCapeModelCalls", capeModelCalls, "missingPlayerInfoPredicate", true, "presetChangeInvalidations", resets,
                "resourceReloadUploaded", true, "onlineAccountUsed", false);
        } finally {
            resetScope = false; layerScope = false; mc.screen = oldScreen;
            SkinChanger.INSTANCE.clearCapePreview(self);
            CapeConfig.setEnabled(oldEnabled); CapeConfig.setPreset(oldPreset); CapeConfig.INSTANCE.save(); LocalCape.changed();
            GlStateManager.activeTexture(GL13.GL_TEXTURE0);
            GlStateManager.bindTexture(oldBinding == 0 || GL11.glIsTexture(oldBinding) ? oldBinding : 0);
            GlStateManager.color4f(color.get(0), color.get(1), color.get(2), color.get(3));
            GlStateManager.activeTexture(active); GL11.glMatrixMode(mode);
        }
    }
    private static void require(boolean value, String message) { if (!value) throw new IllegalStateException(message); }
}
