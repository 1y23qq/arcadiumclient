// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.client.chat;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.net.IDN;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import javax.net.ssl.SNIHostName;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

public final class PublicImageFetcher {
    public static final int MAX_BYTES = 4 * 1024 * 1024, MAX_DIMENSION = 2048, MAX_PIXELS = 4 * 1024 * 1024;
    @FunctionalInterface public interface Resolver { InetAddress[] resolve(String host) throws IOException; }
    @FunctionalInterface public interface Transport { Reply get(URI uri, InetAddress address) throws IOException; }
    public record Reply(int status, Map<String, String> headers, byte[] body) { }
    private final Resolver resolver;
    private final Transport transport;

    public PublicImageFetcher() { this(InetAddress::getAllByName, PublicImageFetcher::request); }
    public PublicImageFetcher(Resolver resolver, Transport transport) { this.resolver = resolver; this.transport = transport; }

    public BufferedImage fetch(String link) throws IOException {
        URI uri;
        try { uri = URI.create(link); }
        catch (RuntimeException invalid) { throw new IOException("Invalid image URL", invalid); }
        for (int redirects = 0; redirects <= 3; redirects++) {
            InetAddress target = target(uri);
            Reply reply = transport.get(uri, target);
            if (reply.status >= 300 && reply.status <= 399) {
                String location = reply.headers.get("location");
                if (location == null || redirects == 3) throw new IOException("Invalid image redirect");
                try { uri = uri.resolve(location); }
                catch (RuntimeException invalid) { throw new IOException("Invalid redirect URL", invalid); }
                continue;
            }
            String type = reply.headers.getOrDefault("content-type", "").split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
            if (reply.status != 200 || !List.of("image/png", "image/jpeg", "image/gif").contains(type)
                    || reply.body.length > MAX_BYTES) throw new IOException("Unsupported image response");
            return decode(reply.body);
        }
        throw new IOException("Too many redirects");
    }

    private InetAddress target(URI uri) throws IOException {
        if (uri.toString().length() > 4096 || !uri.isAbsolute() || uri.getRawUserInfo() != null
                || uri.getHost() == null || !("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))
                || uri.getPort() == 0 || uri.getPort() > 65535) throw new IOException("Only public HTTP image URLs are supported");
        InetAddress[] addresses = resolver.resolve(uri.getHost());
        if (addresses.length == 0) throw new IOException("Image host has no address");
        for (InetAddress address : addresses) if (!publicAddress(address)) throw new IOException("Image host is not public");
        return addresses[0];
    }

    public static boolean publicAddress(InetAddress address) {
        if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                || address.isSiteLocalAddress() || address.isMulticastAddress()) return false;
        byte[] bytes = address.getAddress();
        if (bytes.length == 16) {
            int first = bytes[0] & 255;
            if ((first & 0xFE) == 0xFC || first == 0 || first >= 0xFF) return false;
            if (first == 0x20 && bytes[1] == 1 && ((bytes[2] & 255) == 0x0D && (bytes[3] & 255) == 0xB8
                    || bytes[2] == 0 && (bytes[3] & 255) <= 0x1F)) return false;
            if (first == 0x20 && bytes[1] == 2) return false;
            return first >= 0x20 && first <= 0x3F;
        }
        int a = bytes[0] & 255, b = bytes[1] & 255, c = bytes[2] & 255;
        return a != 0 && a != 10 && a != 127 && a < 224
                && !(a == 100 && b >= 64 && b <= 127) && !(a == 169 && b == 254)
                && !(a == 172 && b >= 16 && b <= 31) && !(a == 192 && (b == 168 || b == 0 || b == 2))
                && !(a == 198 && (b == 18 || b == 19 || b == 51 && c == 100)) && !(a == 203 && b == 0 && c == 113);
    }

