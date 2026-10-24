package dev.sapphire.companion;
import java.net.URI;
import java.nio.charset.StandardCharsets;

final class HandoffPolicy {
    static String copiedLink(String text) {
        if (text == null || text.getBytes(StandardCharsets.UTF_8).length >= 12000) return null;
        java.util.regex.Matcher matches = java.util.regex.Pattern.compile("https?://[^\\s<>\"']+", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(text);
        while (matches.find()) {
            String candidate = matches.group();
            while (!candidate.isEmpty()) {
                char last = candidate.charAt(candidate.length()-1);
                boolean punctuation = ".,!?;:".indexOf(last) >= 0;
                boolean unbalanced = (last == ')' && count(candidate, ')') > count(candidate, '('))
                    || (last == ']' && count(candidate, ']') > count(candidate, '['))
                    || (last == '}' && count(candidate, '}') > count(candidate, '{'));
                if (!punctuation && !unbalanced) break;
                candidate = candidate.substring(0, candidate.length()-1);
            }
            String accepted = url(candidate);
            if (accepted != null) return accepted;
        }
        return null;
    }
    private static long count(String value, char c) { return value.chars().filter(x -> x == c).count(); }
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
