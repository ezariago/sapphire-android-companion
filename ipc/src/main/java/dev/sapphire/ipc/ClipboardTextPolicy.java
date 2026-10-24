package dev.sapphire.ipc;
import java.nio.charset.StandardCharsets;
/** No URI coercion, rich payload, sensitive copy or remote echo. */
public final class ClipboardTextPolicy {
    private ClipboardTextPolicy() {}
    public static boolean accepts(String text, int count, boolean sensitive, boolean fromMac) {
        return count == 1 && !sensitive && !fromMac && text != null && !text.isEmpty()
            && text.indexOf(0) < 0 && text.getBytes(StandardCharsets.UTF_8).length < 12000;
    }
}
