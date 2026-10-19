// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.client.skins;

import java.awt.image.BufferedImage;
import net.minecraft.client.render.texture.HttpTexture;
import net.minecraft.client.resource.manager.ResourceManager;

public final class UploadedSkinTexture extends HttpTexture {
    public UploadedSkinTexture(BufferedImage image) {
        super(null, "", null, null);
        setImage(image);
    }

    @Override public void load(ResourceManager manager) { }
    @Override protected void download() { }
}
