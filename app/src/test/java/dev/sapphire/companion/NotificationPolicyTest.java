package dev.sapphire.companion;

import org.junit.Test;
import static org.junit.Assert.*;

public class NotificationPolicyTest {
    @Test public void escapedJsonCannotOverflowMinimumMtuQueue() throws Exception {
        org.json.JSONObject payload = new org.json.JSONObject().put("text", "\u0001".repeat(4096)).put("title", "Example");
        assertTrue(NotificationPolicy.fitPayload(payload));
        assertTrue(payload.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8).length <= 7000);
        assertFalse(NotificationPolicy.fitPayload(new org.json.JSONObject().put("text", "").put("key", "x".repeat(7001))));
    }
    @Test public void utf8BoundsPreserveCodePoints() {
        assertEquals("hello", NotificationPolicy.bounded("hello", 50));
        assertEquals("", NotificationPolicy.bounded(null, 4));
        assertEquals("ab", NotificationPolicy.bounded("ab🔔z", 5));
        assertEquals("ab🔔", NotificationPolicy.bounded("ab🔔z", 6));
        assertEquals("", NotificationPolicy.bounded("🔔", 3));
    }
    private boolean eligible(String app, boolean sameUser, boolean secret, boolean ongoing, boolean summary,
                             boolean general, boolean system, boolean includeGeneral, boolean includeSystem) {
        return NotificationPolicy.eligible(app, "dev.sapphire.companion", sameUser, secret, ongoing, summary,
                general, system, includeGeneral, includeSystem);
    }
    @Test public void excludesPrivateProfilesSecretAndOwnNotifications() {
        assertTrue(eligible("test.app", true, false, false, false, false, false, false, false));
        assertFalse(eligible("test.app", false, false, false, false, false, false, false, false));
        assertFalse(eligible("test.app", true, true, false, false, false, false, true, true));
        assertFalse(eligible("dev.sapphire.companion", true, false, false, false, false, false, true, true));
        assertFalse(eligible("dev.sapphire.companion.hotspothelper", true, false, false, false, false, false, true, true));
    }
    @Test public void excludesOngoingAndGroupSummaries() {
        assertFalse(eligible("test.app", true, false, true, false, false, false, true, true));
        assertFalse(eligible("test.app", true, false, false, true, false, false, true, true));
    }
    @Test public void priorityAndSystemRequireExplicitInclusion() {
        assertFalse(eligible("test.app", true, false, false, false, true, false, false, false));
        assertFalse(eligible("android", true, false, false, false, false, true, false, false));
        assertTrue(eligible("android", true, false, false, false, true, true, true, true));
    }
}
