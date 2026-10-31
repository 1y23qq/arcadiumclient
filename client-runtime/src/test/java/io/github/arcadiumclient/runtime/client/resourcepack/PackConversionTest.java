// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.resourcepack;

import io.github.arcadiumclient.shared.packs.PackConversion;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.io.*;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.zip.*;
import javax.imageio.ImageIO;
import com.google.gson.JsonParser;
import static org.junit.jupiter.api.Assertions.*;

class PackConversionTest {
    @TempDir Path root;
    @Test void legacyZipBecomesUsableInBothModernVersionsWithoutChangingTheOriginal() throws Exception {
        Path source = zip("old.zip", Map.of("pack.mcmeta", utf("{\"pack\":{\"pack_format\":1,\"description\":\"Old pack\"}}"),
            "assets/minecraft/textures/items/wood_sword.png", png(16,16,0xFF112233),
            "assets/minecraft/textures/blocks/wool_colored_red.png", png(16,16,0xFF445566),
            "assets/minecraft/models/item/wooden_sword.json", utf("{\"parent\":\"builtin/generated\",\"textures\":{\"layer0\":\"items/wood_sword\"}}")));
        byte[] original = Files.readAllBytes(source);
        for (int version : new int[]{75,88}) {
            var target = new PackConversion.Target(version,0);
            Path converted = PackConversion.prepare(source, root.resolve("cache"), target, null);
            assertNotEquals(source, converted);
            try (ZipFile pack = new ZipFile(converted.toFile())) {
                assertPixel(pack,"assets/minecraft/textures/item/wooden_sword.png",0xFF112233);
                assertPixel(pack,"assets/minecraft/textures/block/red_wool.png",0xFF445566);
                String model = read(pack,"assets/minecraft/models/item/wooden_sword.json");
                assertTrue(model.contains("item/wooden_sword"));
                assertTrue(model.contains("item/generated"));
                assertTrue(PackConversion.compatible(JsonParser.parseString(read(pack,"pack.mcmeta")).getAsJsonObject().getAsJsonObject("pack"), target));
                assertNull(pack.getEntry("assets/minecraft/textures/item/netherite_sword.png"));
            }
            assertEquals(converted,PackConversion.prepare(source,root.resolve("cache"),target,null));
            assertTrue(PackConversion.prepared(source,target));
        }
        assertArrayEquals(original,Files.readAllBytes(source));
    }
    @Test void directoryPacksAndCacheInvalidation() throws Exception {
        Path source = root.resolve("folder/nested/pack"); Files.createDirectories(source);
        Files.writeString(source.resolve("pack.mcmeta"),"{\"pack\":{\"pack_format\":1,\"description\":\"Folder\"}}");
        Path icon = source.resolve("assets/minecraft/textures/items/diamond_sword.png"); Files.createDirectories(icon.getParent());
        Files.write(icon,png(16,16,0xFFFF0000));
        Path first = PackConversion.prepare(source,root.resolve("cache"),new PackConversion.Target(75,0),null);
        Files.write(icon,png(32,32,0xFF00FF00));
        Path second = PackConversion.prepare(source,root.resolve("cache"),new PackConversion.Target(75,0),null);
        assertNotEquals(first,second);
        try (ZipFile pack = new ZipFile(second.toFile())) { assertPixel(pack,"assets/minecraft/textures/item/diamond_sword.png",0xFF00FF00); }
        assertTrue(Files.isDirectory(source));
    }
    @Test void reverseConversionProvidesActualLegacyNamesAndPreservesVanillaAtlasPixels() throws Exception {
        Path source = zip("modern.zip",Map.of("pack.mcmeta",utf("{\"pack\":{\"min_format\":[75,0],\"max_format\":[75,0],\"description\":\"New pack\"}}"),
            "assets/minecraft/textures/item/wooden_sword.png",png(16,16,0xFF010203),
            "assets/minecraft/textures/block/red_wool.png",png(16,16,0xFF102030),
            "assets/minecraft/textures/gui/sprites/hud/hotbar.png",png(182,22,0xFFABCDEF)));
        Path converted=PackConversion.prepare(source,root.resolve("cache"),new PackConversion.Target(1,0),path -> {
            try { return path.endsWith("widgets.png") ? png(256,256,0xFF123456) : null; } catch(Exception failure) { throw new RuntimeException(failure); }
        });
        try (ZipFile pack=new ZipFile(converted.toFile())) {
            assertPixel(pack,"assets/minecraft/textures/items/wood_sword.png",0xFF010203);
            assertPixel(pack,"assets/minecraft/textures/blocks/wool_colored_red.png",0xFF102030);
            BufferedImage atlas=ImageIO.read(pack.getInputStream(pack.getEntry("assets/minecraft/textures/gui/widgets.png")));
            assertEquals(0xFFABCDEF,atlas.getRGB(2,2)); assertEquals(0xFF123456,atlas.getRGB(250,250));
        }
    }
    @Test void nativePacksDoNotCreateConvertedCopies() throws Exception {
        Path source=zip("native.zip",Map.of("pack.mcmeta",utf("{\"pack\":{\"pack_format\":1,\"description\":\"Native\"}}")));
        assertEquals(source.toAbsolutePath(),PackConversion.prepare(source,root.resolve("cache"),new PackConversion.Target(1,0),null));
        assertFalse(Files.exists(root.resolve("cache")));
    }
    @Test void atlasSlicesUseResolutionScaling() throws Exception {
        Path source=zip("hd.zip",Map.of("pack.mcmeta",utf("{\"pack\":{\"pack_format\":1,\"description\":\"HD\"}}"),"assets/minecraft/textures/gui/widgets.png",png(512,512,0xFF998877)));
        Path converted=PackConversion.prepare(source,root.resolve("cache"),new PackConversion.Target(75,0),null);
        try (ZipFile pack=new ZipFile(converted.toFile())) {
            BufferedImage icon=ImageIO.read(pack.getInputStream(pack.getEntry("assets/minecraft/textures/gui/sprites/hud/hotbar.png")));
            assertEquals(364,icon.getWidth()); assertEquals(44,icon.getHeight()); assertEquals(0xFF998877,icon.getRGB(0,0));
        }
    }
    @Test void conversionRejectsTraversalAndKeepsOriginalBytes() throws Exception {
        Path source=zip("unsafe.zip",Map.of("pack.mcmeta",utf("{\"pack\":{\"pack_format\":1}}"),"../escape.txt",utf("unsafe")));
        byte[] before=Files.readAllBytes(source);
        assertThrows(IOException.class,()->PackConversion.prepare(source,root.resolve("cache"),new PackConversion.Target(75,0),null));
        assertArrayEquals(before,Files.readAllBytes(source)); assertFalse(Files.exists(root.resolve("escape.txt")));
    }
    @Test void catalogPreviewDoesNotConvertTexturePayloads() throws Exception {
        Path source=zip("preview.zip",Map.of("pack.mcmeta",utf("{\"pack\":{\"pack_format\":1,\"description\":\"Preview\"}}"),"assets/minecraft/textures/items/diamond_sword.png",png(16,16,0xFF112233)));
        Path preview=PackConversion.preview(source,root.resolve("cache"),new PackConversion.Target(75,0));
        try(var pack=new ZipFile(preview.toFile())) { assertEquals(1,pack.size());assertNotNull(pack.getEntry("pack.mcmeta")); }
        Path full=PackConversion.prepare(source,root.resolve("cache"),new PackConversion.Target(75,0),null);
        assertNotEquals(preview,full);
        try(var pack=new ZipFile(full.toFile())) { assertPixel(pack,"assets/minecraft/textures/item/diamond_sword.png",0xFF112233); }
    }
    @Test void modernEntityPathsAndBedAtlasesAreAdaptedFor262() throws Exception {
        Path source=zip("modern-bed.zip",Map.of("pack.mcmeta",utf("{\"pack\":{\"pack_format\":75,\"description\":\"Beds\"}}"),"assets/minecraft/textures/entity/blaze.png",png(64,32,0xFF553311),"assets/minecraft/textures/entity/bed/red.png",png(64,64,0xFF102030)));
        Path full=PackConversion.prepare(source,root.resolve("cache"),new PackConversion.Target(88,0),null);
        try(var pack=new ZipFile(full.toFile())) {
            assertPixel(pack,"assets/minecraft/textures/entity/blaze/blaze.png",0xFF553311);
            assertPixel(pack,"assets/minecraft/textures/block/red_bed_head_up.png",0xFF102030);
            assertNotNull(pack.getEntry("assets/minecraft/textures/block/red_bed_foot_west.png"));
        }
    }
    @Test void inverseMappingsKeepAllLegacyAliasesForSharedModernNames() throws Exception {
        Path source=zip("dyes.zip",Map.of("pack.mcmeta",utf("{\"pack\":{\"pack_format\":75,\"description\":\"Dyes\"}}"),"assets/minecraft/textures/item/bone_meal.png",png(16,16,0xFF123456)));
        Path full=PackConversion.prepare(source,root.resolve("cache"),new PackConversion.Target(1,0),null);
        try(var pack=new ZipFile(full.toFile())) {assertPixel(pack,"assets/minecraft/textures/items/dye_powder_white.png",0xFF123456);assertPixel(pack,"assets/minecraft/textures/items/dye_white.png",0xFF123456);}
    }
    @Test void normalMapsFollowRenamesAndModOwnedResourcePathsStayIntact() throws Exception {
        Path source=zip("normal.zip",Map.of("pack.mcmeta",utf("{\"pack\":{\"pack_format\":1,\"description\":\"Normal\"}}"),"assets/minecraft/textures/items/wood_sword_n.png",png(16,16,0xFF112233),"assets/example/textures/items/wood_sword.png",png(16,16,0xFF334455)));
        Path full=PackConversion.prepare(source,root.resolve("cache"),new PackConversion.Target(75,0),null);
        try(var pack=new ZipFile(full.toFile())) {assertPixel(pack,"assets/minecraft/textures/item/wooden_sword_n.png",0xFF112233);assertPixel(pack,"assets/example/textures/items/wood_sword.png",0xFF334455);assertNull(pack.getEntry("assets/example/textures/item/wooden_sword.png"));}
    }
    private Path zip(String name,Map<String,byte[]> files) throws IOException { Path path=root.resolve(name); try(var zip=new ZipOutputStream(Files.newOutputStream(path))) { for(var file:files.entrySet()) { zip.putNextEntry(new ZipEntry(file.getKey())); zip.write(file.getValue());zip.closeEntry(); } } return path; }
    private static byte[] utf(String value) { return value.getBytes(java.nio.charset.StandardCharsets.UTF_8); }
    private static byte[] png(int w,int h,int color) throws IOException { var image=new BufferedImage(w,h,BufferedImage.TYPE_INT_ARGB); for(int y=0;y<h;y++)for(int x=0;x<w;x++)image.setRGB(x,y,color);var out=new ByteArrayOutputStream();ImageIO.write(image,"png",out);return out.toByteArray(); }
    private static String read(ZipFile file,String name) throws IOException { return new String(file.getInputStream(file.getEntry(name)).readAllBytes(),java.nio.charset.StandardCharsets.UTF_8); }
    private static void assertPixel(ZipFile file,String name,int color) throws IOException { assertNotNull(file.getEntry(name),name);assertEquals(color,ImageIO.read(file.getInputStream(file.getEntry(name))).getRGB(0,0)); }
}
