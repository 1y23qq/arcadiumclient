// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.optimizations;

import java.awt.image.BufferedImage;
import java.lang.reflect.Proxy;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.BitSet;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class GpuVoxelSkinImageAccessTest {
    @Test void ActualPinnedFaceUvDomainsExcludePaddingAndIncludeEveryFaceCell()throws Throwable{
        String jar=System.getProperty("arcadium.voxelSkin072.pinnedJar");assumeTrue(jar!=null);
        try(URLClassLoader loader=new URLClassLoader(new java.net.URL[]{Path.of(jar).toUri().toURL()},getClass().getClassLoader())){
            var access=new GpuVoxelSkinImageAccess(loader);
            Class<?> configType=Class.forName("org.polyfrost.skinlayers.config.SkinLayersConfig",true,loader);Object config=configType.getConstructor().newInstance();
            assertEquals(0xFC0,GpuVoxelSkinBridge.readEnabledMask(config));configType.getMethod("setEnableJacket",boolean.class).invoke(config,false);
            assertEquals(0x7C0,GpuVoxelSkinBridge.readEnabledMask(config));configType.getMethod("setEnableHat",boolean.class).invoke(config,false);
            assertEquals(0x780,GpuVoxelSkinBridge.readEnabledMask(config));
            for(boolean slim:new boolean[]{false,true}){
                BitSet union=new BitSet(4096);int[] expected={384,224,224,slim?192:224,slim?192:224,352};
                for(int part=6;part<12;part++){BitSet domain=access.domain(part,slim);assertEquals(expected[part-6],domain.cardinality());assertTrue(domain.length()<=4096);union.or(domain);}
                assertEquals(slim?1568:1632,union.cardinality());assertFalse(union.get(0));assertTrue(union.get(8*64+40));
            }
            assertNull(access.imageIdentity(new Object()));assertNull(access.snapshot(new BufferedImage(64,32,BufferedImage.TYPE_INT_ARGB)));
            Class<?> imageInterface=Class.forName("org.polyfrost.skinlayers.skin.SkinTextureImageAccess",true,loader);
            BufferedImage image=new BufferedImage(64,64,BufferedImage.TYPE_INT_ARGB);image.setRGB(7,9,0xC0FFFFFF);
            Object source=Proxy.newProxyInstance(loader,new Class<?>[]{imageInterface},(proxy,method,args)->image);
            assertSame(image,access.imageIdentity(source));byte[] snapshot=access.snapshot(image);assertEquals(192,snapshot[9*64+7]&255);
            image.setRGB(7,9,0xFFFFFFFF);assertEquals(192,snapshot[9*64+7]&255);assertEquals(255,access.snapshot(image)[9*64+7]&255);
        }
    }
}
