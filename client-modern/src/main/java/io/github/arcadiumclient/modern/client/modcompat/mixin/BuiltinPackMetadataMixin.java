// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.modern.client.modcompat.mixin;

import com.google.gson.JsonObject;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import io.github.arcadiumclient.modern.client.modcompat.BuiltinPackMetadata;
import java.io.InputStream;
import net.fabricmc.fabric.impl.resource.pack.ModResourcePackCreator;
import net.minecraft.server.packs.AbstractPackResources;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.metadata.MetadataSectionType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(AbstractPackResources.class)
public class BuiltinPackMetadataMixin {
    @ModifyExpressionValue(method = "getMetadataFromStream", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/GsonHelper;parse(Ljava/io/Reader;)Lcom/google/gson/JsonObject;"))
    private static <T> JsonObject arcadium$builtinFormat(JsonObject parsed, MetadataSectionType<T> type, InputStream stream, PackLocationInfo location) {
        return "pack".equals(type.name()) ? BuiltinPackMetadata.repair(location.id(), location.source() == ModResourcePackCreator.RESOURCE_PACK_SOURCE, parsed) : parsed;
    }
}
