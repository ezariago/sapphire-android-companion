package dev.sapphire.companion;
import java.net.URI;
import java.nio.charset.StandardCharsets;

final class HandoffPolicy {
    static String url(String input) {
        if (input == null) return null;
        String raw = input.trim();
        if (raw.isEmpty() || raw.getBytes(StandardCharsets.UTF_8).length > 4096 || raw.contains("\\")) return null;
        for (char c : raw.toCharArray()) if (c < 33 || c > 126) return null;
        try {
            URI uri = new URI(raw);
            if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                    || uri.getRawUserInfo() != null || uri.getHost() == null || uri.getHost().isEmpty()
                    || uri.getPort() > 65535) return null;
            return uri.toASCIIString();
        } catch (Exception invalid) { return null; }
    }
}
