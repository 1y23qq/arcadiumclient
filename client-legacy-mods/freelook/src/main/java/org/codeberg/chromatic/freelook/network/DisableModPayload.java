package org.codeberg.chromatic.freelook.network;

//? if >1.8.9 {
/*import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
*///?} else {
import net.ornithemc.osl.core.api.util.NamespacedIdentifier;
import net.ornithemc.osl.networking.api.ChannelRegistry;
import net.ornithemc.osl.networking.api.PacketBuffer;
import net.ornithemc.osl.networking.api.PacketPayload;
import net.ornithemc.osl.networking.api.StringChannelIdentifierParser;
//?}

public record DisableModPayload() implements /*? if >1.8.9 {*//*CustomPacketPayload*//*?} else {*/PacketPayload/*?}*/ {
    //? if >1.8.9 {
    /*public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("freelook", "disable");
    public static final Type<DisableModPayload> TYPE = new Type<>(ID);
    public static final StreamCodec<RegistryFriendlyByteBuf, DisableModPayload> CODEC = StreamCodec.unit(new DisableModPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
    *///?} else {
    // OSL would put ChannelResourceLocations.from("freelook", "disable") on the wire as "freelook|disable".
    // Parsing the string keeps the same "freelook:disable" id the newer versions use.
    public static final NamespacedIdentifier ID = ChannelRegistry.register(
            StringChannelIdentifierParser.fromString("freelook:disable"), true, false
    );

    @Override
    public void read(PacketBuffer buffer) {
    }

    @Override
    public void write(PacketBuffer buffer) {
    }
    //?}
}
