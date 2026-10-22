package dev.sapphire.companion;

import java.util.LinkedHashMap;
import org.json.*;

/** Bounded, coalesced events awaiting BLE capacity. A removal never overwrites its pending post. */
final class NotificationEvents {
    private final LinkedHashMap<String, JSONObject> events = new LinkedHashMap<>();
    boolean offer(String type, JSONObject payload) {
        if ("notif.reset".equals(type)) events.clear();
        String key = type + ":" + payload.optString("key", "");
        if (events.size() >= 128 && !events.containsKey(key)) return false;
        try {
            events.remove(key);
            events.put(key, new JSONObject().put("type", type).put("payload", payload));
            return true;
        } catch (JSONException e) { return false; }
    }
    JSONObject poll() {
        if (events.isEmpty()) return null;
        return events.remove(events.keySet().iterator().next());
    }
    boolean isEmpty() { return events.isEmpty(); }
    void clear() { events.clear(); }
}
