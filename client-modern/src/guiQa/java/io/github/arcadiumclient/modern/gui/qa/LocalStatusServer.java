// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 Arcadium contributors.

package io.github.arcadiumclient.modern.gui.qa;

import com.google.gson.GsonBuilder;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import net.minecraft.SharedConstants;

final class LocalStatusServer implements AutoCloseable {
    private final ServerSocket server;
    private volatile boolean closed;
    private volatile int requests;

    LocalStatusServer() throws IOException {
        server = new ServerSocket(0, 4, InetAddress.getByName("127.0.0.1"));
        Thread thread = new Thread(this::serve, "Arcadium QA Status Fixture");
        thread.setDaemon(true);
        thread.start();
    }

    String address() { return "127.0.0.1:" + server.getLocalPort(); }
    int requests() { return requests; }

    private void serve() {
        while (!closed) try (Socket socket = server.accept()) {
            socket.setSoTimeout(4000);
            var input = new DataInputStream(socket.getInputStream());
            var output = new DataOutputStream(socket.getOutputStream());
            readPacket(input);
            byte[] request = readPacket(input);
            if (request.length != 1 || request[0] != 0) continue;
            var json = new GsonBuilder().create().toJson(Map.of(
                    "version", Map.of("name", "Arcadium QA", "protocol", SharedConstants.getProtocolVersion()),
                    "players", Map.of("max", 20, "online", 3),
                    "description", Map.of("text", "Local server preview QA", "color", "aqua")));
            var bytes = new ByteArrayOutputStream();
            var payload = new DataOutputStream(bytes);
            payload.writeByte(0);
            byte[] text = json.getBytes(StandardCharsets.UTF_8);
            writeVarInt(payload, text.length);
            payload.write(text);
            writePacket(output, bytes.toByteArray());
            byte[] ping = readPacket(input);
            if (ping.length == 9 && ping[0] == 1) writePacket(output, ping);
            requests++;
        } catch (IOException ignored) {
            if (closed) return;
        }
    }

    private static byte[] readPacket(DataInputStream input) throws IOException {
        int length = readVarInt(input);
        if (length < 1 || length > 65536) throw new IOException("Invalid QA status packet length");
        byte[] packet = input.readNBytes(length);
        if (packet.length != length) throw new IOException("Truncated QA status packet");
        return packet;
    }

    private static void writePacket(DataOutputStream output, byte[] packet) throws IOException {
        writeVarInt(output, packet.length);
        output.write(packet);
        output.flush();
    }

    private static int readVarInt(DataInputStream input) throws IOException {
        int result = 0;
        for (int index = 0; index < 5; index++) {
            int current = input.readUnsignedByte();
            result |= (current & 127) << index * 7;
            if ((current & 128) == 0) return result;
        }
        throw new IOException("Oversized QA status integer");
    }

    private static void writeVarInt(DataOutputStream output, int value) throws IOException {
        do {
            int current = value & 127;
            value >>>= 7;
            output.writeByte(value == 0 ? current : current | 128);
        } while (value != 0);
    }

    @Override public void close() throws IOException { closed = true; server.close(); }
}