    private static BufferedImage decode(byte[] data) throws IOException {
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(data))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new IOException("Unsupported image encoding");
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                int width = reader.getWidth(0), height = reader.getHeight(0);
                if (width <= 0 || height <= 0 || width > MAX_DIMENSION || height > MAX_DIMENSION
                        || (long) width * height > MAX_PIXELS) throw new IOException("Image dimensions exceed the preview limit");
                BufferedImage image = reader.read(0);
                if (image == null) throw new IOException("Invalid image data");
                return image;
            } finally { reader.dispose(); }
        }
    }

    private static Reply request(URI uri, InetAddress address) throws IOException {
        boolean tls = "https".equalsIgnoreCase(uri.getScheme());
        int port = uri.getPort() < 0 ? tls ? 443 : 80 : uri.getPort();
        String host = uri.getHost();
        if (host.startsWith("[")) host = host.substring(1, host.length() - 1);
        Socket socket = new Socket();
        try {
            // Connect to the validated address rather than resolving the host a second time.
            socket.connect(new InetSocketAddress(address, port), 5000);
            socket.setSoTimeout(8000);
            if (tls) {
                SSLSocket secure = (SSLSocket) ((SSLSocketFactory) SSLSocketFactory.getDefault()).createSocket(socket, host, port, true);
                SSLParameters parameters = secure.getSSLParameters();
                parameters.setEndpointIdentificationAlgorithm("HTTPS");
                if (host.indexOf(':') < 0 && !host.matches("[0-9.]+")) parameters.setServerNames(List.of(new SNIHostName(IDN.toASCII(host))));
                secure.setSSLParameters(parameters); secure.setSoTimeout(8000); secure.startHandshake();
                socket = secure;
            }
            String path = uri.getRawPath();
            if (path == null || path.isEmpty()) path = "/";
            if (uri.getRawQuery() != null) path += "?" + uri.getRawQuery();
            String authority = uri.getRawAuthority();
            String request = "GET " + path + " HTTP/1.1\r\nHost: " + authority
                    + "\r\nUser-Agent: Arcadium-Image-Preview\r\nAccept: image/png,image/jpeg,image/gif\r\nAccept-Encoding: identity\r\nConnection: close\r\n\r\n";
            socket.getOutputStream().write(request.getBytes(StandardCharsets.US_ASCII));
            socket.getOutputStream().flush();
            InputStream input = socket.getInputStream();
            String status = line(input);
            String[] pieces = status.split(" ", 3);
            if (pieces.length < 2 || !pieces[0].startsWith("HTTP/1.")) throw new IOException("Invalid HTTP status");
            int code;
            try { code = Integer.parseInt(pieces[1]); }
            catch (NumberFormatException invalid) { throw new IOException("Invalid HTTP status", invalid); }
            HashMap<String, String> headers = new HashMap<>();
            int headerBytes = status.length();
            while (true) {
                String header = line(input);
                headerBytes += header.length() + 2;
                if (headerBytes > 16384) throw new IOException("Oversized image headers");
                if (header.isEmpty()) break;
                int colon = header.indexOf(':');
                if (colon <= 0) throw new IOException("Invalid HTTP header");
                String key = header.substring(0, colon).toLowerCase(Locale.ROOT), value = header.substring(colon + 1).trim();
                if (headers.putIfAbsent(key, value) != null && (key.equals("content-length") || key.equals("transfer-encoding")))
                    throw new IOException("Ambiguous image response");
            }
            if (code >= 300 && code <= 399) return new Reply(code, Map.copyOf(headers), new byte[0]);
            if (!headers.getOrDefault("content-encoding", "identity").equalsIgnoreCase("identity")) throw new IOException("Unsupported image transfer encoding");
            ByteArrayOutputStream body = new ByteArrayOutputStream();
            if (headers.containsKey("transfer-encoding")) {
                if (!headers.get("transfer-encoding").equalsIgnoreCase("chunked")) throw new IOException("Unsupported image transfer");
                while (true) {
                    String chunk = line(input).split(";", 2)[0].trim();
                    int length;
                    try { length = Integer.parseInt(chunk, 16); }
                    catch (NumberFormatException invalid) { throw new IOException("Invalid image chunk", invalid); }
                    if (length == 0) break;
                    transfer(input, body, length);
                    if (!line(input).isEmpty()) throw new IOException("Invalid image chunk separator");
                }
            } else if (headers.containsKey("content-length")) {
                try { transfer(input, body, Integer.parseInt(headers.get("content-length"))); }
                catch (NumberFormatException invalid) { throw new IOException("Invalid image length", invalid); }
            } else {
                byte[] buffer = new byte[8192];
                for (int read; (read = input.read(buffer)) != -1;) {
                    if (body.size() + read > MAX_BYTES) throw new IOException("Image file exceeds preview limit");
                    body.write(buffer, 0, read);
                }
            }
            return new Reply(code, Map.copyOf(headers), body.toByteArray());
        } finally { socket.close(); }
    }

    private static void transfer(InputStream input, ByteArrayOutputStream output, int length) throws IOException {
        if (length < 0 || (long) output.size() + length > MAX_BYTES) throw new IOException("Image file exceeds preview limit");
        byte[] buffer = new byte[Math.min(8192, Math.max(1, length))];
        while (length > 0) {
            int read = input.read(buffer, 0, Math.min(length, buffer.length));
            if (read < 0) throw new EOFException("Truncated image response");
            output.write(buffer, 0, read); length -= read;
        }
    }

    private static String line(InputStream input) throws IOException {
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        while (result.size() <= 8192) {
            int value = input.read();
            if (value < 0) throw new EOFException("Truncated HTTP headers");
            if (value == '\n') return result.toString(StandardCharsets.US_ASCII).replace("\r", "");
            result.write(value);
        }
        throw new IOException("HTTP header line too long");
    }
}
