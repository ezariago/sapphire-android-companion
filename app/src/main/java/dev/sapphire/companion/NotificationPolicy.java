package dev.sapphire.companion;

import java.nio.charset.StandardCharsets;
import org.json.*;

/** Transport bounds shared by extraction and tests. Never split a Unicode code point. */
final class NotificationPolicy {
    static String bounded(CharSequence value, int bytes) {
        if (value == null) return "";
        String input = value.toString();
        int end = 0, used = 0;
        while (end < input.length()) {
            int cp = input.codePointAt(end);
            int size = new String(Character.toChars(cp)).getBytes(StandardCharsets.UTF_8).length;
            if (used + size > bytes) break;
            used += size;
            end += Character.charCount(cp);
        }
        return input.substring(0, end);
    }

    // Escaped JSON can be much larger than its original UTF-8 strings. Keep a
    // full notification within the BLE queue even at the minimum supported MTU.
    static boolean fitPayload(JSONObject payload) throws JSONException {
        String text = payload.optString("text");
        while (payload.toString().getBytes(StandardCharsets.UTF_8).length > 7000 && !text.isEmpty()) {
            text = bounded(text, Math.max(0, text.getBytes(StandardCharsets.UTF_8).length - 256));
            payload.put("text", text);
        }
        return payload.toString().getBytes(StandardCharsets.UTF_8).length <= 7000;
    }

    static boolean eligible(String app, String ownApp, boolean sameUser,
                            boolean secret, boolean ongoing, boolean summary,
                            boolean general, boolean system, boolean includeGeneral, boolean includeSystem) {
        return sameUser && !secret && !ongoing && !summary
                && !app.equals(ownApp) && !app.equals("dev.sapphire.companion.hotspothelper")
                && (!general || includeGeneral) && (!system || includeSystem);
    }
}
