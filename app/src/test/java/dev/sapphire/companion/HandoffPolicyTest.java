package dev.sapphire.companion;
import org.junit.Test;
import static org.junit.Assert.*;

public class HandoffPolicyTest {
    @Test public void acceptsOnlyBoundedWebLinks() {
        for (String input : new String[]{"https://example.com/path?q=test#part", "http://example.com:8080/", "https://example.com/%F0%9F%94%94", "  https://example.com  "})
            assertNotNull(input, HandoffPolicy.url(input));
        for (String input : new String[]{"", "file:///etc/passwd", "javascript:alert(1)", "intent://test", "https:///missing-host", "https://name:password@example.com", "https://example.com/a b", "https://example.com/\npath", "https://example.com\\@other.example/", "https://example.com:99999", "https://example.com/" + "a".repeat(4096)})
            assertNull(input, HandoffPolicy.url(input));
    }
    @org.junit.Test public void copiedTextExtractsSafeFirstLink() {
        org.junit.Assert.assertEquals("https://example.com/path?x=1&y=2", HandoffPolicy.copiedLink("Read https://example.com/path?x=1&y=2 please"));
        org.junit.Assert.assertEquals("https://example.com/wiki/Test_(thing)", HandoffPolicy.copiedLink("See (https://example.com/wiki/Test_(thing))."));
        org.junit.Assert.assertEquals("https://safe.example/", HandoffPolicy.copiedLink("https://user:pass@example.com then https://safe.example/"));
        org.junit.Assert.assertNull(HandoffPolicy.copiedLink("file:///tmp/test intent://test"));
        org.junit.Assert.assertNull(HandoffPolicy.copiedLink("x".repeat(12000) + "https://example.com"));
    }
}
