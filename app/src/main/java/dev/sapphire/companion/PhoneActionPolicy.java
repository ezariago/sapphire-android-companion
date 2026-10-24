package dev.sapphire.companion;

import java.nio.charset.StandardCharsets;
import java.util.*;

/** Drafts only: this policy has no SMS, dialer, contacts or permission APIs. */
final class PhoneActionPolicy {
    static final long LIFETIME_MS = 120_000;
    static final class Draft {
        final String kind, number, body;
        private Draft(String kind, String number, String body) { this.kind = kind; this.number = number; this.body = body; }
        boolean same(Draft other) { return other != null && kind.equals(other.kind) && number.equals(other.number) && body.equals(other.body); }
    }
    static Draft draft(String kind, String input, String body) {
        if (!"sms".equals(kind) && !"dial".equals(kind)) return null;
        if (input == null || body == null || input.length() > 40) return null;
        String number = input.replaceAll("^[ \\t\\r\\n]+|[ \\t\\r\\n]+$", "");
        // No USSD, extensions, pauses, Unicode digits, short codes or URI input.
        if (!number.matches("\\+?[0-9]{7,15}")) return null;
        if (body.getBytes(StandardCharsets.UTF_8).length > 4000 || body.indexOf('\0') >= 0) return null;
        for (int i = 0; i < body.length(); i++) {
            char value = body.charAt(i);
            if (Character.isISOControl(value) && value != '\t' && value != '\r' && value != '\n') return null;
            if (Character.isHighSurrogate(value)) {
                if (++i >= body.length() || !Character.isLowSurrogate(body.charAt(i))) return null;
            } else if (Character.isLowSurrogate(value)) return null;
        }
        if ("dial".equals(kind) && !body.isEmpty()) return null;
        return new Draft(kind, number, body);
    }
    static boolean requestID(String id) { return id != null && id.matches("[A-Za-z0-9-]{1,128}"); }
    enum Offer { READY, DUPLICATE, EXPIRED, INVALID }
    static final class Store {
        private final ArrayDeque<String> received = new ArrayDeque<>();
        private String id, token;
        private Draft value;
        private long started;
        Offer offer(String id, String token, Draft draft, long now) {
            if (!requestID(id) || token == null || token.isEmpty() || draft == null || now < 0) return Offer.INVALID;
            if (received.contains(id)) return id.equals(this.id) && valid(now) && value.same(draft) ? Offer.DUPLICATE : Offer.EXPIRED;
            this.id = id; this.token = token; this.value = draft; started = now;
            received.addLast(id);
            while (received.size() > 32) received.removeFirst();
            return Offer.READY;
        }
        boolean valid(long now) {
            if (value == null) return false;
            if (now < started || now - started >= LIFETIME_MS) { clearPending(); return false; }
            return true;
        }
        Draft peek(String id, String token, long now) {
            return Objects.equals(this.id, id) && Objects.equals(this.token, token) && valid(now) ? value : null;
        }
        boolean consume(String id, String token, long now) {
            if (peek(id, token, now) == null) return false;
            clearPending(); return true;
        }
        void clearPending() { id = null; token = null; value = null; started = 0; }
        void reset() { clearPending(); received.clear(); }
    }
}
