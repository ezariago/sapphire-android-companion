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
}
