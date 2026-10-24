package dev.sapphire.companion;
import org.junit.Test;
import static org.junit.Assert.*;

public final class PhoneActionPolicyTest {
    @Test public void validatesDraftsWithoutCarrierActions() {
        assertEquals("+15550100000", PhoneActionPolicy.draft("sms", " \t+15550100000\n", "Fixture").number);
        for (String value : new String[]{"911", "*#06#", "15550100000,1", "15550100000;1", "tel:+15550100000", "１２３４５６７８９０", "+1234567890123456", "+1 5550100000", "\0+15550100000"})
            assertNull(value, PhoneActionPolicy.draft("dial", value, ""));
        assertNull(PhoneActionPolicy.draft("call", "+15550100000", ""));
        assertNull(PhoneActionPolicy.draft("dial", "+15550100000", "Not a dialer body"));
        assertNull(PhoneActionPolicy.draft("sms", "+15550100000", "nul\0"));
        assertNull(PhoneActionPolicy.draft("sms", "+15550100000", "control\u0003"));
        assertNotNull(PhoneActionPolicy.draft("sms", "+15550100000", "Tabs\tand\nlines"));
        assertNull(PhoneActionPolicy.draft("sms", "+15550100000", "\ud800"));
        assertNotNull(PhoneActionPolicy.draft("sms", "+15550100000", "😀".repeat(1000)));
        assertNull(PhoneActionPolicy.draft("sms", "+15550100000", "😀".repeat(1001)));
    }
    @Test public void boundsReviewTokensAndRejectsStaleWork() {
        PhoneActionPolicy.Store store = new PhoneActionPolicy.Store();
        PhoneActionPolicy.Draft draft = PhoneActionPolicy.draft("sms", "+15550100000", "Synthetic fixture only");
        assertEquals(PhoneActionPolicy.Offer.INVALID, store.offer("bad/id", "token", draft, 1));
        assertEquals(PhoneActionPolicy.Offer.READY, store.offer("one", "token", draft, 100));
        assertNull(store.peek("one", "wrong", 100));
        assertNotNull(store.peek("one", "token", 100));
        assertEquals(PhoneActionPolicy.Offer.DUPLICATE, store.offer("one", "retry-token", draft, 101));
        assertNull(store.peek("one", "retry-token", 101));
        PhoneActionPolicy.Store rollback = new PhoneActionPolicy.Store();
        rollback.offer("rollback", "token", draft, 100);
        assertNull(rollback.peek("rollback", "token", 99));
        assertNull(rollback.peek("rollback", "token", 101));
        assertNotNull(store.peek("one", "token", 120099));
        assertNull(store.peek("one", "token", 120100));
        assertEquals(PhoneActionPolicy.Offer.EXPIRED, store.offer("one", "token2", draft, 120100));
        assertEquals(PhoneActionPolicy.Offer.READY, store.offer("two", "token2", draft, 120101));
        assertNull(store.peek("one", "token", 120102));
        assertTrue(store.consume("two", "token2", 120102));
        assertFalse(store.consume("two", "token2", 120102));
        store.offer("three", "token3", draft, 120103);
        store.reset();
        assertNull(store.peek("three", "token3", 120104));
        for (int i = 0; i < 40; i++) store.offer("id-" + i, "token-" + i, draft, 120200 + i);
        assertEquals(PhoneActionPolicy.Offer.READY, store.offer("id-0", "fresh", draft, 120300));
    }
}
