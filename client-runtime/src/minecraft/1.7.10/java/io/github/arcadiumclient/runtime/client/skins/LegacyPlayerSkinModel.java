// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.client.skins;

import io.github.arcadiumclient.runtime.client.features.SkinLayersBridge;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.living.player.ClientPlayerEntity;
import net.minecraft.client.render.model.ModelPart;
import net.minecraft.client.render.model.entity.HumanoidModel;
import org.lwjgl.opengl.GL11;

public final class LegacyPlayerSkinModel extends HumanoidModel {
    private final boolean slim;
    private LegacyPlayerSkins.Skin skin;
    private ClientPlayerEntity owner;

    public LegacyPlayerSkinModel(boolean slim) {
        super(0f, 0f, 64, 64);
        this.slim = slim;
        parts.clear();
        cape = new ModelPart(this, 0, 0).setTextureSize(64, 32);
        cape.addBox(-5, 0, -1, 10, 16, 1, 0f);
        int arm = slim ? 3 : 4;
        head = part(0, 0, 0, 32, 0, -4, -8, -4, 8, 8, 8, 0, 0);
        body = part(1, 16, 16, 16, 32, -4, 0, -2, 8, 12, 4, 0, 0);
        leftArm = part(2, 32, 48, 48, 48, -1, -2, -2, arm, 12, 4, 5, 2);
        rightArm = part(3, 40, 16, 40, 32, slim ? -2 : -3, -2, -2, arm, 12, 4, -5, 2);
        leftLeg = part(4, 16, 48, 0, 48, -2, 0, -2, 4, 12, 4, 1.9f, 12);
        rightLeg = part(5, 0, 16, 0, 32, -2, 0, -2, 4, 12, 4, -1.9f, 12);
        hat.visible = false;
    }

    public void appearance(ClientPlayerEntity player, LegacyPlayerSkins.Skin value) { owner = player; skin = value; }
    public ClientPlayerEntity owner() { return owner; }
    public LegacyPlayerSkins.Skin skin() { return skin; }

    private ModelPart part(int index, int u, int v, int layerU, int layerV, float x, float y, float z,
                           int width, int height, int depth, float pivotX, float pivotY) {
        return new LayerPart(index, u, v, layerU, layerV, x, y, z, width, height, depth, pivotX, pivotY);
    }

    private final class LayerPart extends ModelPart {
        private final int part;
        private final ModelPart layer;
        private final LegacySkinMesh.Box box;

        private LayerPart(int part, int u, int v, int layerU, int layerV, float x, float y, float z,
                          int width, int height, int depth, float pivotX, float pivotY) {
            super(LegacyPlayerSkinModel.this, u, v);
            this.part = part;
            addBox(x, y, z, width, height, depth, 0f);
            setPos(pivotX, pivotY, 0);
            layer = new ModelPart(LegacyPlayerSkinModel.this, layerU, layerV);
            layer.addBox(x, y, z, width, height, depth, part == 0 ? .5f : .25f);
            box = new LegacySkinMesh.Box(layerU, layerV, width, height, depth, x, y, z);
        }

        @Override public void render(float scale) {
            super.render(scale);
            if (!visible || invisible || skin == null) return;
            var viewer = Minecraft.getInstance().player;
            int distance = SkinLayersBridge.distance();
            if (SkinLayersBridge.part(part) && owner != null && (viewer == null || viewer.squaredDistanceTo(owner) <= distance * distance)) {
                GL11.glPushMatrix();
                try {
                    if (translateX != 0 || translateY != 0 || translateZ != 0) GL11.glTranslatef(translateX, translateY, translateZ);
                    transform(scale);
                    GL11.glScalef(scale, scale, scale);
                    skin.draw(part, slim, box);
                } finally { GL11.glPopMatrix(); }
            } else {
                layer.x = this.x; layer.y = this.y; layer.z = this.z;
                layer.rotationX = rotationX; layer.rotationY = rotationY; layer.rotationZ = rotationZ;
                layer.translateX = translateX; layer.translateY = translateY; layer.translateZ = translateZ;
                layer.render(scale);
            }
        }
    }
}
