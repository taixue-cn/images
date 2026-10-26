/* Retained from the existing local Images integration, recovered with CFR 0.152. */
package com.andavin.images.command;

import com.andavin.images.Images;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URI;
import java.net.URLConnection;
import java.util.List;
import javax.imageio.ImageIO;

final class UrlImageLoader {
    private UrlImageLoader() {
    }

    static BufferedImage read(String string) throws Exception {
        BufferedImage image;
        URI uRI = new URI(string).normalize();
        UrlImageLoader.validate(uRI, Images.getInstance().getConfig().getStringList("url-fetch.trusted-private-prefixes"));
        URLConnection uRLConnection = uRI.toURL().openConnection();
        uRLConnection.setConnectTimeout(Images.getInstance().getConfig().getInt("url-fetch.connect-timeout-ms", 3000));
        uRLConnection.setReadTimeout(Images.getInstance().getConfig().getInt("url-fetch.read-timeout-ms", 10000));
        uRLConnection.setUseCaches(false);
        if (uRLConnection instanceof HttpURLConnection) {
            HttpURLConnection connection = (HttpURLConnection)uRLConnection;
            connection.setInstanceFollowRedirects(false);
            int n = connection.getResponseCode();
            if (n < 200 || n >= 300) {
                throw new IOException("image server returned HTTP " + n);
            }
        }
        try (InputStream inputStream = uRLConnection.getInputStream();){
            image = ImageIO.read(inputStream);
        }
        if (image == null) {
            throw new IOException("response is not a supported image");
        }
        return image;
    }

    static void validate(URI uRI, List<String> list) throws Exception {
        String string = uRI.getScheme();
        if (!"http".equalsIgnoreCase(string) && !"https".equalsIgnoreCase(string)) {
            throw new IllegalArgumentException("only HTTP(S) image URLs are supported");
        }
        if (uRI.getHost() == null || uRI.getRawUserInfo() != null || uRI.getRawFragment() != null) {
            throw new IllegalArgumentException("invalid image URL");
        }
        boolean bl = false;
        for (InetAddress inetAddress : InetAddress.getAllByName(uRI.getHost())) {
            bl |= inetAddress.isAnyLocalAddress() || inetAddress.isLoopbackAddress() || inetAddress.isSiteLocalAddress();
        }
        if (bl && !UrlImageLoader.isTrusted(uRI, list)) {
            throw new IllegalArgumentException("private image URL is not trusted");
        }
    }

    static boolean isTrusted(URI uRI, List<String> list) {
        for (String string : list) {
            try {
                String string2;
                URI uRI2 = new URI(string).normalize();
                if (!UrlImageLoader.sameOrigin(uRI, uRI2) || uRI.getRawQuery() != null || uRI2.getRawQuery() != null) continue;
                String string3 = uRI2.getRawPath();
                String string4 = uRI.getRawPath();
                if (string3 == null || string4 == null || !string4.startsWith(string2 = string3.endsWith("/") ? string3 : string3 + "/") || string4.length() <= string2.length()) continue;
                return true;
            }
            catch (Exception exception) {
            }
        }
        return false;
    }

    private static boolean sameOrigin(URI uRI, URI uRI2) {
        return uRI.getScheme().equalsIgnoreCase(uRI2.getScheme()) && uRI.getHost().equalsIgnoreCase(uRI2.getHost()) && UrlImageLoader.effectivePort(uRI) == UrlImageLoader.effectivePort(uRI2);
    }

    private static int effectivePort(URI uRI) {
        if (uRI.getPort() != -1) {
            return uRI.getPort();
        }
        return "https".equalsIgnoreCase(uRI.getScheme()) ? 443 : 80;
    }
}

