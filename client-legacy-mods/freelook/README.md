## Freelook Mod
Allows you to rotate your camera freely around your character!

---

### Features
- Multiple Camera Perspectives
- Customizable Activation Style
- Camera Cycle Integration
- Smooth Transitions

### Server Opt-Opt
Some servers may consider freelook a competitive advantage. As of 2.0.0, Freelook includes a simple opt‑out protocol.

#### How it works
1. When a player joins, the client sends a `freelook:handshake` packet.
2. If the server wants to disable freelook, it responds with a `freelook:disable` packet.
3. Then the player cannot use freelook for that session.

<details>
<summary>Click to expand code example</summary>

```java
public class Example implements ModInitializer {
    @Override
    public void onInitialize() {
        PayloadTypeRegistry.clientboundPlay().register(DisableModPayload.TYPE, DisableModPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(HandshakePayload.TYPE, HandshakePayload.CODEC);

        ServerPlayNetworking.registerGlobalReceiver(HandshakePayload.TYPE,
                (payload, ctx) -> ServerPlayNetworking.send(ctx.player(), new DisableModPayload())
        );
    }

    public record DisableModPayload() implements CustomPacketPayload {
        public static final Type<DisableModPayload> TYPE = new Type<>(Identifier.parse("freelook:disable"));
        public static final StreamCodec<RegistryFriendlyByteBuf,  DisableModPayload> CODEC = StreamCodec.unit(new DisableModPayload());

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record HandshakePayload() implements CustomPacketPayload {
        public static final Type<HandshakePayload> TYPE = new Type<>(Identifier.parse("freelook:handshake"));
        public static final StreamCodec<RegistryFriendlyByteBuf, HandshakePayload> CODEC = StreamCodec.unit(new HandshakePayload());

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
```

</details>

---

###### Code licensed under LGPL 3.0.
