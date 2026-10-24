package dev.sapphire.companion;
import org.junit.Test;
import static org.junit.Assert.*;
import dev.sapphire.ipc.ClipboardTextPolicy;
public final class ClipboardTextPolicyTest {
 @Test public void filtersSensitiveRemoteAndOversizedText() {
  assertTrue(ClipboardTextPolicy.accepts("hello\nworld",1,false,false));
  assertFalse(ClipboardTextPolicy.accepts("secret",1,true,false));
  assertFalse(ClipboardTextPolicy.accepts("remote",1,false,true));
  assertFalse(ClipboardTextPolicy.accepts("rich",2,false,false));
  assertFalse(ClipboardTextPolicy.accepts(null,1,false,false));
  assertFalse(ClipboardTextPolicy.accepts("",1,false,false));
  assertFalse(ClipboardTextPolicy.accepts("bad\0value",1,false,false));
  assertFalse(ClipboardTextPolicy.accepts("é".repeat(6000),1,false,false));
  assertTrue(ClipboardTextPolicy.accepts("x".repeat(11999),1,false,false));
 }
}
