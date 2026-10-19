// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.client.chat;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

public final class ChatHistoryStore {
    public static final int MAX_MESSAGES = 16384, MAX_TEXT_BYTES = 65536, MAX_FILE_BYTES = 16 * 1024 * 1024;
    private static final long MAGIC = 0x4152434443484154L;
    private final Path folder;
    public record Entry(String json, long received) { }

    public ChatHistoryStore(Path folder) { this.folder = folder.toAbsolutePath().normalize(); }

    public Path path(String server) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(server.getBytes(StandardCharsets.UTF_8));
            return folder.resolve(HexFormat.of().formatHex(hash) + ".bin");
        } catch (NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
    }

    public List<Entry> load(String server) throws IOException {
        Path file = path(server);
        if (!Files.exists(file, LinkOption.NOFOLLOW_LINKS)) return List.of();
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) || Files.size(file) > MAX_FILE_BYTES)
            throw new IOException("Invalid chat history file");
        try (DataInputStream input = new DataInputStream(new BufferedInputStream(Files.newInputStream(file)))) {
            if (input.readLong() != MAGIC || input.readInt() != 1) throw new IOException("Unsupported chat history format");
            int size = input.readInt();
            if (size < 0 || size > MAX_MESSAGES) throw new IOException("Invalid chat history count");
            ArrayList<Entry> entries = new ArrayList<>(size);
            for (int i = 0; i < size; i++) {
                long received = input.readLong();
                int length = input.readInt();
                if (length < 0 || length > MAX_TEXT_BYTES) throw new IOException("Invalid chat message length");
                byte[] data = input.readNBytes(length);
                if (data.length != length) throw new IOException("Truncated chat history");
                entries.add(new Entry(new String(data, StandardCharsets.UTF_8), received));
            }
            if (input.read() != -1) throw new IOException("Unexpected chat history data");
            return List.copyOf(entries);
        }
    }

    public void save(String server, List<Entry> entries) throws IOException {
        Files.createDirectories(folder);
        Path target = path(server), temporary = Files.createTempFile(folder, ".chat-", ".tmp");
        try {
            ArrayList<byte[]> messages = new ArrayList<>();
            long bytes = 16;
            for (Entry entry : entries) {
                byte[] json = entry.json.getBytes(StandardCharsets.UTF_8);
                if (json.length > MAX_TEXT_BYTES) continue;
                if (messages.size() == MAX_MESSAGES || bytes + 12 + json.length > MAX_FILE_BYTES) break;
                messages.add(json);
                bytes += 12L + json.length;
            }
            try (DataOutputStream output = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(temporary)))) {
                output.writeLong(MAGIC); output.writeInt(1); output.writeInt(messages.size());
                int saved = 0;
                for (Entry entry : entries) {
                    if (saved == messages.size()) break;
                    byte[] json = entry.json.getBytes(StandardCharsets.UTF_8);
                    if (json.length > MAX_TEXT_BYTES) continue;
                    output.writeLong(entry.received); output.writeInt(json.length); output.write(json);
                    saved++;
                }
            }
            try { Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException ignored) { Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temporary); }
    }
}
