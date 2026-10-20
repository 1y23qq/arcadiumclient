/*
 * This file is part of OneConfig.
 * OneConfig - Next Generation Config Library for Minecraft: Java Edition
 * Copyright (C) 2021~2024 Polyfrost.
 *   <https://polyfrost.org> <https://github.com/Polyfrost/>
 *
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 *   OneConfig is licensed under the terms of version 3 of the GNU Lesser
 * General Public License as published by the Free Software Foundation, AND
 * under the Additional Terms Applicable to OneConfig, as published by Polyfrost,
 * either version 1.0 of the Additional Terms, or (at your option) any later
 * version.
 *
 *   This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 *   You should have received a copy of the GNU Lesser General Public
 * License.  If not, see <https://www.gnu.org/licenses/>. You should
 * have also received a copy of the Additional Terms Applicable
 * to OneConfig, as published by Polyfrost. If not, see
 * <https://polyfrost.org/legal/oneconfig/additional-terms>
 */

package org.polyfrost.oneconfig.api.hypixel.v1.internal;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.hypixel.modapi.HypixelModAPI;
import net.hypixel.modapi.packet.impl.clientbound.ClientboundHelloPacket;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.ApiStatus;
import org.polyfrost.oneconfig.api.event.v1.EventManager;
import org.polyfrost.oneconfig.api.event.v1.events.HypixelLocationEvent;
//? if = 1.7.10 {
/*import net.hypixel.modapi.HypixelModAPIImplementation;
import net.hypixel.modapi.packet.HypixelPacket;
import net.hypixel.modapi.serializer.PacketSerializer;
import net.ornithemc.osl.core.api.util.NamespacedIdentifier;
import net.ornithemc.osl.core.api.util.NamespacedIdentifiers;
import net.ornithemc.osl.networking.api.ChannelRegistry;
import net.ornithemc.osl.networking.api.client.ClientPlayNetworking;
import java.util.HashMap;
import java.util.Map;
*///?}

@ApiStatus.Internal
public final class HypixelApiInternalsImpl implements HypixelApiInternals {
    private static final Logger LOGGER = LogManager.getLogger("Arcadium Options/HypixelAPI");

    private volatile boolean onHypixel = false;

    public HypixelApiInternalsImpl() {
        LOGGER.info("Registering Hypixel API hello/disconnect handlers");
        HypixelModAPI.getInstance().createHandler(ClientboundHelloPacket.class, p -> onHypixel = true);
        //? if = 1.7.10
        //initLegacyTransport();
        //? if > 1.8.9 {
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> onHypixel = false);
        //?} else
        //ClientConnectionEvents.DISCONNECT.register(context -> onHypixel = false);
    }

    //? if = 1.7.10 {
    /*private void initLegacyTransport() {
        HypixelModAPI api = HypixelModAPI.getInstance();
        Map<String, NamespacedIdentifier> channels = new HashMap<>();
        for (String channel : api.getRegistry().getIdentifiers()) {
            channels.put(channel, ChannelRegistry.register(NamespacedIdentifiers.parse(channel),
                api.getRegistry().getClientboundIdentifiers().contains(channel),
                api.getRegistry().getServerboundIdentifiers().contains(channel)));
        }
        api.setModImplementation(new HypixelModAPIImplementation() {
            @Override
            public void onInit() {
                for (String channel : api.getRegistry().getClientboundIdentifiers()) {
                    ClientPlayNetworking.registerListener(channels.get(channel), (context, buffer) -> {
                        context.ensureOnMainThread();
                        try {
                            api.handle(channel, new PacketSerializer(buffer));
                        } catch (RuntimeException error) {
                            LOGGER.warn("Invalid Hypixel payload on {}", channel, error);
                        }
                    });
                }
            }

            @Override
            public boolean sendPacket(HypixelPacket packet) {
                NamespacedIdentifier channel = channels.get(packet.getIdentifier());
                if (!onHypixel || channel == null || !ClientPlayNetworking.isPlayReady()) return false;
                ClientPlayNetworking.sendNoCheck(channel, buffer -> packet.write(new PacketSerializer(buffer)));
                return true;
            }

            @Override
            public boolean isConnectedToHypixel() {
                return onHypixel;
            }
        });
    }
    *///?}

    @Override
    public boolean isOnHypixel() {
        return onHypixel;
    }

    @Override
    @ApiStatus.Internal
    public void postLocationEvent() {
        EventManager.INSTANCE.post(HypixelLocationEvent.INSTANCE);
    }
}
