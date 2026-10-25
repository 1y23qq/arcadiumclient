// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.modern.client.glint;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexMultiConsumer;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.rendertype.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;

public final class ModernGlint {
    public enum Role { HELD, GUI, DROPPED, FRAMED, SHINY }
    private record Key(Role role, int variant, int stroke) { }
    private static final ThreadLocal<GlintItemData.Data> CURRENT = ThreadLocal.withInitial(() -> GlintItemData.Data.EMPTY);
    private static final ThreadLocal<ItemDisplayContext> CONTEXT = ThreadLocal.withInitial(() -> ItemDisplayContext.NONE);
    private static final Map<Key, RenderType> TYPES = new HashMap<>();
    private static final Map<RenderType, Key> UNIFORMS = new java.util.IdentityHashMap<>();
    private static final java.util.Set<RenderType> OWN = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
    private static RenderType armor;
    private static RenderPipeline itemPipeline, armorPipeline;
    private static boolean installed;
    private ModernGlint() { }

    public static void initialize() { if (!installed) { installed = true; GlintConfig.INSTANCE.preload(); GlintBuffers.initialize(); } }
    public static GlintItemData.Data current() { return CURRENT.get(); }
    public static GlintItemData.Data scope(GlintItemData.Data data) { var old = CURRENT.get(); CURRENT.set(data); return old; }
    public static ItemDisplayContext scopeContext(ItemDisplayContext context) { var old = CONTEXT.get(); CONTEXT.set(context); return old; }
    public static GlintItemData.Data inspect(ItemStack stack) {
        boolean potion = stack.is(Items.POTION) || stack.is(Items.SPLASH_POTION) || stack.is(Items.LINGERING_POTION);
        if (!potion) return GlintItemData.Data.EMPTY;
        var contents = stack.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY);
        return new GlintItemData.Data(true, contents.getColor());
    }
    public static Role role(ItemDisplayContext context, GlintItemData.Data data) {
        return switch (context) {
            case GUI -> data.potion() && GlintConfig.INSTANCE.getPotionGlintBackground() ? Role.SHINY : Role.GUI;
            case GROUND -> Role.DROPPED;
            case FIXED -> Role.FRAMED;
            default -> Role.HELD;
        };
    }
    public static GlintEffectOptions options(Role role) {
        return switch (role) {
            case HELD -> GlintConfig.INSTANCE.getHeldItem();
            case GUI -> GlintConfig.INSTANCE.getGuiItem();
            case DROPPED -> GlintConfig.INSTANCE.getDroppedItem();
            case FRAMED -> GlintConfig.INSTANCE.getFramedItem();
            case SHINY -> GlintConfig.INSTANCE.getShinyPots();
        };
    }
    public static boolean owns(RenderType type) { return OWN.contains(type); }
    public static boolean armorType(RenderType type) { return type == armor && armor != null; }
    public static RenderType armor(RenderType original) {
        if (!GlintConfig.INSTANCE.getEnabled()) return original;
        if (armor == null) {
            armor = RenderType.create("arcadium_armor_glint", RenderSetup.builder(pipeline(true))
                .withTexture("Sampler0", ItemRenderer.ENCHANTED_GLINT_ARMOR)
                .setTextureTransform(TextureTransform.ARMOR_ENTITY_GLINT_TEXTURING)
                .setLayeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING).createRenderSetup());
            OWN.add(armor);
        }
        return armor;
    }
    private static RenderPipeline pipeline(boolean armor) {
        RenderPipeline present = armor ? armorPipeline : itemPipeline;
        if (present != null) return present;
        var builder = RenderPipeline.builder(RenderPipelines.MATRICES_PROJECTION_SNIPPET, RenderPipelines.FOG_SNIPPET, RenderPipelines.GLOBALS_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath("arcadium", "pipeline/" + (armor ? "armor_glint" : "item_glint")))
            .withVertexShader(Identifier.fromNamespaceAndPath("arcadium", "core/glint"))
            .withFragmentShader(Identifier.fromNamespaceAndPath("arcadium", "core/glint"))
            .withSampler("Sampler0").withDepthWrite(false).withCull(false)
            .withDepthTestFunction(DepthTestFunction.EQUAL_DEPTH_TEST).withBlend(BlendFunction.GLINT)
            .withVertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS);
        if (armor) builder.withShaderDefine("ARCADIUM_ARMOR");
        present = RenderPipelines.register(builder.build());
        if (armor) armorPipeline = present; else itemPipeline = present;
        return present;
    }
    private static RenderType type(Role role, int variant, int stroke) {
        return TYPES.computeIfAbsent(new Key(role, variant, stroke), key -> {
            var transform = new TextureTransform("arcadium_glint_" + role + "_" + stroke,
                () -> GlintMath.texture(options(role), stroke, net.minecraft.util.Util.getMillis(), Minecraft.getInstance().options.glintSpeed().get()));
            var builder = RenderSetup.builder(pipeline(variant >= 3)).withTexture("Sampler0", ItemRenderer.ENCHANTED_GLINT_ITEM).setTextureTransform(transform);
            if (variant % 3 == 1) builder.setOutputTarget(OutputTarget.ITEM_ENTITY_TARGET);
            RenderType result = RenderType.create("arcadium_glint_" + role + "_" + variant + "_" + stroke, builder.createRenderSetup());
            OWN.add(result);
            if (variant >= 3) UNIFORMS.put(result, key);
            return result;
        });
    }
    public static java.util.List<RenderType> specialTypes(java.util.List<RenderType> nativeTypes) {
        if (!GlintConfig.INSTANCE.getEnabled()) return nativeTypes;
        var result = new java.util.ArrayList<RenderType>(nativeTypes.size() + 1);
        Role role = role(CONTEXT.get(), current());
        for (var nativeType : nativeTypes) {
            if (nativeType == RenderTypes.glint() || nativeType == RenderTypes.glintTranslucent() || nativeType == RenderTypes.entityGlint()) {
                int variant = nativeType == RenderTypes.glintTranslucent() ? 4 : nativeType == RenderTypes.entityGlint() ? 5 : 3;
                result.add(type(role, variant, 0)); result.add(type(role, variant, 1));
            } else result.add(nativeType);
        }
        return result;
    }
    public static org.joml.Vector4fc uniform(RenderType type, org.joml.Vector4fc original) {
        var key = UNIFORMS.get(type);
        if (!armorType(type) && key == null) return original;
        int argb = key == null ? GlintConfig.INSTANCE.getArmorColor().getArgb() : GlintMath.color(options(key.role()), key.stroke(), false, GlintItemData.Data.EMPTY);
        return new org.joml.Vector4f((argb >> 16 & 255) / 255f, (argb >> 8 & 255) / 255f, (argb & 255) / 255f, (argb >>> 24) / 255f);
    }
    public static MultiBufferSource buffers(MultiBufferSource original, ItemDisplayContext context) {
        if (!GlintConfig.INSTANCE.getEnabled()) return original;
        var data = current();
        Role role = role(context, data);
        var settings = options(role);
        return nativeType -> {
            int variant = nativeType == RenderTypes.glintTranslucent() ? 1 : nativeType == RenderTypes.entityGlint() ? 2 : 0;
            if (nativeType != RenderTypes.glint() && nativeType != RenderTypes.glintTranslucent() && nativeType != RenderTypes.entityGlint()) return original.getBuffer(nativeType);
            boolean based = context == ItemDisplayContext.GUI && GlintConfig.INSTANCE.getPotionBasedColor();
            var first = new GlintVertices(original.getBuffer(type(role, variant, 0)), GlintMath.color(settings, 0, based, data));
            var second = new GlintVertices(original.getBuffer(type(role, variant, 1)), GlintMath.color(settings, 1, based, data));
            return VertexMultiConsumer.create(first, second);
        };
    }
}
