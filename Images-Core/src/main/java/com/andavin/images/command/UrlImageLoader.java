package com.andavin.images.command;

import com.andavin.images.Images;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URI;
import java.net.URLConnection;
import java.util.List;
import java.io.InputStream;
import javax.imageio.ImageIO;

final class UrlImageLoader {

    private UrlImageLoader() {
    }

    static BufferedImage read(String rawUrl) throws Exception {
        URI uri = new URI(rawUrl).normalize();
        validate(uri, Images.getInstance().getConfig().getStringList("url-fetch.trusted-private-prefixes"));

        URLConnection connection = uri.toURL().openConnection();
        connection.setConnectTimeout(Images.getInstance().getConfig().getInt("url-fetch.connect-timeout-ms", 3000));
        connection.setReadTimeout(Images.getInstance().getConfig().getInt("url-fetch.read-timeout-ms", 10000));
        connection.setUseCaches(false);
        if (connection instanceof HttpURLConnection) {
            HttpURLConnection http = (HttpURLConnection) connection;
            http.setInstanceFollowRedirects(false);
            int status = http.getResponseCode();
            if (status < 200 || status >= 300) {
                throw new IOException("image server returned HTTP " + status);
            }
        }

        BufferedImage image;
        try (InputStream input = connection.getInputStream()) {
            image = ImageIO.read(input);
        }
        if (image == null) {
            throw new IOException("response is not a supported image");
        }
        return image;
    }

    static void validate(URI uri, List<String> trustedPrivatePrefixes) throws Exception {
        String scheme = uri.getScheme();
        if (!("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))) {
            throw new IllegalArgumentException("only HTTP(S) image URLs are supported");
        }
        if (uri.getHost() == null || uri.getRawUserInfo() != null || uri.getRawFragment() != null) {
            throw new IllegalArgumentException("invalid image URL");
        }

        boolean local = false;
        for (InetAddress address : InetAddress.getAllByName(uri.getHost())) {
            local |= address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isSiteLocalAddress();
        }
        if (local && !isTrusted(uri, trustedPrivatePrefixes)) {
            throw new IllegalArgumentException("private image URL is not trusted");
        }
    }

    static boolean isTrusted(URI target, List<String> prefixes) {
        for (String rawPrefix : prefixes) {
            try {
                URI prefix = new URI(rawPrefix).normalize();
                if (!sameOrigin(target, prefix) || target.getRawQuery() != null || prefix.getRawQuery() != null) {
                    continue;
                }
                String prefixPath = prefix.getRawPath();
                String targetPath = target.getRawPath();
                if (prefixPath == null || targetPath == null) {
                    continue;
                }
                String boundary = prefixPath.endsWith("/") ? prefixPath : prefixPath + "/";
                if (targetPath.startsWith(boundary) && targetPath.length() > boundary.length()) {
                    return true;
                }
            } catch (Exception ignored) {
                // Ignore malformed administrator configuration entries.
            }
        }
        return false;
    }

    private static boolean sameOrigin(URI left, URI right) {
        return left.getScheme().equalsIgnoreCase(right.getScheme())
                && left.getHost().equalsIgnoreCase(right.getHost())
                && effectivePort(left) == effectivePort(right);
    }

    private static int effectivePort(URI uri) {
        if (uri.getPort() != -1) {
            return uri.getPort();
        }
        return "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
    }
}
