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

public record HandshakePayload() implements /*? if >1.8.9 {*//*CustomPacketPayload*//*?} else {*/PacketPayload/*?}*/ {
    //? if >1.8.9 {
    /*public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("freelook", "handshake");
    public static final Type<HandshakePayload> TYPE = new Type<>(ID);
    public static final StreamCodec<RegistryFriendlyByteBuf, HandshakePayload> CODEC = StreamCodec.unit(new HandshakePayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
    *///?} else {
    // OSL would put ChannelResourceLocations.from("freelook", "handshake") on the wire as "freelook|handshake".
    // Parsing the string keeps the same "freelook:handshake" id the newer versions use.
    public static final NamespacedIdentifier ID = ChannelRegistry.register(
            StringChannelIdentifierParser.fromString("freelook:handshake"), false, true
    );

    @Override
    public void read(PacketBuffer buffer) {
    }

    @Override
    public void write(PacketBuffer buffer) {
    }
    //?}
}
