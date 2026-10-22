package dev.sapphire.companion;

import org.junit.Test;
import org.json.*;
import static org.junit.Assert.*;

public class NotificationEventsTest {
    private JSONObject payload(String key, String revision) throws Exception {
        return new JSONObject().put("key", key).put("revision", revision);
    }
    @Test public void coalescedUpdateStillArrivesBeforeRemoval() throws Exception {
        NotificationEvents queue = new NotificationEvents();
        queue.offer("notif.postedV2", payload("a", "1"));
        queue.offer("notif.postedV2", payload("a", "2"));
        queue.offer("notif.removed", payload("a", "2"));
        JSONObject posted = queue.poll();
        assertEquals("notif.postedV2", posted.getString("type"));
        assertEquals("2", posted.getJSONObject("payload").getString("revision"));
        assertEquals("notif.removed", queue.poll().getString("type"));
        assertTrue(queue.isEmpty());
    }
    @Test public void repostDoesNotMoveAheadOfOlderRemoval() throws Exception {
        NotificationEvents queue = new NotificationEvents();
        queue.offer("notif.postedV2", payload("a", "1"));
        queue.offer("notif.removed", payload("a", "1"));
        queue.offer("notif.postedV2", payload("a", "2"));
        assertEquals("notif.removed", queue.poll().getString("type"));
        assertEquals("2", queue.poll().getJSONObject("payload").getString("revision"));
    }
    @Test public void actionResultCannotReplaceRemoval() throws Exception {
        NotificationEvents queue = new NotificationEvents();
        queue.offer("notif.removed", payload("a", "1"));
        queue.offer("notif.actionResult", payload("a", "1"));
        assertEquals("notif.removed", queue.poll().getString("type"));
        assertEquals("notif.actionResult", queue.poll().getString("type"));
    }
    @Test public void resetsAndBackpressureAreExplicit() throws Exception {
        NotificationEvents queue = new NotificationEvents();
        for (int n=0; n<128; n++) assertTrue(queue.offer("notif.postedV2", payload("key-"+n,"1")));
        assertFalse(queue.offer("notif.removed", payload("overflow", "1")));
        assertTrue(queue.offer("notif.reset", new JSONObject()));
        assertEquals("notif.reset", queue.poll().getString("type"));
        assertTrue(queue.isEmpty());
    }
}
